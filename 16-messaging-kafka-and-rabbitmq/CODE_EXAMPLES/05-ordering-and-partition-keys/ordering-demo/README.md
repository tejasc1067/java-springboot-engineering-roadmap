# ordering-demo

Kafka keeps order only within a partition. One order's events, sent without a key, end up on different partitions
and are read out of order; keyed by order id, they never are.

- `OutOfOrderTest`
  - `noKey_aFewSmallEventsStillLandOnOnePartition_soItLooksFineInDev` — the sticky partitioner keeps a small burst together.
  - `noKey_underRealVolume_manyOrdersHaveTheirEventsSplitAcrossPartitions` — with `batch.size=100` it switches constantly;
    about two thirds of the orders end up split.
  - `eventsOnDifferentPartitions_areReadInWhateverOrderTheConsumersRun` — SHIPPED, PAID, CREATED; the view says "created".
- `KeyedOrderingTest` — 50 orders, key = order id, three concurrent consumers: every order in sequence.
- `PartitionKeyTest` — the partition is `murmur2(key) % partitions`; one big customer makes a hot partition;
  going from 3 to 6 partitions moves about half of all keys.

Run (no Docker; `@EmbeddedKafka` starts a broker inside the test JVM):

```
mvn -q test
```

The tests print how many orders were split (around 65 of 100, it varies) and how many of 1,000 keys changed partition (507).
