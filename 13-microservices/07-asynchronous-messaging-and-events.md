# Asynchronous Messaging and Events

A synchronous call (topic 05) couples two services in time: order-service can only get its answer if inventory-service is up *at that instant*, and it blocks a thread waiting. The alternative is **asynchronous messaging** — order-service publishes a message ("an order was placed") to a broker and moves on; other services consume it whenever they're ready. This trades the immediate answer for decoupling: the consumer can be slow, or down, or not exist yet, and the publisher doesn't care. This topic is when that trade is worth making, and the semantics you take on when you make it.

Kafka's internals (partitions, offsets, consumer groups) are module 16. Here we focus on the *pattern* — why you'd choose events over calls, and what "at-least-once" and "eventually consistent" cost you.

---

## The problem this solves

Placing an order should trigger a lot: send a confirmation email, notify the warehouse, update analytics, award loyalty points. If order-service does all of that with synchronous calls before returning `201`, then:

- The user waits for *all* of it — the response is as slow as the slowest downstream.
- If *any* of those services is down, the order fails — even though the order itself is fine and the email is not essential to it.
- order-service has to *know about* every one of those services and call each explicitly. Add a new one (fraud-check) and you edit and redeploy order-service.

None of those tasks needs to happen before the order is confirmed, and none needs an answer order-service will act on. That's the signature of work that should be asynchronous: **fire-and-forget, not ask-and-wait.**

## How it works: publish / consume through a broker

Instead of calling downstream services, order-service publishes an **event** — a statement that something happened — to a **broker**, and returns. Consumers subscribe and process on their own schedule:

```
                           ┌──▶ notification-service  (sends email)
order-service ──publish──▶ [ broker ] ──▶ shipping-service      (reserves a courier)
 "OrderPlaced"    (returns    queue        └──▶ analytics-service     (updates dashboards)
                  instantly)
```

order-service now knows only about the broker, not about its consumers. Adding fraud-check means fraud-check subscribes to `OrderPlaced` — **order-service doesn't change at all**. That's the decoupling: the publisher and consumers don't know about each other, don't have to be up at the same time, and can be deployed independently.

The demo (`async-events-demo`) uses an in-memory queue as a stand-in broker and proves the two behaviours that matter:

**1. The publisher doesn't block on the consumer.**
```java
// consumer takes 200ms per event; publishing 3 events still returns in < 1ms
long start = System.nanoTime();
for (int i = 1; i <= 3; i++) broker.publish(new OrderPlacedEvent((long) i, "SKU-PEN", 1));
long publishMillis = (System.nanoTime() - start) / 1_000_000;   // < 200ms; a sync call would block ~600ms
```

**2. The consumer can be down and catch up.**
```java
// nobody is consuming yet — publish anyway (a synchronous call would fail here)
for (int i = 1; i <= 5; i++) broker.publish(new OrderPlacedEvent((long) i, "SKU-BOOK", 1));
assertThat(broker.backlog()).isEqualTo(5);   // buffered
broker.startConsumer(...);                    // consumer comes online and drains the backlog
```

A synchronous call has neither property: it blocks for the full downstream time, and it fails outright if the downstream is down. The third test makes that contrast explicit — the sync notify throws when the downstream is down; the async publish just buffers.

## Events vs. commands

Two kinds of messages, and the distinction shapes your coupling:

