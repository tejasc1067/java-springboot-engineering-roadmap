# Kafka vs RabbitMQ vs SQS

You've now used two brokers that look alike from a distance: a producer sends a message, a consumer receives it. Underneath, they're built on different ideas. Kafka is a **log** that keeps events and lets each reader track its own place. RabbitMQ is a **queue** (more precisely, a router in front of queues) that hands each message to a consumer and deletes it once it's acknowledged. Amazon SQS is a fully managed queue. Choosing the wrong one usually doesn't fail on day one. It fails months later, when someone needs to replay last week's events, or add a fifth consumer that turns out to do nothing.

This topic compares them on the properties that decide real projects, and the demo runs the same two scenarios on Kafka and RabbitMQ so you can see the differences, not just read about them.

---

## The problem this solves

A team picks a broker because "everyone uses Kafka" or "we already run RabbitMQ". Then:

- Analytics joins in March and asks for every `OrderPlaced` event since January. On RabbitMQ those messages were deleted the moment billing acknowledged them.
- Orders pile up, so the team scales the shipping consumer from 2 to 4 instances on Kafka. Throughput doesn't move, because the topic has 2 partitions.
- A payment reminder must go out in 30 minutes. Kafka has no per-message delay, so someone builds a polling table next to it.

Each of these is a property of the broker's model, not a configuration mistake. Know them before you choose.

## How it works: log vs queue, side by side

```text
Kafka (log)                                          RabbitMQ (queue)

topic "orders", partition 0                          exchange "orders" --> queue "billing"   [m4][m5]
[m1][m2][m3][m4][m5] ...  (kept for days)                              \-> queue "analytics" (empty: bound too late)
          ^           ^
   billing group   analytics group                   a message is deleted once a consumer acks it
   (offset 2)      (offset 5)
```

- **Kafka** appends every event to a partition and keeps it for the retention period (7 days by default), whether or not anyone has read it. Each consumer group just remembers an **offset**, its position in the log ([02-kafka-core-concepts.md](02-kafka-core-concepts.md), [04-spring-kafka-consumer.md](04-spring-kafka-consumer.md)).
- **RabbitMQ** routes each message through an exchange to every queue bound to it *at that moment* ([10-rabbitmq-core-concepts.md](10-rabbitmq-core-concepts.md)). A queue delivers each message to one of its consumers, and deletes it when that consumer acknowledges it ([11-spring-amqp.md](11-spring-amqp.md)).

### Scenario 1: replay

`KafkaReplayAndScalingTest.aNewConsumerGroup_addedLater_readsEveryPastEvent` publishes 5 events. The `billing` group reads them. Then a brand-new group, `analytics`, starts with `auto.offset.reset=earliest`:

```java
assertThat(readAll(broker, "orders-replay", "billing")).hasSize(5);
assertThat(readAll(broker, "orders-replay", "analytics")).hasSize(5);   // the past is still there
```

`RabbitReplayAndScalingTest.aQueueBoundAfterPublishing_getsNothing_theEventsAreGone` does the same on RabbitMQ. Billing's queue gets and acknowledges all 5, after which its message count is 0. Analytics then binds its own queue to the same exchange:

```java
assertThat(TEMPLATE.receiveAndConvert(analytics.getName(), 1_000)).isNull();   // nothing: they were never routed here
```

On RabbitMQ, a consumer that didn't exist when the message was published will never see it. On Kafka, any new reader can start from the beginning of the retained log, or from any offset.

### Scenario 2: scaling consumers

`fourConsumersInOneGroup_onTwoPartitions_onlyTwoGetWork` starts 4 consumers in one Kafka group on a 2-partition topic:

```java
container.setConcurrency(4);
...
assertThat(container.getAssignmentsByClientId()).hasSize(4);   // 4 consumers really joined
assertThat(consumersWithAPartition(container)).isEqualTo(2);   // the other 2 sit idle
```

Within a group, each partition belongs to exactly one consumer. With 2 partitions, consumers 3 and 4 get nothing, and 40 messages are handled by exactly 2 threads. The partition count is the ceiling on a group's parallelism. You can add partitions later, but that moves keys between partitions and breaks per-key ordering for a while ([05-ordering-and-partition-keys.md](05-ordering-and-partition-keys.md)).

