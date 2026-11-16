# Kafka Core Concepts

Kafka is the log from topic 01, scaled out. Five words cover almost all of it: **topics**, **partitions**, **offsets**, **brokers**, and **consumer groups**. This topic explains each with a real Kafka running inside the test, before Spring gets involved in topics 03 and 04, because every Kafka bug you'll meet later (lost order, idle consumers, lost or duplicate messages) is one of these five behaving exactly as designed.

The demo uses the plain Kafka client classes (`KafkaProducer`, `KafkaConsumer`, `AdminClient`) and `@EmbeddedKafka`, which starts real Kafka brokers inside the test JVM. No Docker.

---

## The problem this solves

Topic 01's log was one list on one machine. That has two limits. **Throughput**: one machine's disk and network cap how fast you can write and read. **Availability**: if that machine dies, so does every service that depends on it. Kafka splits each log into pieces spread across several machines, and keeps copies of every piece on more than one machine.

## How it works

### Topics and partitions

A **topic** is a named stream of records, such as `orders` or `payments`. A record is a key, a value, a timestamp, and optional headers, all stored as bytes.

A topic is split into **partitions**. Each partition is its own append-only log, like topic 01's `LogBroker`. A topic with 3 partitions is really 3 logs. Partitions are the unit of everything in Kafka: they're spread over machines for throughput, copied for safety, and handed out to consumers for parallelism.

### Offsets are per partition

`eachPartitionCountsItsOwnOffsets` writes `a1, a2, a3` to partition 0 and `b1, b2` to partition 1, interleaved:

```text
partition 0:  a1(offset 0)  a2(offset 1)  a3(offset 2)
partition 1:  b1(offset 0)  b2(offset 1)
```

There's no offset for "the topic". A record's address is *topic + partition + offset*.

### Order is guaranteed within a partition, and only there

`orderIsKeptWithinAPartition_notAcrossPartitions` sends `a1, b1, a2, b2, a3` (a's to partition 0, b's to partition 1) and reads them back. Within each partition the order is exactly as written. Across partitions, this run printed:

```text
delivered: [b1, b2, a1, a2, a3]
```

The consumer fetches from each partition separately, and nothing relates their positions. If two records must be processed in order (all events for one order, one account), they must be in the **same partition**. The producer does that with the record's **key** (topic 03), and topic 05 covers what goes wrong without it.

### Brokers and replication

A **broker** is one Kafka server. A **cluster** is several brokers. Each partition has a **replication factor**: how many brokers hold a copy. One copy is the **leader**, which takes every read and write for that partition. The others are **followers** that copy from the leader. Followers that are fully caught up form the **in-sync replicas (ISR)**.

`aTopicIsSplitIntoPartitions_eachReplicatedWithOneLeader` runs 3 brokers and creates a topic with 3 partitions and replication factor 3:

```text
partition 0: leader=2 replicas=[2, 0, 1] isr=[2, 0, 1]
partition 1: leader=0 replicas=[0, 1, 2] isr=[0, 1, 2]
partition 2: leader=1 replicas=[1, 2, 0] isr=[1, 2, 0]
```

Every broker holds every partition, but each broker *leads* a different one, so the work is spread. If broker 2 dies, one of the followers in partition 0's ISR becomes the leader and producers and consumers carry on. How many copies a write must reach before the producer is told "done" is the `acks` setting, which topic 03 demonstrates by stopping a broker.

In production, use replication factor 3. Clusters run in **KRaft** mode, where some nodes act as controllers and keep the cluster's metadata. Older clusters used ZooKeeper for this, which Kafka 4 has removed.

### Retention: consuming doesn't delete

`consumingDoesNotDelete_aGroupRemembersOnlyAnOffset` writes three payments and reads them as the `email` group, which commits. Then it asks Kafka what it stored for that group:

```java
admin.listConsumerGroupOffsets("email").partitionsToOffsetAndMetadata().get()
// payments-0 -> offset 3
```

