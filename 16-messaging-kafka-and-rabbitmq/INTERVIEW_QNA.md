# Interview questions — Messaging with Kafka and RabbitMQ

Each answer is what an interviewer hopes to hear: the mechanism, the trade-off, and the production gotcha. The behaviour described comes from this module's tests (spring-kafka 3.3, kafka-clients 3.9, RabbitMQ 4.1).

---

## 1. Kafka vs a traditional queue: what is the fundamental difference, and what does it let each do?

A queue (RabbitMQ, SQS) hands each message to one consumer and deletes it once acknowledged. The broker tracks delivery per message, which is ideal for workers sharing tasks. But one queue can't give two services every message (the broker must copy into one queue per subscriber), and a service added later can't see history.

Kafka is a log. Records are appended to partitions and kept until retention expires (7 days by default), whether or not anyone read them. Each consumer group stores only an offset per partition: the next record to read. That gives **fan-out** (any number of groups read the same topic independently), **replay** (a new service reads from offset 0; a buggy consumer rewinds and reprocesses), and **work sharing** within a group (each partition goes to one consumer).

The costs: ordering only within a partition, so related records must share a key; a group's parallelism capped by the partition count; and no per-message "retry this one later" the way a queue's reject works, so retries and dead letters are built around the log (topic 07).

## 2. What do `acks=all`, `min.insync.replicas`, and the idempotent producer each guarantee? What happens to a producer when a replica is down?

With `acks=all`, the leader replies only after every in-sync replica (ISR) has the record. The ISR can shrink to just the leader, though, and then `acks=all` is no stronger than `acks=1`.

`min.insync.replicas` closes that gap: an `acks=all` write is refused unless at least that many replicas are in sync. The broker answers `NotEnoughReplicasException`, which is retriable, so the producer retries until `delivery.timeout.ms` (2 minutes by default) and then fails the send with a `TimeoutException` (topic 03). Kafka chooses being unavailable over accepting a write it might lose. The standard setup is replication factor 3 with `min.insync.replicas=2`: one broker can be down and writes still succeed.

The idempotent producer (default since Kafka 3.0) numbers batches per partition, and the broker drops a retried batch it already wrote. It prevents duplicates and reordering caused by the producer's own retries. It requires `acks=all`: setting `acks=1` silently disables it. It does not prevent duplicates from the application calling `send` twice, or from a consumer reprocessing after a crash. Those need idempotent consumers.

## 3. Events for one order are processed out of order. Why, and how do you fix it?

Kafka orders records only within a partition. Records with no key are spread by the sticky partitioner, which keeps a small burst on one partition (so tests pass) but switches partition every batch under load. The Created, Paid, and Shipped events for one order then land on different partitions, which different consumers read at different speeds (topic 05).

The fix is a key: send every event with the order id as the key. The default partitioner hashes the key (`murmur2(key) % partitions`), so all of one order's events go to one partition and one consumer, in order, while different orders still spread across partitions.

Three things still break it: a hot key (one huge customer overloads its partition), adding partitions (the modulo changes, so going from 3 to 6 partitions moved about half the keys), and non-blocking retries (`@RetryableTopic`), which let later records overtake a failed one. Within a consumer, don't hand records of one key to a thread pool either.

## 4. What does Kafka's exactly-once actually guarantee? Your consumer writes to Postgres. How do you avoid double-processing?

When the offset is committed decides the guarantee. Commit before processing is **at-most-once**: a crash in between loses the message. Process before committing is **at-least-once**: a crash in between processes it twice. Spring Kafka's default is at-least-once.

Kafka's exactly-once is three pieces together: the idempotent producer; transactions, which make a set of produced records and the consumed offsets commit or abort as one; and consumers with `isolation.level=read_committed` (the default is `read_uncommitted`, which also reads records from aborted transactions). In Spring Boot, setting `spring.kafka.producer.transaction-id-prefix` makes the listener container run each delivery in a transaction, so a Kafka-to-Kafka pipeline becomes exactly-once.

It covers nothing outside Kafka. In topic 06, a crash mid-transaction left one visible receipt but sent the email twice. For a Postgres write you build *effectively-once*: at-least-once delivery plus an idempotent consumer. The producer puts a unique event id in each event; the consumer inserts that id into a table with a primary key in the same database transaction as the business change, and treats a duplicate-key error as "already applied". A `SELECT` check first isn't enough: concurrent duplicates, typical after a rebalance, both pass it (topic 08). Where possible, make the change naturally idempotent instead: set-to-value or upsert, not add.

## 5. A Kafka consumer stopped making progress on one partition and the logs are huge. What could cause it, and how should error handling be configured?