(The test waits for the group to settle first. Consumers join one at a time and each join triggers a rebalance, so for a moment the first consumer can own both partitions. Topic 04 covers rebalancing.)

`fourConsumersOnOneQueue_allFourGetWork` starts 4 consumers on one RabbitMQ queue, with a prefetch of 1. Each handler waits until 4 messages are being handled at the same time, which is only possible if 4 different consumers each hold one:

```java
container.setConcurrentConsumers(4);
container.setPrefetchCount(1);
...
assertThat(fourInProgress.await(10, TimeUnit.SECONDS)).isTrue();
assertThat(threadsThatGotWork).hasSize(4);
```

Competing consumers on a queue scale by just adding consumers. The price: once several consumers share a queue, nothing keeps related messages in order.

## The comparison

| | Kafka | RabbitMQ | SQS |
|---|---|---|---|
| **Model** | Partitioned, replicated log | Exchanges route to queues | Managed queue |
| **After a message is consumed** | Stays until retention expires (default 7 days, or forever with compaction) | Deleted on ack | Deleted when the consumer deletes it |
| **Replay / new reader gets history** | Yes: reset the group's offset, or start a new group | No (classic and quorum queues). RabbitMQ *Streams* add a log-style replay | No |
| **Ordering** | Per partition, so per key | Per queue, with one consumer. Lost once several consumers share it | Standard: best effort. FIFO queues: per message group |
| **Consumer scaling** | Up to the number of partitions per group | Add consumers to the queue | Add consumers; effectively unlimited |
| **Acknowledgement** | Commit an offset: "everything before here is done" | Ack or reject each message; rejected ones can be requeued or dead-lettered | Delete each message; if it isn't deleted before the visibility timeout ends, it reappears |
| **Routing** | By topic and key. Filtering happens in the consumer | Rich: direct, topic (wildcards), fanout, headers exchanges | None: one queue. Fan-out needs SNS in front |
| **Delayed messages** | No | Plugin, or TTL plus a dead-letter exchange | Yes, up to 15 minutes |
| **Priorities** | No | Yes (priority queues) | No |
| **Throughput** | Very high (sequential disk writes, batching) | High, lower per node than Kafka | High, managed for you |
| **Operations** | Heaviest: brokers, partitions, replication, controllers | Moderate: a cluster with quorum queues | None to run; you pay per request and are tied to AWS |

**SQS in two paragraphs** (conceptual; no cloud code in this roadmap). SQS is Amazon's managed queue: no servers, nothing to patch, scales without planning. A consumer receives a message, and SQS hides it for the **visibility timeout** (30 seconds by default). If the consumer deletes it within that time, it's done. If the consumer crashes or is slow, the message becomes visible again and another consumer gets it. That makes delivery at-least-once, so consumers must be idempotent ([08-idempotent-consumers.md](08-idempotent-consumers.md)), and the timeout must be longer than the slowest processing or the message gets processed twice.

**Standard** queues have nearly unlimited throughput but may deliver out of order and occasionally twice. **FIFO** queues keep order within a *message group* and drop duplicates sent within a 5-minute window, at lower throughput. Messages are kept up to 14 days, with no replay. Failed messages go to a dead-letter queue after a configured number of receives.

**Redis Streams** ([module 15 topic 11](../15-caching-and-redis/11-redis-pub-sub-and-streams.md)) belong in this conversation too. They have consumer groups, acknowledgements, pending messages, and replay by id. If Redis is already in your stack and the volume is moderate, a stream may be all the broker you need. They're limited by memory and have weaker durability guarantees than Kafka's replicated log.

## When to use which

| You need... | Choose | Why |
|---|---|---|
| Several independent consumers of the same events, including ones added later | Kafka | Each group reads the retained log at its own pace |
| To rebuild a read model or reprocess after a bug | Kafka | Reset the offset and replay ([module 13 topic 22](../13-microservices/22-cqrs-and-event-sourcing.md)) |
| A task queue: "resize this image", "send this email" | RabbitMQ or SQS | Per-message ack, retry, dead-letter; scale by adding workers |
| Routing by pattern (`order.*.eu`) to different consumers | RabbitMQ | Topic exchanges do it in the broker |
| Delays or priorities | RabbitMQ (or SQS for short delays) | Kafka has neither |
| Very high event volume (clicks, telemetry, CDC streams) | Kafka | Built for sequential throughput and long retention |
| No broker to run, already on AWS | SQS (with SNS for fan-out) | Fully managed |
| A small work queue, Redis already running | Redis Streams | No new system |

