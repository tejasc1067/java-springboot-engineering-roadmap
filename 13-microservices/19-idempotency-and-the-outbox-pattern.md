# Idempotency and the Outbox Pattern

Three earlier topics all created the same hazard: retries repeat calls (topic 10), messaging delivers at-least-once (topic 07), and saga steps and compensations get re-run (topic 18). In every case, **the same operation can happen more than once**, and a timeout never tells you whether the first attempt already succeeded (topic 09). This topic is the two mechanisms that make "might-happen-twice" safe: **idempotency** (an operation you can repeat with no extra effect) and the **transactional outbox** (publishing an event reliably when you commit a change, without a distributed transaction). Neither is optional in a real distributed system — they're what keep retries and events from corrupting data.

The demos are runnable: idempotency keys in memory, and a real H2 transaction for the outbox.

---

## Part 1 — Idempotency: safe to repeat

An operation is **idempotent** if doing it twice has the same effect as doing it once. `GET` is naturally idempotent (reading twice changes nothing); so is "set status to CONFIRMED" (setting it twice lands on the same state). But "reserve 2 units" or "charge $20" are **not** — doing them twice reserves 4 or charges $40. Those are exactly the operations retries and duplicate deliveries endanger.

The general fix is an **idempotency key**: the caller generates a unique id for *this attempt* and sends it with the request. The server remembers which keys it has processed; a repeat of the same key returns the stored result instead of doing the work again. From the demo:

```java
public Reservation reserve(String idempotencyKey, String sku, int quantity) {
    return resultsByKey.computeIfAbsent(idempotencyKey, key -> {
        int n = workDone.incrementAndGet();      // the side-effecting work — at most once per key
        return new Reservation("reservation-" + n);
    });
}
```

The demo proves it: two calls with `key-1` do the work **once** and return the same result; different keys do separate work; and even 12 threads racing on the *same* key produce exactly **one** reservation (`computeIfAbsent` makes the check-and-act atomic). A client that times out (topic 09) and retries with the *same* key can't double-reserve — which is precisely what made retrying a `POST` unsafe until now.

Two things matter in practice:
- **The key must be stable across retries.** The client generates it once and reuses it on every retry of the *same* logical request. A new key per attempt defeats the purpose (each looks new).
- **Persist processed keys** in the database (a `processed_requests` table, or a unique constraint on the key), not a map — so idempotency survives a restart and works across instances. A unique constraint is a clean implementation: insert the key; a duplicate-key violation means "already processed," so you return the prior result.

## Part 2 — The transactional outbox: publish reliably

A service often must do two things atomically: commit a business change *and* publish an event about it (topic 07). The naive way is a **dual write** — commit to the database, then publish to the broker:

```java
orderRepository.save(order);   // write #1: the database
broker.publish(orderPlaced);   // write #2: the broker  <-- what if we crash right here?
```

These are two separate systems, and you cannot commit them atomically without a distributed transaction (which topic 18 told you to avoid). So there's a gap:
- **Crash after the DB commit, before publish** → the order exists but no event is ever sent. Downstream services never hear about it. **Lost event.** The demo's first test shows exactly this: order committed, broker empty.
- **Publish first, then the DB write fails** → you announced an order that doesn't exist. **Phantom event.**

The **transactional outbox** removes the gap. Instead of publishing directly, write the event into an `outbox` table **in the same local transaction** as the business change:

```java
c.setAutoCommit(false);
insertOrder(c, orderId, sku);                       // business row
insertOutbox(c, "OrderPlaced", "OrderPlaced:" + orderId); // the event to publish
c.commit();                                          // BOTH or NEITHER — one local transaction
```

Now the order and the "intent to publish" commit atomically — no distributed transaction needed, because both writes go to the *same* database. A separate **relay** (a background poller, or a change-data-capture tail of the DB log) reads unpublished outbox rows, publishes them to the broker, and marks them published:

```java
// relay: SELECT ... WHERE published = FALSE; publish each; UPDATE ... SET published = TRUE
```

