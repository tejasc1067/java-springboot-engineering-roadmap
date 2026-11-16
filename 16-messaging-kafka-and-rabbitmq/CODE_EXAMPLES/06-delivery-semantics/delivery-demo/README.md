# delivery-demo

When you commit the offset decides what a crash does: commit first and the message is lost, process first and
it's processed twice. Kafka transactions make consume-transform-produce exactly-once, for Kafka only.

- `CommitOrderTest` — plain `KafkaConsumer`, auto-commit off. Commit-then-process loses `order-1`;
  process-then-commit charges `order-2` twice.
- `TransactionsTest` — an aborted transaction's record is still in the log: `read_uncommitted` (the default) sees it,
  `read_committed` doesn't.
- `PaymentToReceiptListener` — reads a payment, sends a receipt, "sends an email", crashes once.
  - `NoTransactionTest` — two receipts, two emails.
  - `ExactlyOnceTest` — with `spring.kafka.producer.transaction-id-prefix`: one committed receipt, still two emails.

Run (no Docker):

```
mvn -q test
```

The listener tests log one `crash after sending` error each. That's the crash the tests expect.
