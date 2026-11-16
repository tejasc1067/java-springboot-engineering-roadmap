# Ordering and Partition Keys

Kafka keeps messages in order, but only **within one partition**. A topic with several partitions has no order across them. So whether a consumer sees "order created, paid, shipped" in that order or "shipped, paid, created" depends on one choice the producer makes: the record's **key**. This topic shows the out-of-order bug, the key that fixes it, and the two ways a key itself causes trouble: one key carrying most of the traffic, and adding partitions later.

---

## The problem this solves

An order service publishes three events for each order to `order-events`: `CREATED`, `PAID`, `SHIPPED`. A status service consumes them and keeps "the latest status of each order". It trusts the arrival order, like nearly every consumer does: the last event it applied is the current status.

The topic has 3 partitions so three consumer threads can share the work (topic 04). If one order's three events land on three different partitions, three different consumers handle them, and nothing makes them take turns. The status service can apply `SHIPPED` first and `CREATED` last, and show a delivered order as "created".

## How it works

### Where a record goes

The producer picks a partition for every record:

| Record has… | Partition chosen by the default partitioner |
|---|---|
| an explicit partition number | that partition |
| a key | `murmur2(key bytes) % numberOfPartitions`, the same partition every time for the same key |
| no key | a "sticky" partition: it keeps sending to one partition until about `batch.size` (16 KB) has gone there, then picks another |

You can see the key rule in `PartitionKeyTest.aKeyAlwaysGoesToTheSamePartition_computedFromItsHash`. The partition the broker reports for every send equals `BuiltInPartitioner.partitionForKey(key, 3)`, which is the producer's own calculation:

```java
static int partitionFor(String key, int partitions) {
    return BuiltInPartitioner.partitionForKey(key.getBytes(StandardCharsets.UTF_8), partitions);
}
```

### Broken: no key

```java
kafka.send("order-events", "order-1:CREATED");   // no key
```

This is the trap: in development it usually *works*. `OutOfOrderTest.noKey_aFewSmallEventsStillLandOnOnePartition_soItLooksFineInDev` sends one order's three small events without a key, and all three land on the same partition. The sticky partitioner hasn't sent 16 KB yet, so it hasn't switched.

Under real traffic it switches all the time. `noKey_underRealVolume_manyOrdersHaveTheirEventsSplitAcrossPartitions` sends 100 orders × 3 events with `batch.size=100`, so the partitioner switches after ~100 bytes, the way a busy producer's does after 16 KB. About two thirds of the orders (62–68 of 100 across runs) have their three events split over more than one partition.

Once they're on different partitions, the order they're *read* in is up to the consumers. `eventsOnDifferentPartitions_areReadInWhateverOrderTheConsumersRun` puts `CREATED`, `PAID` and `SHIPPED` on partitions 0, 1 and 2, and lets the consumer for partition 0 be the slow one (in production: a GC pause, a slow database call, a rebalance):

```java
assertThat(view.receivedFor("order-9")).containsExactly(SHIPPED, PAID, CREATED);
assertThat(view.statusOf("order-9")).isEqualTo(CREATED);   // it shipped, the view says "created"
```

No error, no exception. Just a wrong status that's only reproducible under load.

### Fixed: key by the thing that needs ordering

```java
kafka.send("order-events", orderId, event);   // key = orderId
```

Same key, same partition, every time. One partition is read by exactly one consumer in a group, in offset order. So every event for `order-7` is read by one consumer, in the order it was sent.

`KeyedOrderingTest.keyedByOrderId_everyOrdersEventsArriveInOrder_evenWithThreeConcurrentConsumers` sends 50 orders × 3 events to the 3-partition topic, consumed by a listener with `concurrency = "3"`:

```java
@KafkaListener(topics = "order-events", groupId = "order-status", concurrency = "3")
public void on(String value) { view.apply(OrderEvent.parse(value)); }
```

Every order arrives as `CREATED, PAID, SHIPPED` and ends `SHIPPED`. Different orders are still processed in parallel; only events with the same key are kept in sequence. That's the whole trade: **you get ordering per key, and parallelism across keys.**

Choose the key as the smallest thing whose events must stay in order: the order id for order events, the account id for ledger entries. Not a random UUID per message (that's no better than no key), and not a constant like `"orders"` (that's one partition for everything).

