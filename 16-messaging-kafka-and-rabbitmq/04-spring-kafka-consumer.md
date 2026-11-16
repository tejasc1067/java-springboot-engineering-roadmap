# Spring Kafka Consumer

On the consuming side, Spring turns a method into a Kafka consumer with one annotation, `@KafkaListener`. Behind it, a **listener container** polls Kafka, calls your method, and commits offsets. This topic covers the three things that container decides for you: how partitions are shared among your instances, where a new consumer starts reading, and *when* the offset is committed, which decides whether a crash loses messages or repeats them.

The demo is a Spring Boot app with `@EmbeddedKafka` (no Docker).

---

## The problem this solves

The email service consumes `orders`. It runs as several instances and gets restarted on every deploy. The team needs to know:

- If we run 3 instances, are they all working?
- When we deploy a brand-new service, does it see orders placed before it existed?
- If an instance crashes halfway through an order, is that order emailed, skipped, or emailed twice?

The answers are all consumer settings, and two of the defaults surprise people.

## How it works

### `@KafkaListener`

```java
@KafkaListener(id = "email", topics = "orders", groupId = "email", concurrency = "3")
public void onOrder(ConsumerRecord<String, String> record) {
    sent.add(record.value());
}
```

- `groupId` is the consumer group (topic 02). Every instance of the email service uses the same one, so they share the work. A different service uses its own group and gets its own copy of every record.
- `concurrency = "3"` starts three consumers for this listener in this instance, each on its own thread.
- The method can take the value, the whole `ConsumerRecord` (key, partition, offset, headers), or an `Acknowledgment` for manual commits (below).

`theListenerReceivesWhatIsPublished` publishes two orders and waits until both arrive.

### Consumers beyond the partition count do nothing

Kafka gives each partition to exactly one consumer in a group. `orders` has 2 partitions, and the listener has `concurrency = "3"`. `moreConsumersThanPartitions_theExtraConsumerSitsIdle` prints the assignment:

```text
email group assignments: {consumer-email-1=[orders-0], consumer-email-2=[orders-1], consumer-email-3=[]}
```

The third consumer has no partition and never receives anything. The same happens across instances: scale the email service to 5 pods on a 2-partition topic, and 3 of them are idle. **To process faster, add partitions first.** Total consumers in a group (instances × concurrency) should be at most the partition count.

### Rebalancing: partitions move when the group changes

`aConsumerJoiningOrLeaving_reassignsThePartitions` uses the `payments` topic (2 partitions) and the `ledger` group:

1. One instance starts: it gets both partitions.
2. A second instance starts. The group **rebalances**, and they get one partition each, never the same one.
3. The second instance stops. Another rebalance, and the first takes both back.

That's how scaling works without any configuration: start an instance, it joins the group, and partitions move to it. A rebalance also happens when a consumer is considered dead. That happens when it stops sending heartbeats, or takes longer than `max.poll.interval.ms` (5 minutes by default) between polls because one batch is too slow. During a rebalance the moving partitions are paused, and records that were processed but not yet committed are delivered again to their new owner. So frequent rebalances mean pauses and duplicates.

### Where a new group starts: `auto.offset.reset`

A group with a committed offset resumes from it. A **new** group has none, and `auto.offset.reset` decides: `earliest` (the oldest record still retained) or `latest` (only records written from now on).

Kafka's default is **`latest`**, and Spring Boot doesn't change it. `aNewGroupWithTheDefaultLatest_missesEverythingPublishedBeforeItJoined`:

```java
kafka.send("invoices", "invoice-1");    // 1, 2, 3 published before billing existed
...
// billing: a new group, nothing configured
billing joins, then invoice-4 is published

billing receives: [invoice-4]           // invoice-1, 2, 3: never
```

The `audit` group, configured with `earliest`, receives all four. Deploy a new consumer service with the default, and every event published before its first start is skipped, silently. Set it explicitly:

```yaml
spring:
  kafka:
    consumer:
      auto-offset-reset: earliest
```

`latest` is right only when old records are useless to the new consumer, for example live price ticks.

### When the offset is committed

The committed offset means "everything before this is done". The order of *process* and *commit* decides what a crash does. The demo plays both orders with a plain consumer and a simulated crash.

**Broken: commit, then process.** `commitBeforeProcessing_aCrashLosesTheMessage`:

```java
pollUntil(first, 1);
first.commitSync();          // "I've got it"
// ...crash, before processing
// restart: resumes after the committed offset - the refund is never paid
```

The record is marked done but never processed. Nothing errors, nothing is logged. This is **at-most-once**.

