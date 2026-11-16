# 16 — Messaging: Kafka and RabbitMQ

Module 13 topic 07 made the case for asynchronous messaging: instead of calling five services and waiting, a service publishes "an order was placed" and moves on, and whoever cares reacts in their own time. That topic treated the broker as a box. This module opens the box. You'll use the two brokers most Java backends meet, **Apache Kafka** and **RabbitMQ**, from Spring Boot, and see exactly where messages get lost, duplicated, reordered, or stuck.

Messaging bugs are rarely loud. A producer that never checks its send result loses messages without an error. A consumer that commits too early loses them on a crash; one that commits too late processes them twice. A poison message can block a whole partition, or bounce between broker and consumer forever. Each topic shows the broken version as a test that demonstrates it, then the fix.

## Who this is for

- Anyone who has finished module 13 and has only ever seen messaging as a diagram.
- Developers who have added `@KafkaListener` or `@RabbitListener` and aren't sure what happens when the listener throws.
- Anyone asked "Kafka or RabbitMQ?" who wants to answer with reasons instead of fashion.

## What you'll be able to do at the end

- [ ] Explain the difference between a queue and a log, and why it decides what a broker can do (replay, competing consumers).
- [ ] Explain Kafka topics, partitions, offsets, replication, and consumer groups, and read a topic's layout.
- [ ] Send messages with `KafkaTemplate` without losing them: keys, `acks`, the idempotent producer, and checking the send result.
- [ ] Consume with `@KafkaListener`, size consumer concurrency to partitions, and choose when offsets are committed.
- [ ] Keep related messages in order with partition keys, and explain what breaks the ordering.
- [ ] Explain at-most-once, at-least-once, and exactly-once delivery, and what Kafka transactions do and don't cover.
- [ ] Retry failed messages and route the ones that can't succeed to a dead-letter topic, without blocking the partition.
- [ ] Make a consumer safe to run twice on the same message.
- [ ] Change a message's format without breaking consumers that deploy on a different day.
- [ ] Explain RabbitMQ's exchanges, bindings, and queues, and route messages with direct, topic, and fanout exchanges.
- [ ] Use Spring AMQP with JSON messages, acknowledgements, retries, and a dead-letter queue.
- [ ] Choose between Kafka, RabbitMQ, SQS, and Redis Streams for a given system.
- [ ] Test producers and consumers reliably, without `Thread.sleep`.

If you can do all thirteen, the module worked.

## Prerequisites

- **Module 13 topics 07 and 19.** This module assumes the reasons for asynchronous messaging (13-07) and the idempotency and outbox patterns (13-19). Topic 08 here builds directly on 13-19.
- **Modules 08 and 10.** Spring Boot configuration and a database for the consumer-side examples.
- **Module 14 topic 11 (Testcontainers)** for the RabbitMQ topics.
- **Docker installed and running** for the RabbitMQ topics (10, 11, and parts of 12–13). The image (`rabbitmq:4.1-management-alpine`) is about 260 MB. The Kafka topics need no Docker: they use Spring Kafka's embedded broker, which runs inside the test JVM.

## How this module is organized

Topic 01 is **the model**: queues and logs, the two shapes every broker takes.

Topics 02–04 are **Kafka basics**: how Kafka stores messages (02), producing them (03), and consuming them in groups (04).

Topics 05–09 are **the hard parts of messaging**, shown with Kafka but true of every broker: ordering (05), delivery guarantees (06), retries and dead letters (07), idempotent consumers (08), and message formats that change over time (09).

Topics 10–11 are **RabbitMQ**: its routing model (10) and Spring AMQP (11).

Topics 12–13 are **choosing and testing**: Kafka vs RabbitMQ vs SQS vs Redis Streams (12), and testing messaging code without flaky waits (13).

```text
01-why-message-brokers                <- queue vs log; what a broker adds over a direct call
02-kafka-core-concepts                <- topics, partitions, offsets, replication, retention, consumer groups
03-spring-kafka-producer              <- KafkaTemplate, keys, acks, idempotent producer; the ignored send result
04-spring-kafka-consumer              <- @KafkaListener, groups, rebalancing, offset commits, auto.offset.reset
05-ordering-and-partition-keys        <- out of order without a key; hot partitions; adding partitions moves keys
06-delivery-semantics                 <- at-most-once, at-least-once, exactly-once and its limits
07-retries-and-dead-letter-topics     <- DefaultErrorHandler, poison messages, DLT, non-blocking retries
08-idempotent-consumers               <- duplicates are normal; dedupe with a unique constraint
09-schemas-and-serialization          <- type headers, field renames, schema evolution and compatibility
10-rabbitmq-core-concepts             <- exchanges, bindings, routing keys; silently dropped messages; prefetch
11-spring-amqp                        <- @RabbitListener, JSON, acks, requeue loops, dead-letter exchanges
12-kafka-vs-rabbitmq-vs-sqs           <- choosing, with replay and scaling shown on both brokers
13-testing-messaging                  <- embedded Kafka, Testcontainers RabbitMQ, flaky test patterns
```

