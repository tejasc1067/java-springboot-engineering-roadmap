# idempotent-demo

A redelivered payment credited twice, and the processed-events table that stops it.

- `RedeliveryTest` — both listeners crash once after the database commit, so Kafka delivers the payment again:
  - `naiveConsumer_aRedeliveredPaymentIsCreditedTwice` — 100 paid, balance 200.
  - `idempotentConsumer_theRedeliveryIsRecognisedAndSkipped` — the event id's primary key stops the second credit.
- `ConcurrentDuplicateTest` — two deliveries at the same moment:
  - check-then-insert without a constraint: both pass the check, balance 200;
  - with the primary key: one succeeds, one gets `DuplicateKeyException`, balance 100.
- `NaturalIdempotencyTest` — an upsert applied twice leaves one row.

Run (no Docker; embedded Kafka and in-memory H2):

```
mvn -q test
```
