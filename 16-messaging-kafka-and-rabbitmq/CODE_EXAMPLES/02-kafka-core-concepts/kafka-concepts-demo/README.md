# kafka-concepts-demo

Topics, partitions, offsets, replication, retention, and consumer groups, against real Kafka brokers started inside the
test with `@EmbeddedKafka`. The tests use the plain Kafka clients, not Spring, so what you see is Kafka itself.

- `TopicsAndPartitionsTest` (3 brokers) — a 3-partition topic with replication factor 3: one leader per partition,
  spread over the brokers, all replicas in sync; offsets counted per partition; order kept within a partition only.
- `RetentionAndGroupsTest` — a group's committed offset is one number per partition; records are still there after
  being consumed; a second group reads them all.

Run (no Docker):

```
mvn -q test
```

About 15 seconds. `@EmbeddedKafka(kraft = true)`: spring-kafka-test 3.3 still starts a ZooKeeper-based broker unless
you ask for KRaft, the mode Kafka 4 uses exclusively. The tests print each partition's leader, replicas, and ISR, and
the order the consumer happened to deliver records from two partitions.