**Process, then commit.** `processThenCommit_aCrashInBetweenProcessesTheMessageTwice`:

```java
pollUntil(first, 1).forEach(r -> processed.add(r.value()));
// ...crash, before commitSync()
// restart: the record is delivered again
processed == [ship-order-1, ship-order-1]
```

Nothing is lost, but the parcel ships twice. This is **at-least-once**, and it's the right default: a duplicate can be detected and ignored (topic 08), a lost message can't be recovered. Topic 06 goes through all three delivery guarantees.

### What Spring does by default

Kafka's own consumer commits automatically (`enable.auto.commit=true`): every 5 seconds, during `poll()`, it commits whatever the last poll returned. That's commit-on-a-timer, unrelated to whether you finished. Spring's listener container turns that off (unless you set it yourself) and commits on your behalf according to its **`AckMode`**:

| `AckMode` | Commits |
|---|---|
| `BATCH` (default) | after the listener has returned for every record from one `poll()` |
| `RECORD` | after the listener returns for each record |
| `MANUAL` / `MANUAL_IMMEDIATE` | when your code calls `acknowledgment.acknowledge()` |

`springsDefault_commitsAfterTheListenerReturns` checks that the default is `BATCH`, holds the listener inside its method, and reads the group's committed offset: **0** while the listener is still working, **1** once it returns. So a plain `@KafkaListener` is already process-then-commit, at-least-once.

What happens when the listener *throws* is the error handler's job: by default Spring's `DefaultErrorHandler` retries the record immediately 9 more times (10 attempts in all), then logs it and moves on, committing past it. Topic 07 replaces that with retries and a dead-letter topic.

---

## When to use it / when not to

- **Concurrency:** set instances × `concurrency` ≤ partitions. More is wasted threads.
- **`auto-offset-reset: earliest`** for almost every business consumer. `latest` only when old records have no value to a new consumer.
- **Default `BATCH` ack mode** for most listeners. `RECORD` if processing is slow and you want less redelivered after a crash, at the cost of more commits. `MANUAL` when processing hands work to another thread and you must commit only after it finishes.
- **Never commit before processing** for anything that matters.

---

## Common pitfalls

- **More consumers than partitions.** The extra ones are idle. Add partitions.
- **A new service with the default `latest`.** It silently skips every record published before its first start. Set `auto-offset-reset: earliest`.
- **Slow processing inside the poll loop.** If one batch takes longer than `max.poll.interval.ms`, the consumer is removed from the group, its partitions are reassigned, and the batch is processed again elsewhere: a loop of rebalances and duplicates. Lower `max.poll.records` or speed up processing.
- **Assuming each record arrives once.** At-least-once means duplicates after every crash and rebalance. Make the listener idempotent (topic 08).

---

## Code examples

`CODE_EXAMPLES/04-spring-kafka-consumer/kafka-consumer-demo/`. Run with `mvn -q test` (no Docker; about 15 seconds). Read in this order:

1. `src/main/java/com/example/consumer/ConsumerDemoApplication.java` — the `orders` topic with 2 partitions.
2. `src/main/java/com/example/consumer/OrderEmailListener.java` — `@KafkaListener` with `concurrency = "3"`.
3. `src/test/java/com/example/consumer/ConcurrencyAndRebalanceTest.java` — the idle third consumer; partitions moving as instances join and leave.
4. `src/test/java/com/example/consumer/OffsetResetTest.java` — a new group with `latest` misses earlier records; `earliest` doesn't.
5. `src/test/java/com/example/consumer/CommitTimingTest.java` — commit-then-crash loses a record, process-then-crash repeats it, and Spring commits after the listener returns.

---

## Try this yourself

1. Change `OrderEmailListener` to `concurrency = "2"` and adjust `moreConsumersThanPartitions_theExtraConsumerSitsIdle`. Then change the `orders` `NewTopic` in `ConsumerDemoApplication` to 4 partitions. How are 4 partitions spread over 2 consumers?
2. Write a listener with `AckMode.MANUAL` (set it on a container from the factory, as `CommitTimingTest` does) that takes an `Acknowledgment` and only calls `acknowledge()` for even-numbered records. Restart the container and see which records come back.

## Self-check

1. The email service runs 4 instances with `concurrency = "2"` on a topic with 6 partitions. How many consumers are in the group, and how many do nothing?
2. A new analytics service is deployed and finds the first week of orders missing. Which setting caused it, and what's its default?
3. Explain why committing before processing gives at-most-once and processing before committing gives at-least-once. Which one does a plain `@KafkaListener` use, and how did the test show it?
