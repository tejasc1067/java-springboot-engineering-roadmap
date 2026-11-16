# Redis Pub/Sub and Streams

Redis can carry messages between services as well as store data. It has two separate features for it, and they behave in opposite ways. **Pub/Sub** broadcasts a message to whoever is listening *right now* and keeps nothing: a subscriber that's down misses it forever. **Streams** store messages in a log, track which ones each consumer has handled, and redeliver what a crashed consumer never finished. Picking the wrong one is how "we lost some order-confirmation emails" happens. This topic shows both, and uses Pub/Sub to fix the cache disagreement from [05-local-cache-with-caffeine.md](05-local-cache-with-caffeine.md).

---

## The problem this solves

Two jobs come up once a service runs as several instances:

1. **"Tell every instance that something changed."** Instance A updates product p-1's price. Instances A and B each have p-1 in their local cache (topic 05). B keeps serving the old price until its entry expires. Every instance needs to hear "evict p-1", and if one misses it, a TTL eventually covers the gap.
2. **"Make sure this work gets done once."** An order is placed; a confirmation email must be sent. If the email service is restarting, the message must wait. If it crashes halfway, someone must retry. Missing one isn't acceptable.

The first is a broadcast where loss is tolerable. The second is a work queue where loss isn't. Pub/Sub fits the first, Streams the second.

---

## Pub/Sub: broadcast to whoever is listening

Publishers send to a **channel** (just a name); every client currently **subscribed** to that channel gets a copy. Redis doesn't store the message anywhere.

```text
PUBLISH orders "order-1 placed"      -> (integer) 2      the number of subscribers that received it
```

In Spring, `StringRedisTemplate.convertAndSend` publishes and a `RedisMessageListenerContainer` subscribes:

```java
redis.convertAndSend("orders", "order-1 placed");      // PUBLISH; returns the receiver count

RedisMessageListenerContainer container = new RedisMessageListenerContainer();
container.setConnectionFactory(connectionFactory);
container.addMessageListener((message, pattern) -> handle(new String(message.getBody())),
        new ChannelTopic("orders"));
container.afterPropertiesSet();
container.start();
```