The demo proves the full story: the order + event commit together (nothing lost); a failure before commit rolls back **both** (no order, no phantom event); and the relay, run twice, publishes the event **once** (idempotent across restarts).

## Why the two halves belong in one topic

The outbox guarantees the event is published **at-least-once** — the relay may publish, crash before marking the row, restart, and publish again. That's the safe failure mode (better duplicate than lost), but it means **consumers can receive the event twice**, so every consumer must be **idempotent** (part 1). The outbox reliably *emits* at-least-once; idempotency safely *absorbs* at-least-once. You need both: one without the other is either lossy or duplicative. This is the same at-least-once contract from topic 07, now with the machinery to honor it end to end.

## When to use it / when not to

- **Idempotency: on every state-changing operation that can be retried or redelivered.** That's essentially all of them in a distributed system (retries, at-least-once messaging, saga re-runs). Naturally-idempotent operations (pure reads, absolute "set to X") need no key; "add/charge/reserve/increment" style operations do.
- **The outbox: whenever a service must publish an event as part of committing a change.** It's the standard, correct way to avoid the dual-write problem. Simpler than it looks — one extra table and a poller.
- **You may not need the outbox** if the event isn't critical to lose (rare — usually it is), or if your platform provides transactional messaging / CDC out of the box (e.g. Debezium tailing the DB log is an outbox relay you don't write). But *never* rely on a bare dual write for an event that matters.
- **Don't reach for a distributed transaction (2PC) to solve the dual write.** The outbox is the microservices answer precisely because it keeps everything in one local transaction (topic 18).

## Common pitfalls

- **Retrying non-idempotent operations without a key** — the double-charge/double-reserve bug that timeouts (09) and retries (10) set up. Add an idempotency key.
- **A fresh idempotency key per retry** — every attempt looks new, so nothing is deduplicated. Generate the key once per logical request and reuse it.
- **Idempotency keys only in memory** — lost on restart, not shared across instances. Persist them (a table / unique constraint).
- **Bare dual write for a critical event** — the lost/phantom-event bug. Use the outbox.
- **Forgetting consumers must be idempotent** — the outbox (and any broker) is at-least-once, so a consumer that isn't idempotent acts twice on a redelivered event. Both halves are required.
- **Never cleaning up the outbox / processed-keys tables** — they grow forever. Archive or purge published outbox rows and old keys.

---

## Code examples

Runnable demo — `19-idempotency-and-the-outbox-pattern/idempotency-outbox-demo/`. Read then `mvn test`:

1. `idempotency-outbox-demo/src/main/java/com/example/idem/IdempotentReservationService.java` — the idempotency key mechanism (`computeIfAbsent`).
2. `idempotency-outbox-demo/src/test/java/com/example/idem/IdempotencyTest.java` — same key once, different keys separately, concurrent duplicates once.
3. `idempotency-outbox-demo/src/test/java/com/example/idem/OutboxTest.java` — dual-write loses an event; the outbox commits order+event atomically; failure rolls back both; the relay is idempotent (real H2 transaction).

## Try this yourself

1. In `IdempotencyTest`, change the service to *not* dedupe (call the work directly instead of `computeIfAbsent`) and re-run — watch `workDoneCount` become 2 (and 12 under the concurrent test). That's the double-apply bug the key prevents.
2. In `OutboxTest`, add a step to `placeOrderWithOutbox` that throws *after* both inserts but *before* `commit()`. Confirm neither row persists — one local transaction, all-or-nothing.
3. Wire it to the saga (topic 18): make each saga step reserve/charge with an idempotency key, so that when the orchestrator retries a step after a timeout it doesn't double-apply. Describe which key you'd use (hint: something stable per saga + step).

## Self-check

1. What makes an operation idempotent? Give one naturally-idempotent operation and one that isn't, and explain how an idempotency key makes the second safe to retry.
2. Describe the dual-write problem and the two ways it goes wrong. How does the transactional outbox eliminate it *without* a distributed transaction?
3. The outbox relay guarantees at-least-once publishing. What does that force every consumer to be, and why are idempotency and the outbox two halves of one solution?
