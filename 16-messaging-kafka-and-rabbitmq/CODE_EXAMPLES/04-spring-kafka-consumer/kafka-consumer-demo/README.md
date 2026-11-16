# kafka-consumer-demo

`@KafkaListener` and listener containers against embedded Kafka: concurrency vs partitions, rebalancing,
`auto.offset.reset`, and when offsets are committed.

- `ConcurrencyAndRebalanceTest` — `concurrency = "3"` on a 2-partition topic leaves one consumer with nothing; a
  second instance joining takes a partition, and leaving gives it back.
- `OffsetResetTest` — a new group with Kafka's default `latest` misses everything published before it joined;
  `earliest` gets it all.
- `CommitTimingTest` — commit then crash: the record is lost; process then crash: it's processed twice; Spring's
  default `AckMode.BATCH` commits only after the listener returns.

Run (no Docker):

```
mvn -q test
```

About 15 seconds.
