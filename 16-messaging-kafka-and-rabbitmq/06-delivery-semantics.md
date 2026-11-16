# Delivery Semantics: At-Most-Once, At-Least-Once, Exactly-Once

When a consumer crashes halfway through a message, one of two things happens next: the message is never processed, or it's processed again. Which one depends on a single decision: **do you commit the offset before or after you process?** This topic shows both crashes, then what Kafka's "exactly-once" really covers, and the part it can't cover: anything outside Kafka.

---

## The problem this solves

A consumer reads `order-2`, charges the customer's card, and commits the offset to say "done" (topic 04). The process can die at any moment: a deploy, an out-of-memory kill, a rebalance that hands the partition to someone else. If it dies between charging and committing, the next consumer starts from the last committed offset and charges `order-2` again. If you commit first and it dies before charging, the order is never charged.

There's no ordering of "process" and "commit" that avoids both. You choose which failure you'd rather have.

## How it works

### At-most-once: commit, then process

```java
KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10), 1);
consumer.commitSync();            // 1. "I've got it"
// 2. crash before processing: charge(...) never runs
```

`CommitOrderTest.commitThenProcess_crashInBetween_theMessageIsLost`: a new consumer in the same group polls and gets nothing. The group's committed offset says `order-2` was handled. **The message is lost**, silently.

At-most-once is acceptable only for data you can afford to lose: metrics samples, "user is typing" notifications.

### At-least-once: process, then commit

```java
KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10), 1).forEach(this::charge);   // 1. process
// 2. crash before commitSync()
```

`processThenCommit_crashInBetween_theMessageIsProcessedTwice`: the restarted consumer reads `order-2` again, and the customer is charged twice. **Nothing is lost, but anything can be duplicated.**

This is what almost every system chooses, and it's Spring Kafka's default. The listener container commits the offsets after your `@KafkaListener` method returns. If the method throws, the error handler seeks back and the record is delivered again (topic 07). `NoTransactionTest` shows it: the listener sends a receipt, then crashes, and the redelivery sends a second one.

At-least-once only works if consumers handle duplicates safely. That's topic 08, and it's not optional.

Duplicates can also come from the producer side: a producer that retries a send after a timeout may write the record twice. Kafka's **idempotent producer** (`enable.idempotence=true`, the default since Kafka 3.0) stops that by numbering each producer's records so the broker drops a retried copy (topic 03). It fixes producer retries only. It does nothing about a consumer processing a record twice.

### Kafka transactions

A Kafka producer can write several records, to several topics and partitions, as one **transaction**: all of them become visible, or none.

```java
producer.initTransactions();

producer.beginTransaction();
producer.send(new ProducerRecord<>("ledger", "debit 50 (aborted)"));
producer.flush();
producer.abortTransaction();

producer.beginTransaction();
producer.send(new ProducerRecord<>("ledger", "debit 20 (committed)"));
producer.commitTransaction();
```

`TransactionsTest.abortedRecords_areStillInTheLog_onlyReadCommittedConsumersSkipThem` shows the surprising part. An aborted record isn't deleted: it was already written to the partition, and the broker only adds a marker saying "aborted". **Whether a consumer sees it depends on the consumer's `isolation.level`**:

| Consumer `isolation.level` | Sees |
|---|---|
| `read_uncommitted` (Kafka's default) | `debit 50 (aborted)`, `debit 20 (committed)` |
| `read_committed` | `debit 20 (committed)` |

A transaction protects nobody whose consumers are on the default. Every consumer of a transactional topic needs `isolation.level=read_committed` (`spring.kafka.consumer.isolation-level: read_committed`).

(The test calls `flush()` before aborting so the record really reaches the broker. Without it, records still waiting in the producer's buffer are simply dropped on abort and never written.)

### Exactly-once: consume, transform, produce in one transaction

The pattern Kafka's exactly-once is built for: read from one topic, write the result to another, and commit the input offset **inside the same transaction** as the output. Either the output and the offset commit both happen, or neither does, so a redelivered input can't leave a duplicate output behind.

In Spring Boot it's one property:

```yaml
spring:
  kafka:
    producer:
      transaction-id-prefix: receipts-tx-     # turns on transactions
    consumer:
      isolation-level: read_committed
```

With it, Boot creates a `KafkaTransactionManager`, and the listener container starts a transaction before calling your listener. A `KafkaTemplate.send` inside the listener joins that transaction, and the container sends the consumed offset to the same transaction before committing. If the listener throws, the transaction aborts and the record is redelivered.

The demo's listener reads a payment, writes a receipt, "sends an email", and crashes the first time:

```java
@KafkaListener(topics = "payments", groupId = "receipts")
public void on(String payment) {
    kafka.send("receipts", "receipt for " + payment);   // part of the transaction
    emailsSent.add(payment);                              // NOT part of it: nothing can take an email back

    if (crashOnce.getAndSet(false)) {
        throw new IllegalStateException("crash after sending, before the transaction commits");
    }
}
```

Two runs of the same listener:

| | `NoTransactionTest` (no prefix) | `ExactlyOnceTest` (prefix set) |
|---|---|---|
| receipts a `read_committed` consumer sees | 2 | **1** |
| emails sent | 2 | **2** |

With transactions, the receipt from the crashed attempt was aborted, and only the retry's receipt is visible. **But the email went out twice.** Exactly-once in Kafka means: *records written to Kafka and offsets committed to Kafka* are exactly-once. Anything the listener does outside Kafka (a database write, an HTTP call, an email) is not in the transaction, and happens once per delivery.

### So what does "exactly-once" mean for my service?

For a consumer that writes to a database or calls another service, Kafka can't give you exactly-once. What you build instead is **effectively-once**: at-least-once delivery plus an idempotent consumer, so the second delivery has no effect (topic 08). That's the combination nearly every production system uses.

Kafka transactions are worth turning on when the pipeline is Kafka-to-Kafka (Kafka Streams uses them for exactly that), or when several records must appear together or not at all.

---

## When to use it / when not to

| Guarantee | How | Use for |
|---|---|---|
| At-most-once | commit, then process | data you can lose: metrics, presence |
| At-least-once | process, then commit (Spring's default) | almost everything, together with idempotent consumers |
| Exactly-once (Kafka) | transactions + `read_committed` | Kafka-to-Kafka pipelines, records that must appear atomically |

Transactions cost latency (each commit is extra round trips to the broker) and add operational parts (a transaction coordinator, transactional ids that must be unique per producer instance). Don't turn them on hoping they'll protect a database write. They won't.

## Common pitfalls

- **`enable.auto.commit=true` with a plain `KafkaConsumer`.** Offsets are committed on a timer, regardless of whether processing finished, so a crash can lose or duplicate records. (Spring's listener container sets it to false and commits itself.)
- **Transactions on the producer, default `isolation.level` on consumers.** Aborted records are delivered anyway. Set `read_committed` on every consumer of that topic.
- **Believing exactly-once covers the database or the email.** It covers Kafka only. Make the side effect idempotent (topic 08) or put it behind the outbox (module 13 topic 19).
- **Committing before an `@Async` hand-off.** If the listener passes the record to another thread and returns, the offset is committed before the work is done: at-most-once by accident.

---

## Code examples

`CODE_EXAMPLES/06-delivery-semantics/delivery-demo/`. Run with `mvn -q test` (no Docker). Read in this order:

1. `src/test/java/com/example/delivery/Clients.java`: plain Kafka producer and consumer with auto-commit off.
2. `src/test/java/com/example/delivery/CommitOrderTest.java`: commit-then-process loses the message; process-then-commit processes it twice.
3. `src/test/java/com/example/delivery/TransactionsTest.java`: an aborted transaction, read with both isolation levels.
4. `src/main/java/com/example/delivery/PaymentToReceiptListener.java`: consume, produce, side effect, crash once.
5. `src/test/java/com/example/delivery/NoTransactionTest.java`: without transactions, two receipts and two emails.
6. `src/test/java/com/example/delivery/ExactlyOnceTest.java`: with `transaction-id-prefix`, one receipt and still two emails.

---

## Try this yourself

1. In `ExactlyOnceTest`, read the receipts with `read_uncommitted` instead. Do you see the aborted receipt? Run it a few times. Why does the answer depend on whether the producer had sent its buffer before the abort?
2. Move the `kafka.send` in `PaymentToReceiptListener` *after* the crash check. Now what do the two tests report, and why?

## Self-check

1. A consumer charges a card, then commits the offset. It crashes between the two. What happens next, and what's this guarantee called?
2. Your producer uses transactions. A downstream team's consumer still sees records from aborted transactions. Why, and what's the one-line fix?
3. A colleague turns on Kafka transactions so that "each payment is written to the database exactly once". Explain why it won't work and what they should do instead.
