# Idempotent Consumers

Kafka consumers run at-least-once (topic 06): after a crash, a rebalance, or a retry, a record you already processed can arrive again. Module 13 topic 19 introduced idempotency keys and why every consumer must be idempotent. This topic makes it concrete for a Kafka listener that writes to a database: the double credit a redelivery causes, the processed-events table that stops it, and the race that sinks the "check first" version.

---

## The problem this solves

A ledger service consumes `payments` and credits accounts: `UPDATE account SET balance = balance + 100`. The listener's database transaction commits, and then, before the listener returns and Spring commits the offset, the instance dies. The next consumer to own the partition starts from the last committed offset, reads the same payment, and credits the account again. The customer paid 100 and has 200.

Redeliveries happen in normal operation, not just in disasters:

- **The listener throws after its side effect.** The error handler seeks back and retries the record (topic 07).
- **A rebalance.** A consumer that takes too long (`max.poll.interval.ms`) is removed from the group. Its partitions move to another consumer, which starts from the last *committed* offset, while the slow one may still be finishing the same record.
- **A crash or deploy** between the database commit and the offset commit.
- **Replays** from a dead-letter topic (topic 07), or resetting a group's offsets to reprocess.

(Producer retries can also write a record twice, but the idempotent producer, on by default since Kafka 3.0, removes those. Topic 03.)

## How it works

### Broken: a consumer that adds

```java
@Transactional
public void creditNaive(Payment payment) {
    jdbc.update("UPDATE account SET balance = balance + ? WHERE id = ?", payment.amount(), payment.account());
}
```

The demo's listener "crashes" once, after the database commit, so Spring delivers the record again (topic 06):

```java
@KafkaListener(topics = "payments-naive", groupId = "ledger-naive")
public void naive(String value) {
    accounts.creditNaive(Payment.parse(value));
    crashOnce(value);   // throws the first time
}
```

`RedeliveryTest.naiveConsumer_aRedeliveredPaymentIsCreditedTwice`: one payment of 100, a balance of 200.

### Fixed: a processed-events table, in the same transaction

Every event carries an id that the **producer** creates once (`evt-2`). The consumer records the id and makes the change in **one database transaction**, and a primary key makes recording the same id twice impossible:

```sql
CREATE TABLE processed_event (
    event_id VARCHAR(100) PRIMARY KEY
);
```

```java
@Transactional
public boolean creditOnce(Payment payment) {
    try {
        jdbc.update("INSERT INTO processed_event (event_id) VALUES (?)", payment.eventId());
    } catch (DuplicateKeyException alreadyApplied) {
        return false;                                  // seen it: do nothing
    }
    jdbc.update("UPDATE account SET balance = balance + ? WHERE id = ?", payment.amount(), payment.account());
    return true;
}
```

`idempotentConsumer_theRedeliveryIsRecognisedAndSkipped`: the redelivered `evt-2` hits the primary key, and the balance is 100 (105 after the next payment of 5).

Why both writes must be in one transaction:

- **Id saved, credit not** (separate transactions, crash between): the retry sees the id and skips. The payment is lost.
- **Credit saved, id not**: the retry doesn't see the id. Credited twice.

In one transaction they commit together or roll back together, so a retry after a rollback still applies the payment, and a retry after a commit is skipped.

The event id must come from the producer and stay the same on every redelivery. An id the consumer generates, or the Kafka offset of a record that may be *re-published* (a dead-letter replay gets a new offset), doesn't identify the event.

### Broken: check, then insert

The version people write first:

```java
Integer seen = jdbc.queryForObject("SELECT COUNT(*) FROM processed_event_no_key WHERE event_id = ?", Integer.class, id);
if (seen > 0) return false;
jdbc.update("INSERT INTO processed_event_no_key (event_id) VALUES (?)", id);
jdbc.update("UPDATE account SET balance = balance + ? WHERE id = ?", amount, account);
```

It handles a redelivery that comes *after* the first one finished. It fails when two deliveries of the same event run **at the same time**, which is exactly what a rebalance produces: the old owner of the partition is still processing the record when the new owner starts on it.

`ConcurrentDuplicateTest.checkThenInsert_withoutAConstraint_bothDeliveriesPassTheCheck` holds both deliveries just after their `SELECT`: both saw 0, both insert, both credit. Balance 200. It's the same check-then-act race as module 03c, between two machines instead of two threads, and `@Transactional` doesn't stop it: under the default isolation level, neither transaction can see the other's uncommitted insert.