Two typical causes. **A poison record with unlimited retries:** `DefaultErrorHandler` retries by seeking back to the failed offset, so nothing behind that record moves until it succeeds, and a poison record never does. **A deserialization failure:** a bare `JsonDeserializer` throws inside `poll()`, before Spring has a record to give the error handler, so the consumer fails on the same offset thousands of times a second (topic 07).

Note that Spring's default is the opposite extreme: `FixedBackOff(0, 9)`, 10 instant attempts, then the record is logged and skipped. That's silent loss.

A sound setup: `ErrorHandlingDeserializer` around every deserializer; a `DefaultErrorHandler` with a short bounded back-off, then `DeadLetterPublishingRecoverer`, which publishes to `<topic>-dlt` on the same partition number with `kafka_dlt-*` headers (original topic, partition, offset, exception); `addNotRetryableExceptions` for validation errors; an alert on the dead-letter topic and a replay procedure, with idempotent consumers because replay delivers records again. For long back-offs, `@RetryableTopic` moves failures to retry topics so the main partition keeps flowing, at the cost of ordering.

## 6. How do you change an event's structure when several services consume the topic?

Producers and consumers deploy independently, and the topic keeps old messages, so every change must be readable by consumers older than the new producer *and* by new consumers reading old messages.

Safe: adding optional fields with defaults, with consumers that ignore unknown fields (Spring Kafka's `JsonDeserializer` does by default; a hand-made `ObjectMapper` doesn't). Unsafe: renaming or removing a field someone reads, changing a type, or changing a field's meaning. With JSON a rename is silent: the old consumer reads a default value, such as an amount of 0 (topic 09).

**Backward compatible** means new consumers can read old data, so deploy consumers first. **Forward compatible** means old consumers can read new data, so deploy producers first. Teams that want this enforced use Avro or Protobuf with a schema registry, which rejects a schema version that breaks the configured rule. Incompatible changes get a new event type or topic, with both published during the migration. Also avoid consumers that deserialize by the producer's class name (Spring Kafka's `__TypeId__` header): moving that class breaks every consumer.

## 7. In Spring AMQP, what happens when a `@RabbitListener` throws, and how do you design failure handling?

In the default AUTO acknowledge mode, a normal return acks and an exception rejects *with requeue*. The broker redelivers immediately, so a message that can never succeed loops: hundreds of times a second on a classic queue, or until the quorum queue's delivery limit (20 redeliveries by default in RabbitMQ 4).

Give the queue an `x-dead-letter-exchange` bound to a DLQ, and reject without requeue: globally with `spring.rabbitmq.listener.simple.default-requeue-rejected=false`, or per failure with `AmqpRejectAndDontRequeueException`. The message moves to the DLQ with an `x-death` header and the rest of the queue keeps flowing. For temporary failures, enable `spring.rabbitmq.listener.simple.retry` with a few attempts and back-off; the retries run inside the listener, and when they run out the default `RejectAndDontRequeueRecoverer` dead-letters the message (topic 11). Keep those retries short, since they block the consumer.

On the producer side, use publisher confirms *and* returns: an ack means only that the broker took the message, and an unroutable one is acked and dropped (topic 10).

## 8. Kafka or RabbitMQ: how do you choose?

Start from the model. Kafka is a partitioned log: events stay for the retention period, any number of groups read them independently, new groups can start from the beginning, and ordering holds per key. A group's parallelism is capped by its partition count.

RabbitMQ routes each message through an exchange to the queues bound at that moment, gives it to one consumer, and deletes it on ack. That brings per-message ack and reject, dead-letter exchanges, priorities, delays, and flexible routing (direct, topic, fanout). Workers scale by adding consumers to a queue. But there's no history, and ordering is lost once several consumers share a queue (topic 12 shows both differences on both brokers).

So: business events consumed by several services, replay, very high volume, or per-key ordering at scale point to Kafka. Task queues with per-message retries, routing rules, priorities, or delays point to RabbitMQ, or SQS if you're on AWS and want nothing to operate. Redis Streams are enough for a modest work queue when Redis is already there (module 15 topic 11). Many systems run Kafka and RabbitMQ side by side.

## 9. How do you test a Kafka consumer without flaky tests?

Keep the listener thin and unit-test the service it calls with no broker. That covers most business rules, including idempotency. Use a few `@EmbeddedKafka` integration tests for the wiring (topic names, group ids, deserializers, error handlers), and Testcontainers where there's no embedded option (RabbitMQ).

In those tests: wait for partition assignment before sending (`ContainerTestUtils.waitForAssignment`), because `latest` skips earlier messages; replace `Thread.sleep` with Awaitility's `atMost`; isolate tests with unique topics or groups, since the broker outlives each test; and never assert only that something *didn't* happen: first await proof the listener processed the message, then assert the absence with `during` (topic 13). Cover message-format changes between teams with schema compatibility checks or contract tests (module 13 topic 21), not with integration tests.
