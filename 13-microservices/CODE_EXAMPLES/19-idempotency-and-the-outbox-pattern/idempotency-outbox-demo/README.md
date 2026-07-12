# idempotency-outbox-demo

Two mechanisms that make "at-least-once, might-happen-twice" safe.

## Idempotency keys — `IdempotencyTest`

An operation the caller can safely retry: the same idempotency key does the work once and
returns the same result.

- **same key processes once** — two calls with `key-1`, one reservation, same result.
- **different keys process separately** — genuinely different requests.
- **concurrent duplicates of the same key still process once** — 12 racing threads, one
  reservation (`computeIfAbsent` makes check-and-do atomic).

## Transactional outbox — `OutboxTest` (real H2 transaction)

Reliably publish an event when you commit a DB change, without a distributed transaction.

- **dual-write loses the event on a crash** — commit the order, "crash" before the
  separate publish → order exists, event lost.
- **outbox writes order + event atomically, then the relay publishes** — both rows commit
  together; a relay publishes the outbox row afterwards (never lost).
- **a failure before commit leaves neither an order nor a phantom event** — the whole local
  transaction rolls back.
- **the relay is idempotent across reruns** — publishing twice emits the event once.

## Run

```bash
mvn -q test        # Tests run: 7, green
```

## Why they go together

The outbox guarantees **at-least-once** publishing (the relay may republish after a
restart), so consumers must be **idempotent** to not act twice. Retries (topic 10),
messaging (topic 07), and saga steps (topic 18) all create duplicates — idempotency is
what makes them safe.