```text
COMMON_MISTAKES.md       <- real messaging bugs with code
INTERVIEW_QNA.md         <- interview-grade questions on brokers, ordering, delivery guarantees, retries, schemas
```

## The toolkit

| Tool / library | What it does | First used in |
|---|---|---|
| Spring for Apache Kafka (`spring-kafka`, Boot-managed) | `KafkaTemplate`, `@KafkaListener`, error handlers, transactions | topic 02 |
| `spring-kafka-test` (`@EmbeddedKafka`) | a real Kafka broker inside the test JVM, no Docker | topic 02 |
| RabbitMQ 4.1 (`rabbitmq:4.1-management-alpine`) | the AMQP broker | topic 10 |
| Spring AMQP (`spring-boot-starter-amqp`) | `RabbitTemplate`, `@RabbitListener`, `RabbitAdmin` | topic 10 |
| Testcontainers RabbitMQ + `@ServiceConnection` | starts RabbitMQ from JUnit and points Spring at it | topic 10 |
| Apache Avro (`org.apache.avro:avro`) | schemas and compatibility checks | topic 09 |
| Awaitility (Boot-managed) | waits for asynchronous results with a timeout instead of `Thread.sleep` | topic 04 |

The demos stay on Spring Boot 3.5.14 and Java 21, like modules 08–15.

## How to run the examples

Each topic has its own Maven project under `CODE_EXAMPLES/NN-topic-name/`. The tests are the lesson:

```bash
cd 16-messaging-kafka-and-rabbitmq/CODE_EXAMPLES/05-ordering-and-partition-keys/<demo-dir>
mvn -q test
```

Kafka tests start an embedded broker; expect a few seconds of startup and a lot of Kafka log output. RabbitMQ tests start a container and remove it when they finish. To look around RabbitMQ yourself, run `docker run --rm -p 5672:5672 -p 15672:15672 rabbitmq:4.1-management-alpine` and open `http://localhost:15672` (user and password `guest`).

## Checkpoint

You're done with this module when you can:

1. Explain why a second consumer group on a Kafka topic can read every past message, while a new RabbitMQ queue gets only messages published after it was bound.
2. Draw a topic with 3 partitions and a consumer group of 4 consumers, and say which consumer reads what.
3. Show the line of producer code that loses messages without any error, and fix it.
4. Explain why "commit the offset, then process" loses messages on a crash and "process, then commit" duplicates them.
5. Explain why events for one order can arrive out of order, and the one-line fix.
6. Say what Kafka's exactly-once guarantees, and why it doesn't stop a consumer from sending the same email twice.
7. Explain how a poison message can block a partition, and how a dead-letter topic fixes it.
8. Write a consumer that adds to a balance and stays correct when the same message arrives twice.
9. Rename a field in an event and say which consumers break, and which schema change would have been safe.
10. Route `order.created.eu` to one queue and every `order.*.*` message to another with a RabbitMQ topic exchange, and say what happens to a message that matches no binding.
11. Explain why a RabbitMQ listener that throws gets the same message redelivered in a tight loop (forever on a classic queue, 20 redeliveries on a quorum queue), and the configuration that dead-letters it instead.
12. Pick a broker for three different systems and defend each choice.

If those twelve feel comfortable, move to module 17, where you'll see what all of these services and messages are doing in production.

## A note on what's *not* here

- **No cloud messaging services.** SQS appears only as a concept in topic 12 for comparison; no AWS SDK, SNS, Google Pub/Sub, or Azure Service Bus code.
- **No Kafka operations.** Cluster sizing, broker configuration, rack awareness, tiered storage, and MirrorMaker are platform work.
- **No Kafka Streams or ksqlDB.** Stream processing is a separate subject from producing and consuming.
- **No Kafka Connect or CDC tooling.** Debezium is mentioned where it fits (the outbox in module 13 topic 19), not built.
- **No running schema registry.** Topic 09 checks Avro compatibility with the Avro library itself, which is the same rule a registry enforces.