## Five scenarios

1. **An e-commerce platform publishes `OrderPlaced`, consumed by billing, shipping, email, and (next quarter) a fraud model trained on history.** Kafka. Several consumers each need every event, and the fraud team will want to replay months of history. With RabbitMQ, each new consumer would start empty.

2. **A photo service turns uploads into thumbnails. Jobs take 2–20 seconds; a failed one should be retried twice and then parked.** RabbitMQ (or SQS on AWS). Each job is independent, workers scale by count, and per-message ack with a dead-letter exchange (topic 11) is exactly the retry-and-park behaviour wanted. On Kafka, a slow job blocks its whole partition, and parallelism is capped by partitions.

3. **A bank must process account events in exact order per account, at high volume.** Kafka, with the account id as the key (topic 05). Order holds per key across many partitions, so it scales. A single RabbitMQ queue keeps order only with one consumer; sharding queues by account works but is more to build.

4. **A small internal app sends "your report is ready" notifications, a few hundred a day, and already uses Redis for caching.** Redis Streams, or even a database table polled by a scheduled job. Running Kafka for this is all cost and no benefit.

5. **A SaaS on AWS must send reminder emails 10 minutes after sign-up, with no ops team.** SQS with a delivery delay. It's managed, supports per-message delays up to 15 minutes, and the team runs nothing. Longer delays need a scheduler instead.

Many systems use two: Kafka for business events between services, RabbitMQ or SQS for task queues inside one service. That's fine. Using the right model for each flow beats forcing one broker to do both.

## Common pitfalls

- **Choosing a queue and later needing replay.** Messages are gone after ack. Choose a log (Kafka, RabbitMQ Streams) if history might matter, or also write events to storage.
- **Too few Kafka partitions.** More consumers than partitions means idle consumers. Size partitions for the parallelism you expect to need, since adding them later reshuffles keys.
- **Several consumers on a RabbitMQ queue that needs order.** Order is lost the moment two consumers share the queue. Use one consumer, or shard into one queue per key range.
- **An SQS visibility timeout shorter than processing time.** The message reappears while still being processed and is handled twice. Set it above the slowest processing time, and keep consumers idempotent.
- **Picking Kafka for a task queue.** One slow or poison message blocks everything behind it in its partition ([07-retries-and-dead-letter-topics.md](07-retries-and-dead-letter-topics.md)). Per-message retry is what queues are for.

---

## Code examples

`CODE_EXAMPLES/12-kafka-vs-rabbitmq-vs-sqs/broker-comparison-demo/`. Run with `mvn -q test` (Docker must be running for the RabbitMQ half; Kafka is embedded). Read in this order:

1. `src/test/java/com/example/brokers/KafkaReplayAndScalingTest.java` — a group added later reads every past event; 4 consumers on 2 partitions, only 2 work.
2. `src/test/java/com/example/brokers/RabbitReplayAndScalingTest.java` — a queue bound after publishing gets nothing; 4 consumers on one queue all work.

---

## Try this yourself

1. In `KafkaReplayAndScalingTest`, change `@EmbeddedKafka(partitions = 2, ...)` to `partitions = 4`. What do you need to change in the scaling test's assertions, and why?
2. In `RabbitReplayAndScalingTest`, declare and bind the `analytics` queue *before* publishing. Now both queues get all 5 messages. How many copies of each message did RabbitMQ store, compared with Kafka's one copy read by two groups?

## Self-check

1. A new service needs every event published in the last month. Which broker makes that easy, and what exactly does the new consumer do?
2. A Kafka topic has 3 partitions and a group has 6 consumers. How many process messages, and what are your options to get more parallelism?
3. Give one workload where you'd pick RabbitMQ or SQS over Kafka, and name the broker feature that decides it.