### Fixed: let the constraint decide

`uniqueConstraint_onlyOneOfTwoConcurrentDeliveriesApplies` runs `creditOnce` twice at the same time. The database lets only one insert of `evt-11` succeed; the other gets `DuplicateKeyException` and does nothing. Balance 100. The `SELECT` was never what made it safe. The constraint is.

### Better still: operations that are naturally idempotent

Some changes are safe to apply twice by their nature: "set the email to X", "set the status to SHIPPED", an upsert of a full row.

```java
jdbc.update("MERGE INTO customer_email (customer, email) KEY (customer) VALUES (?, ?)", customer, email);
```

`NaturalIdempotencyTest.setToAValue_appliedTwice_isTheSameAsOnce`: applied twice, one row, same value. No processed-events table needed.

When you design an event, prefer **state** ("balance is now 300", "order 7 is SHIPPED") over **deltas** ("add 100") wherever the consumer doesn't need the delta. Note that set-to-value has its own catch: an old event redelivered *after* a newer one overwrites the newer value. If that can happen, include a version or timestamp in the event and only apply it when it's newer (`UPDATE ... WHERE version < ?`).

---

## When to use it / when not to

- **Every consumer with side effects needs one of these.** At-least-once is the normal case, not the edge case.
- **Naturally idempotent operations** need nothing extra (plus a version check if order can flip).
- **A processed-events table** for deltas, inserts, and anything that's not safe to repeat. It must live in the **same database** as the change, so one transaction covers both.
- **Side effects outside your database** (an email, a payment provider call) can't share a transaction with the table. Pass the event id to the other system as its idempotency key (module 13 topic 19), or record the intent in your database and have one job perform it.
- **Kafka transactions don't replace this** for database writes (topic 06).

The table grows with every event. Delete ids older than the longest redelivery window you can have (dead-letter replays included), for example older than 30 days.

## Common pitfalls

- **Check-then-insert without a unique constraint.** Concurrent duplicates both pass the check. Use a primary key or unique constraint and treat the violation as "already done".
- **Recording the id in a separate transaction from the change.** Lose the payment or double it, depending on which commits. One transaction.
- **An id the consumer invents** (a new UUID per delivery, or the offset of a replayed record). Every delivery looks new. Use an id the producer put in the event.
- **Keeping processed ids in memory.** Lost on restart and not shared between instances, while the duplicate usually arrives on *another* instance after a rebalance.
- **A long transaction around a slow listener.** If processing exceeds `max.poll.interval.ms`, the consumer is kicked out and the record goes to another consumer, creating the very concurrent duplicate this topic is about. Keep listeners fast.

---

## Code examples

`CODE_EXAMPLES/08-idempotent-consumers/idempotent-demo/`. Run with `mvn -q test` (no Docker: embedded Kafka and in-memory H2). Read in this order:

1. `src/main/resources/schema.sql`: the account table and the two processed-events tables, with and without a key.
2. `src/main/java/com/example/idempotent/AccountService.java`: `creditNaive`, `creditOnce`, `creditCheckThenInsert`, `setEmail`.
3. `src/main/java/com/example/idempotent/PaymentListeners.java`: two listeners that crash once after the database commit.
4. `src/test/java/com/example/idempotent/RedeliveryTest.java`: 200 vs 100 after a redelivery.
5. `src/test/java/com/example/idempotent/ConcurrentDuplicateTest.java`: the check-then-insert race, and the constraint that stops it.
6. `src/test/java/com/example/idempotent/NaturalIdempotencyTest.java`: set-to-value applied twice.

---

## Try this yourself

1. Split `creditOnce` into two methods, each `@Transactional`, one that inserts the id and one that credits, and call them one after the other from the listener. Make the listener crash *between* the two calls. What's the balance after the redelivery?
2. Add a `version` column to `customer_email` and change `setEmail` so an older version never overwrites a newer one. Write a test that applies version 2, then a redelivered version 1.

## Self-check

1. Name three things that cause a Kafka consumer to receive a record it already processed.
2. Your consumer checks `SELECT ... WHERE event_id = ?` before processing, and duplicates still get through in production, but never in tests. Explain the interleaving, and the change that fixes it.
3. Why must the processed-events insert and the business change be in the same transaction? Describe what goes wrong for each order of committing them separately.