- An **event** states a fact about the past: `OrderPlaced`, `StockChanged`, `PaymentCaptured`. The publisher doesn't know or care who reacts. Many consumers can subscribe. This is the loosely-coupled default.
- A **command** tells one specific service to do something: `ReserveStock`, `SendEmail`. It has one intended handler and expects it to act. Commands are more coupled (the sender knows the receiver) but still async (the sender doesn't block).

Prefer events for cross-service notification ("this happened, react if you care") and reserve commands for "I need this specific thing done, but not synchronously." Naming matters: events are past tense (`OrderPlaced`), commands are imperative (`ReserveStock`).

## Async into the order/inventory system

Two ways events reshape the scaffold's synchronous design:

1. **order-service publishes `OrderPlaced`.** Notifications, shipping, and analytics consume it. These were never good synchronous calls — they don't block the order decision — so they move off the critical path entirely.
2. **inventory-service publishes `StockChanged`; order-service keeps a local read model.** This is the "be told" option from topic 04: instead of calling inventory at read time, order-service maintains its own small, read-only copy of stock levels, updated by events. Reads become local (fast, and they survive inventory being down) — at the cost of the copy being *eventually* consistent (briefly stale after a change).

Note what stays synchronous: the actual stock *check that gates the order* (topic 05). You need that answer before confirming, and it must be authoritative — that's a genuine ask-and-wait. Events are for everything that can happen after, or that tolerates slight staleness.

## The semantics you take on

Async isn't free — it moves complexity from "handling failure now" to "handling time and duplicates later":

- **Eventual consistency.** After `OrderPlaced` is published, the warehouse doesn't know *yet* — there's a window where the order exists but shipping hasn't seen it. The system is correct *eventually*, not instantly. Your UX and your data model must tolerate that window (topic 18 goes deep on this with sagas).
- **At-least-once delivery.** Real brokers guarantee a message is delivered *at least* once, not *exactly* once — on retries or consumer restarts, the same event can arrive twice. So **every consumer must be idempotent**: processing `OrderPlaced` twice must not send two emails or reserve stock twice. This is why topic 19 (idempotency) is a required companion to messaging, not optional.
- **"Exactly-once" is mostly a myth.** You approximate it with at-least-once delivery + idempotent consumers, not with a magic broker setting. Design for duplicates. Module 16 [topic 06](../16-messaging-kafka-and-rabbitmq/06-delivery-semantics.md) shows each guarantee with real offset commits, and [topic 08](../16-messaging-kafka-and-rabbitmq/08-idempotent-consumers.md) builds the idempotent consumer.
- **Ordering is limited.** Messages aren't globally ordered; at best they're ordered within a partition/key. Don't assume event B (published after A) is processed after A unless you've arranged for it (partition keys, module 16 [topic 05](../16-messaging-kafka-and-rabbitmq/05-ordering-and-partition-keys.md)).
- **Events have contracts too.** An event's schema is an API (topic 06) — adding fields is safe for tolerant consumers, renaming/removing is breaking. Version events the same way you version endpoints. Module 16 [topic 09](../16-messaging-kafka-and-rabbitmq/09-schemas-and-serialization.md) covers event schemas and compatibility.

## Poison messages and dead letters

A **poison message** is one the consumer can never process: a malformed payload, a SKU that doesn't exist, a bug triggered by one specific value. Retrying won't help — it fails the same way every time. With no plan for it, there are two bad outcomes:

1. **The consumer dies.** The handler throws, nothing catches it, the consumer stops. Every good message queued behind the poison one waits forever. The demo's `startConsumer` does exactly this:
   ```java
   broker.publish(order1);  broker.publish(poison);  broker.publish(order3);
   broker.startConsumer(handler);          // handler throws on the poison message
   // processed == 1, backlog == 1 — order3 is stuck, and nobody was told
   ```
2. **The consumer retries forever.** The message goes back to the head of the queue and fails again, in a tight loop. Same result — nothing behind it moves — plus a CPU spike and a flood of identical error logs.

The fix is **bounded retries, then a dead-letter queue (DLQ)**:

```java
broker.startConsumerWithDeadLetter(handler, 3);
// poison message: tried 3 times, then moved to broker.deadLetters()
// order1 and order3: processed normally; the consumer is still alive
```

A DLQ is just another queue (Kafka: a dead-letter *topic*; RabbitMQ: a dead-letter *exchange*) where failed messages are parked with the error that killed them. Then:

- **Alert on it.** A message in the DLQ is a bug or bad data. If nobody watches the DLQ, it's a silent data-loss bin.
- **Fix, then replay.** After fixing the consumer bug, move the messages back to the main queue. This is safe only if the consumer is idempotent (topic 19) — some of those messages may have partly succeeded.
- **Retry only what can succeed.** A timeout calling the email provider is transient — retry with backoff (topic 10). A JSON parse error is permanent — send it straight to the DLQ.

Module 16 does this with real brokers: Spring Kafka's `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` ([topic 07](../16-messaging-kafka-and-rabbitmq/07-retries-and-dead-letter-topics.md)), and RabbitMQ dead-letter exchanges ([topic 11](../16-messaging-kafka-and-rabbitmq/11-spring-amqp.md)).

## When to use it / when not to

**Use async messaging when:**
- The work doesn't need to finish before you respond (email, analytics, downstream notifications).
- You want to fan out one occurrence to many consumers without the source knowing them.
- You want to survive a consumer being down (buffering) or smooth out load spikes (the queue absorbs bursts).
- You're willing to accept eventual consistency for the decoupling.

**Stay synchronous when:**
- You need the answer *now* to make the current decision (the stock check that gates the order).
- The caller genuinely cannot proceed without the result, and staleness is unacceptable.
- Strong, immediate consistency is required for correctness.

Most real systems are a mix: synchronous for the authoritative decision on the critical path, asynchronous for everything that reacts to it.

## Common pitfalls

- **Non-idempotent consumers.** Assuming exactly-once delivery. The email service sends duplicates on the first retry. Make consumers idempotent (topic 19).
- **Publishing after the transaction, non-atomically.** order-service commits the order to its DB, then crashes before publishing `OrderPlaced` → the order exists but no one is notified (a lost event). The fix is the transactional outbox (topic 19), not "publish and hope."
- **Using async where you needed an answer.** Firing an event to "check stock" and then confirming the order anyway is a correctness bug — that check must be synchronous.
- **Treating eventual consistency as instant.** Reading your own write immediately after an event and expecting the consumer to have processed it. It hasn't yet. Design the UX for the lag.
- **No dead-letter queue.** One malformed message kills the consumer or loops forever, and every good message behind it stops. Bound the retries and park failures in a DLQ that someone is alerted on.
- **Unversioned event schemas.** Renaming a field in `OrderPlaced` silently breaks every consumer (topic 06 applies to events verbatim — it's the silent-break demo, over a broker).

---

## Code examples

Runnable demo — `07-asynchronous-messaging-and-events/async-events-demo/`. Read then `mvn test`:

1. `async-events-demo/src/main/java/com/example/asyncevents/OrderPlacedEvent.java` — an event (past-tense fact) vs a command.
2. `async-events-demo/src/main/java/com/example/asyncevents/MessageBroker.java` — the in-memory stand-in broker: `publish()` returns immediately; messages buffer until a consumer runs.
3. `async-events-demo/src/test/java/com/example/asyncevents/AsyncEventsTest.java` — buffer-while-down, publish-doesn't-block, sync-fails-vs-async-buffers; then a poison message killing a consumer with no error handling, and bounded retries + dead-letter keeping the queue moving.

The in-memory broker is deliberately minimal; module 16 (Kafka, RabbitMQ) is the real thing with durability, consumer groups, and replay. Its [topic 01](../16-messaging-kafka-and-rabbitmq/01-why-message-brokers.md) starts by contrasting this queue with Kafka's log, and [topic 12](../16-messaging-kafka-and-rabbitmq/12-kafka-vs-rabbitmq-vs-sqs.md) helps you choose a broker.

## Try this yourself

1. In `AsyncEventsTest`, add a *second* consumer subscribing to the same events (start two consumers before publishing). With the in-memory queue, each message goes to only one consumer — note that. A real broker's *consumer groups* and *topics* are what give you true fan-out (every service gets every event); reasoning about why the toy can't do that is the lesson (module 16).
2. In `retriesThenDeadLetterKeepsTheQueueMoving`, make the poison message fail only on its first attempt (a transient error). Confirm it is processed and the DLQ stays empty. Then change `maxAttempts` to 1 and see the same transient failure land in the DLQ — that is the cost of retrying too little.
3. Sketch (no code) how you'd convert the scaffold's synchronous stock check into an event-driven local read model in order-service: what event would inventory publish, what would order-service store, and what's the consistency window you just introduced?

## Self-check

1. Give two properties an asynchronous event has that a synchronous call does not, and name one property a synchronous call has that you *lose* by going async.
2. A consumer has no error handling and receives one malformed message. What happens to the messages queued behind it? What two things does a dead-letter setup add, and why is replaying from a DLQ only safe with an idempotent consumer?
3. Why must every message consumer be idempotent? What delivery guarantee makes this non-negotiable, and which later topic builds the mechanism?
4. In the order/inventory system, which interaction should stay synchronous and which should become events — and what's the consistency trade-off of moving the stock lookup to an event-fed local read model?