Just one number per partition: the offset of the **next** record to read. Not "which messages were delivered", as a queue tracks. The records themselves are untouched: a consumer that seeks back to the beginning reads all three again.

Records are deleted by **retention**, per topic: by age (`retention.ms`, default 7 days) or by size (`retention.bytes`). There's also **log compaction** (`cleanup.policy=compact`), which keeps only the latest record for each key, useful for "current state per key" topics.

### Consumer groups

A **consumer group** is all the instances of one service reading a topic together, sharing a `group.id`. Kafka assigns each partition to exactly **one** consumer in the group, so within a group each record is processed once, and the group's instances split the work (topic 04 shows this with Spring). **Different groups are independent**: the same test's `analytics` group, created after everything was consumed, still gets all three payments.

This is how one Kafka topic acts as both a work queue *and* a fan-out:

```text
                        ┌── email group:     consumer 1 ← partition 0
topic "orders"          │                    consumer 2 ← partition 1
 partition 0 ─────────▶ │
 partition 1 ─────────▶ └── analytics group: consumer 1 ← partitions 0 and 1
```

One consequence: **partitions cap a group's parallelism**. A topic with 2 partitions can keep at most 2 consumers in one group busy. Choose the partition count with the maximum number of consumers you'll want in mind. You can add partitions later but can't remove them, and adding them changes which partition each key goes to (topic 05).

---

## When to use it / when not to

These are the decisions you'll make when creating a topic:

- **Partitions:** enough for the number of consumers you'll need at peak, plus some room. A few to a few dozen for a typical service topic. Each partition costs open files and memory on the brokers, so thousands "just in case" is a mistake too.
- **Replication factor:** 3 in production, so one broker can be down for maintenance while another fails. 1 only for local development, as in this demo.
- **Retention:** long enough for a consumer to be down over a weekend and catch up, and for any replay you plan. Compaction for topics that represent the latest state per key.

---

## Common pitfalls

- **Expecting order across a whole topic.** Kafka only orders within a partition. Put records that must stay in order under the same key.
- **More consumers than partitions.** The extra consumers sit idle (topic 04). Increase partitions, not consumers.
- **Replication factor 1 in production.** One broker failure loses data and stops the partition. Use 3.
- **Assuming a record is gone after it's consumed.** It stays until retention. Plan disk for retention, and don't put data in Kafka that must be deleted on request unless retention or compaction will do it.

---

## Code examples

`CODE_EXAMPLES/02-kafka-core-concepts/kafka-concepts-demo/`. Run with `mvn -q test` (no Docker; about 15 seconds). Read in this order:

1. `src/test/java/com/example/kafkaconcepts/KafkaClients.java` — the plain producer, consumer, and admin client, and the `pollUntil` helper.
2. `src/test/java/com/example/kafkaconcepts/TopicsAndPartitionsTest.java` — partitions with leaders and in-sync replicas across 3 brokers, offsets per partition, ordering within a partition only.
3. `src/test/java/com/example/kafkaconcepts/RetentionAndGroupsTest.java` — a group's committed offset, records still there after consumption, a second group reading everything.

---

## Try this yourself

1. In `orderIsKeptWithinAPartition_notAcrossPartitions`, send all five records to partition 0. What does `delivered` print now, and why is it guaranteed?
2. In `RetentionAndGroupsTest`, create the topic with `.configs(Map.of("retention.ms", "1000"))`, write three records, and keep polling for a minute. Do they disappear exactly after one second? (Look up `segment.ms`: Kafka deletes whole log segments, never single records.)

## Self-check

1. A topic has 4 partitions. What is an offset, and how many records in that topic can have offset 0?
2. What are a partition's leader, its followers, and its ISR? What happens to the partition when the leader's broker dies?
3. Two consumer groups read the same topic. One has 3 consumers, the other 1. How many times is each record processed, and by whom?