(In an application you'd declare the container as a `@Bean` and Spring starts and stops it.)

`PubSubTest.everySubscriberGetsItsOwnCopy_messagesAreBroadcastNotShared`: two subscribers, one message, both receive it. There's no "one of them handles it"; every subscriber gets every message.

### Broken: a subscriber that's down misses messages, permanently

`publishWithNoSubscriber_isDroppedAtOnce_andNeverDeliveredLater`:

```java
Long receivers = redis.convertAndSend("orders", "order-1 placed");
assertThat(receivers).isZero();                  // nobody subscribed: Redis discards it

startSubscriber("orders");                       // the subscriber comes back up
redis.convertAndSend("orders", "order-2 placed");
// ... order-2 arrives ...
assertThat(received).containsExactly("order-2 placed");   // order-1 is gone for good
```

There's no queue, no replay, no acknowledgement. The same happens to a subscriber that is "up" but briefly disconnected (a network blip, a Redis failover), or one too slow to keep up: Redis disconnects clients whose output buffer for Pub/Sub grows past a limit. Pub/Sub is **at-most-once**: each message is delivered zero or one times.

Using it for "send the confirmation email" is the bug. Restart the email service during a deploy and every order placed in those seconds gets no email, with no error anywhere.

### Where Pub/Sub fits: cache invalidation across instances

Losing an occasional message is acceptable when something else repairs the damage. Cache eviction is the classic case: each instance keeps its local cache and subscribes to an eviction channel; whoever changes a product publishes its id (`LocalProductCache`):

```java
listener.addMessageListener((message, pattern) -> entries.remove(new String(message.getBody())),
        new ChannelTopic("cache:evict:product"));

public void evictEverywhere(String productId) {      // call after the database write commits
    redis.convertAndSend("cache:evict:product", productId);
}
```

`CacheInvalidationTest.updateOnInstanceA_isBroadcast_andInstanceBStopsServingTheOldPrice`: after the database update, B still returns `price=10` (topic 05's bug); after A's broadcast, B reloads `price=12`. The publishing instance receives its own message too, so it evicts through the same path.

Keep a TTL on the local entries anyway (topic 04). An instance that was disconnected during the broadcast never hears it, and the TTL is what bounds how long it stays wrong.

---

## Streams: a log that remembers

A **stream** is an append-only log stored under one Redis key. Every entry gets an id (`1731744000000-0`: milliseconds, then a sequence number) and a small map of fields.

```text
XADD orders * orderId 1        -> "1731744000000-0"     (* = let Redis pick the id)
```

Entries stay until you trim them. The other half is the **consumer group**: a named reader, like `email-service`, for which Redis remembers two things:

- the **last delivered id**: which entries have been handed out to the group;
- the **pending entries list (PEL)**: entries handed to a consumer but not yet **acknowledged** with `XACK`.

```java
StreamOperations<String, Object, Object> streams = redis.opsForStream();

streams.createGroup("orders", ReadOffset.from("0"), "email-service");   // XGROUP CREATE ... 0 MKSTREAM
streams.add("orders", Map.of("orderId", "1"));                           // XADD

// XREADGROUP GROUP email-service worker-1 COUNT 10 STREAMS orders >
List<MapRecord<String, Object, Object>> batch = streams.read(Consumer.from("email-service", "worker-1"),
        StreamReadOptions.empty().count(10), StreamOffset.create("orders", ReadOffset.lastConsumed()));

// ... send the emails ...
streams.acknowledge("orders", "email-service", batch.get(0).getId());   // XACK
```

`ReadOffset.lastConsumed()` is the `>` in `XREADGROUP`: "entries never delivered to anyone in this group". `StreamsTest` shows what this buys:

- **`messagesAddedWhileNoConsumerRuns_areWaitingWhenItStarts`**: three orders are added while the email service is down; when worker-1 starts, it gets all three. Compare with Pub/Sub's lost `order-1`.
- **`eachMessageGoesToOneConsumerInTheGroup_soConsumersShareTheWork`**: worker-1 gets order 1, worker-2 gets order 2. Within a group, each entry goes to one consumer, so adding consumers spreads the load. A *different* group (say `analytics`) would get its own copy of every entry.
- **`acknowledgedMessages_areStillInTheStream_untilTrimmed`**: `XACK` removes the entry from the pending list, not from the stream. Cap the stream with `XTRIM` or `XADD ... MAXLEN ~ 100000`, or it grows until Redis runs out of memory.

### A consumer crashes before acknowledging

`consumerCrashesBeforeAck_messageStaysPending_andAnotherConsumerClaimsIt`:

```text
worker-1  XREADGROUP ... >      gets order 1, then crashes before sending the email or calling XACK
          XPENDING              order 1, owner worker-1, delivered 1 time
worker-2  XREADGROUP ... >      nothing: order 1 was already delivered, ">" never returns it again
worker-2  XCLAIM ... order 1    now owns it, sends the email
worker-2  XACK                  pending list is empty
```

The message isn't lost, but it isn't retried by itself either. Something has to look at the pending list. Two standard moves:

- **On start-up, a consumer reads its own pending entries** by reading from id `0` instead of `>` (`restartedConsumer_readsItsOwnPendingMessagesFirst`). That covers "the consumer restarted".
- **A periodic job claims entries idle for too long** with `XCLAIM` (or `XAUTOCLAIM`, which finds and claims in one command). That covers "the consumer died and isn't coming back". Use a real minimum idle time, such as a minute, so you don't take work from a consumer that is only slow; the test uses zero to stay fast.

Each pending entry also counts its deliveries. An entry delivered 5 times that still fails is a **poison message**: move it to a separate "dead letter" stream and acknowledge it, instead of retrying forever. Module 16 covers dead-letter handling properly.

### At-least-once means handlers must be idempotent

A consumer can send the email and crash *before* `XACK`. The entry is claimed and the email goes out twice. Streams give **at-least-once** delivery: every message is handled, some more than once. Make the handler safe to repeat, for example by recording processed order ids, as in module 13's [19-idempotency-and-the-outbox-pattern.md](../13-microservices/19-idempotency-and-the-outbox-pattern.md).

Spring Data Redis also has a `StreamMessageListenerContainer` that runs the `XREADGROUP` loop for you. The demo calls the commands directly so each step is visible.

---

## Pub/Sub vs Streams vs a real broker

| | Pub/Sub | Streams | Kafka / RabbitMQ (module 16) |
|---|---|---|---|
| Stored? | no | yes, in Redis memory | yes, on disk |
| Subscriber down | misses messages | they wait | they wait |
| Delivery | at-most-once | at-least-once (with `XACK`) | at-least-once; more options |
| Work sharing | no, everyone gets everything | consumer groups | consumer groups / competing consumers |
| Replay old messages | no | yes, by id | yes (Kafka); no (RabbitMQ, once consumed) |
| Good for | cache invalidation, live notifications | modest work queues when you already run Redis | high volume, long retention, many teams |

Streams are a reasonable work queue for moderate volume when Redis is already in your stack. Their limits are that everything lives in memory and durability depends on how Redis persistence (RDB snapshots, the AOF log) and replication are configured: a failover can lose the last writes. When messages are your system's backbone, high-volume, kept for days, read by many teams, use a broker built for it (module 16).

---

## When to use it / when not to

- **Pub/Sub**: notifications that are useful now and harmless to miss, with a fallback such as a TTL. Cache eviction broadcasts, "config changed, reload", pushing live updates to connected WebSocket clients.
- **Not Pub/Sub**: anything that must happen: emails, payments, inventory changes, audit records.
- **Streams**: a work queue or event log that must survive consumer restarts, at moderate volume, when you already run Redis and don't want another system yet.
- **Not Streams**: when you need days of retention, very high throughput, or many independent teams consuming. That's Kafka's job.

---

## Common pitfalls

- **Pub/Sub for work that must happen.** A subscriber restarting during a deploy silently drops every message sent meanwhile. Use Streams or a broker.
- **Never acknowledging.** Every entry stays in the pending list forever and memory grows. `XACK` after the work succeeds.
- **No one ever reads the pending list.** A crashed consumer's entries sit there unprocessed. Read your own pending entries on start-up, and run an `XAUTOCLAIM` job for entries idle too long.
- **Never trimming the stream.** Acknowledged entries are still stored. Use `MAXLEN` on `XADD` or a periodic `XTRIM`.
- **Non-idempotent handlers.** At-least-once delivery means duplicates. Make reprocessing harmless.

---

## Code examples

`CODE_EXAMPLES/11-redis-pub-sub-and-streams/pubsub-streams-demo/`. Run with `mvn -q test` (Docker running). Read in this order:

1. `src/test/java/com/example/messaging/PubSubTest.java` — a received message, a message dropped while nobody listens, and broadcast to two subscribers.
2. `src/main/java/com/example/messaging/LocalProductCache.java` — a local cache that evicts on a Pub/Sub broadcast.
3. `src/test/java/com/example/messaging/CacheInvalidationTest.java` — instance B stale after A's update, then fixed by the broadcast.
4. `src/test/java/com/example/messaging/StreamsTest.java` — messages waiting for a consumer, work shared in a group, the crashed consumer and `XCLAIM`, re-reading own pending entries, and trimming.

`RedisTestBase.java` starts one Redis container for all the test classes and empties it before each test.

---

## Try this yourself

1. In `StreamsTest`, create a second group `analytics` on the same stream and read with it after `email-service` has read everything. What does `analytics` get, and why?
2. Write a test that delivers the same entry to worker-1 three times (read, then claim it back with `XCLAIM` twice) and then checks `getTotalDeliveryCount()` in the pending list. Use the count to move the entry to an `orders:dead` stream after 3 attempts.
3. In `CacheInvalidationTest`, close instance B's listener before A broadcasts, then reopen it. Is B's cached price fixed? What setting would bound how long it stays wrong?

## Self-check

1. A service sends "order placed" events to the email service over Redis Pub/Sub. The email service restarts during a 20-second deploy. What happens to orders placed in those 20 seconds? What would happen with a stream and a consumer group?
2. A consumer reads a stream entry with `XREADGROUP ... >` and crashes before `XACK`. Where is the entry now, why won't `>` return it again, and how does it get processed?
3. Why is Pub/Sub acceptable for cache invalidation but not for sending emails? What makes the cache case safe to lose a message?