### Broken: one key carries most of the traffic (a hot partition)

`oneHugeCustomerAsTheKey_makesOnePartitionHot` keys 1,000 events by customer, where one big retailer sends 70% of them. All of that customer's events hash to one partition, so it gets at least 700 of the 1,000 events while the others sit mostly idle.

One partition is read by one consumer thread. Adding consumers doesn't help: the extra ones get the quiet partitions or nothing. Lag builds on the hot partition only, and that customer's events get later and later.

Fix it by keying on something finer: if you only need ordering per order, key by order id, not customer id. If one entity really does produce that much, ask whether all of its events truly need one global order.

### Broken: adding partitions later

`murmur2(key) % numberOfPartitions` depends on the number of partitions. Change the number and most keys move:

```java
// addingPartitions_movesExistingKeysToOtherPartitions
partitionFor(key, 3) != partitionFor(key, 6)    // true for 507 of 1,000 order ids
```

Kafka lets you add partitions to a topic (never remove them), but it doesn't move existing records. An order whose key moved has its old events on the old partition and its new events on the new one. For a while, two consumers hold events for the same order, and they can be read out of order: the exact bug the key was meant to prevent.

So pick the partition count up front with room to grow. When you really must change it, create a new topic with more partitions and migrate to it deliberately, rather than adding partitions to a live keyed topic. This is the `hash % N` problem databases have too; [module 18 topic 07](../18-system-design-basics/07-partitioning-and-sharding.md) shows how consistent hashing limits it.

---

## When to use it / when not to

- **Use a key** whenever any consumer cares about the order of related events, or needs all events for one entity on one consumer (for example, an in-memory aggregate per account).
- **No key is fine** for independent events: page views, log lines, metrics. You get the most even spread across partitions.
- **One partition** gives total order across everything, and the throughput of one consumer. Rarely the right answer for a busy topic.

## Common pitfalls

- **No key, because it worked in testing.** The sticky partitioner keeps small test bursts on one partition. Key every event that has an ordering requirement.
- **A random UUID as the key.** Spread, but no ordering. Use the entity id.
- **A coarse key** (customer, tenant, `"global"`). Hot partitions and idle consumers. Use the finest id that still covers the ordering you need.
- **Adding partitions to a keyed topic.** Keys move and old and new events split across partitions. Size the partition count up front.
- **Retries reordering a key's events.** A non-blocking retry topic sends a failed event to the back of a queue while later events for the same key carry on (topic 07). If you need strict order, retry in place.

---

## Code examples

`CODE_EXAMPLES/05-ordering-and-partition-keys/ordering-demo/`. Run with `mvn -q test` (no Docker: `@EmbeddedKafka` starts Kafka inside the test). Read in this order:

1. `src/main/java/com/example/ordering/OrderEvent.java`: the events.
2. `src/main/java/com/example/ordering/OrderStatusView.java`: the consumer's view. The last event applied wins.
3. `src/test/java/com/example/ordering/OutOfOrderTest.java`: no key looks fine in dev, splits orders under volume, and gets read out of order.
4. `src/main/java/com/example/ordering/OrderEventsListener.java`: a listener with three concurrent consumers.
5. `src/test/java/com/example/ordering/KeyedOrderingTest.java`: key = order id, every order in sequence.
6. `src/test/java/com/example/ordering/PartitionKeyTest.java`: the hash, a hot partition, and what adding partitions does.

---

## Try this yourself

1. In `KeyedOrderingTest`, remove the key (`kafka.send("order-events", value)`) and set `spring.kafka.producer.batch-size=100` in the test's properties. Run it several times. How often does it fail, and why not every time?
2. In `PartitionKeyTest`, count how many keys move going from 6 to 12 partitions, and from 3 to 4. Why is it close to half for 3→6, but not for 3→4?

## Self-check

1. A topic has 6 partitions and 6 consumers in one group. Which events are guaranteed to be read in the order they were sent, and which aren't?
2. Your events have no key and every test passes. Explain why production can still see them out of order.
3. One customer produces most of your traffic and their events lag minutes behind everyone else's. What's happening, why doesn't adding consumers fix it, and what would you change?
