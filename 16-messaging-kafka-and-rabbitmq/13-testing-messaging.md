# Testing Messaging

Messaging code is harder to test than a REST endpoint. Calls are asynchronous, so a test can't just check a return value, and a broker is shared state that outlives a single test. The result is a familiar kind of suite: slow because of `Thread.sleep`, flaky on CI, and sometimes green because the code under test never ran at all. This topic shows how to layer messaging tests so most of them need no broker, how to write the rest against a real (embedded or containerized) broker without sleeps, and the four broken test patterns you'll meet in almost every codebase.

---

## The problem this solves

A team's shipping consumer has these tests:

```java
@Test
void ordersAreShipped() throws Exception {
    kafka.send("orders", "order-1:2500");
    Thread.sleep(5000);                                    // "give the listener time"
    assertThat(shipping.shipmentFor("order-1")).isPresent();
}

@Test
void invalidOrdersAreNotShipped() {
    kafka.send("orders", "order-2:-5");
    assertThat(shipping.shipmentFor("order-2")).isEmpty();   // checked instantly
}
```

The first takes 5 seconds when the listener needed 50 milliseconds, and still fails on a slow CI agent that needed 6. The second passes whatever the code does, because it asserts before the listener could have done anything. Neither tells you much.

## How it works: three layers

```text
          few     +-------------------------------------+
                  | real broker in Docker (Testcontainers) |  RabbitOrderFlowTest
                  +-------------------------------------+
                  | embedded broker (@EmbeddedKafka)     |  KafkaOrderFlowTest
                  +-------------------------------------+
          most    | plain unit tests of the handler      |  ShippingServiceTest
                  +-------------------------------------+
```

### Layer 1: keep the listener thin, test the logic without a broker

The listener does three things: receive, call a service, publish the result. Everything that can be wrong about the *business* lives in the service:

```java
@KafkaListener(id = "shipping", topics = "orders", groupId = "shipping")
public void onOrder(String orderEvent) {
    received.incrementAndGet();
    shipping.handle(orderEvent)
            .ifPresent(shipmentId -> kafka.send("shipments", orderEvent.split(":")[0], shipmentId));
}
```

`ShippingService` knows nothing about Kafka or RabbitMQ, so `ShippingServiceTest` is plain JUnit: valid orders ship, invalid ones (`-5`, `abc`, `garbage`) don't, and the same event twice ships once. It runs in milliseconds. Most of your messaging tests should be like this. The demo uses the same service behind both a Kafka listener and a RabbitMQ listener, which shows how little the broker has to do with the logic.

### Layer 2: the real flow through an embedded Kafka

`@EmbeddedKafka` (from `spring-kafka-test`) starts a Kafka broker inside the test JVM. No Docker, a couple of seconds to start:

```java
@SpringBootTest
@ActiveProfiles("kafka")
@EmbeddedKafka(partitions = 1, topics = {"orders", "shipments"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class KafkaOrderFlowTest {
```

`bootstrapServersProperty` points Spring Boot's Kafka settings at the embedded broker. Then `validOrder_isShipped_andAShipmentEventIsPublished` tests the whole path: send to `orders`, wait for the side effect, read the output topic.

```java
String orderId = "order-" + UUID.randomUUID();   // unique per test: other tests share these topics

kafka.send("orders", orderId + ":2500");

await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(shipping.shipmentFor(orderId)).isPresent());
assertThat(shipmentEventsFor(orderId)).containsExactly("ship-" + orderId);
```

**Awaitility** (`await()`, included in `spring-boot-starter-test`) polls the condition every 100 ms and returns the moment it's true, failing only after `atMost`. A test that needs 50 ms takes 50 ms, and a slow CI machine gets up to 10 seconds. That replaces every `Thread.sleep`.

`shipmentEventsFor` reads the output topic with a fresh consumer from `KafkaTestUtils`. One trap: `KafkaTestUtils.consumerProps` defaults to an `Integer` key deserializer, and the first version of this test failed with `RecordDeserializationException: Error deserializing KEY`. Pass the deserializers that match your topic.

### Layer 3: a real broker in Docker

There's no embedded RabbitMQ, so `RabbitOrderFlowTest` uses Testcontainers, exactly like Postgres in module 14 topic 11 and Redis in module 15:

```java
@ServiceConnection
static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4.1-management-alpine");

static {
    RABBIT.start();
}
```

`@ServiceConnection` sets Spring Boot's RabbitMQ host, port, and credentials from the container. The tests then look just like the Kafka ones: `convertAndSend`, then `await()`.

The same option exists for Kafka: `org.testcontainers:kafka`'s `KafkaContainer` runs the `apache/kafka` image, and `@ServiceConnection` supports it. Use it when you need behaviour the embedded broker doesn't reproduce, such as a specific broker version or configuration. For most application tests, `@EmbeddedKafka` is faster and needs no Docker.

## The four broken test patterns

### 1. `Thread.sleep` instead of waiting for a condition

Sleeping for a fixed time is always wrong in one direction: too long (a slow suite) or too short (a flaky one), and usually both on different machines. Replace it with `await().atMost(...).until(...)`, which waits only as long as needed.

### 2. Sending before the consumer owns its partition

Kafka's default `auto.offset.reset` is `latest`: a consumer group with no committed offset starts at the *end* of the partition at the moment it's assigned. Anything sent before that is skipped. `BrokenTestPatternsTest.messageSentBeforeTheConsumerIsAssigned_isSkipped_withTheDefaultLatestReset` forces that order:

```java
send(broker, topic, "order-1:2500");   // the test sends right away...
container.start();                     // ...and the consumer starts at the END of the partition
ContainerTestUtils.waitForAssignment(container, 1);
await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(2)).until(received::isEmpty);   // never arrives
```

In a `@SpringBootTest` this is a race between the listener's startup and the test's first `send`. On a fast laptop the listener usually wins; here `KafkaOrderFlowTest` passed 3 runs out of 3 without waiting. On a loaded CI agent it may lose, and the test fails "randomly". The fix is to wait for assignment before sending (`waitingForAssignmentFirst_thenSending_isReceived`), which `KafkaOrderFlowTest` does for its `@KafkaListener` before every test:

```java
@BeforeEach
void waitUntilTheListenerOwnsItsPartition() {
    ContainerTestUtils.waitForAssignment(registry.getListenerContainer("shipping"), 1);
}
```

The second argument is the number of partitions the listener should own. Setting `spring.kafka.consumer.auto-offset-reset=earliest` for tests also avoids it.

### 3. Tests sharing a topic or group

The broker outlives each test. A message one test left behind is still there for the next. `sharedTopic_aLeftoverFromAnotherTest_breaksGetSingleRecord`:

```java
send(broker, sharedTopic, "left behind by an earlier test");
send(broker, sharedTopic, "order-1:2500");   // this test's own message

KafkaTestUtils.getSingleRecord(consumer, sharedTopic, Duration.ofSeconds(5));
// IllegalStateException: More than one record for topic found
```

Worse versions of this don't throw: a test asserts on the first record it sees, which belongs to another test, and passes or fails depending on test order. Fixes: a unique topic per test (`topicPerTest_onlyThisTestsMessage`), a unique consumer group, or unique ids in the payload filtered on read (how `KafkaOrderFlowTest` reads `shipments`).

### 4. A test that passes because the listener never ran

`typoInTheTopic_theTestStillPasses_becauseItOnlyCheckedThatNothingHappened` sends to `order` instead of `orders`. Nothing listens there:

```java
kafka.send("order", "order-x:-5");
assertThat(shipping.shipmentFor("order-x")).isEmpty();   // green, and proves nothing
assertThat(listener.received()).isEqualTo(before);      // the listener never even saw it
```

Any test that asserts "X did *not* happen" passes when nothing happened at all. The listener could be misconfigured, on the wrong topic, or not started. The fix is in two steps (`invalidOrder_isNotShipped_provedByWaitingUntilTheListenerProcessedIt`): first prove something happened, then check the thing that mustn't happen, over a short window:

```java
await().atMost(Duration.ofSeconds(10)).until(() -> listener.received() > before);   // it ran
await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(2))
        .until(() -> shipping.shipmentFor("order-y").isEmpty());                    // and didn't ship
```

`during` makes Awaitility require the condition to stay true for the whole 500 ms, so a shipment that arrives a moment late still fails the test. The `received()` counter on the listener exists only so tests can observe it; a metric (module 17) or a recorded output serves the same purpose in larger apps.

## Contracts between producer and consumer

Everything above tests one side. The other risk is that the producer changes the message shape and the consumer breaks in production, while both teams' tests stay green. That's what consumer-driven contract tests catch for HTTP and messages ([module 13 topic 21](../13-microservices/21-contract-testing.md)), and what schema compatibility checks catch for serialized events ([09-schemas-and-serialization.md](09-schemas-and-serialization.md)).

## When to use which layer

- **Unit tests of the service**: every business rule, every validation, idempotency. Most of your tests.
- **Embedded Kafka**: listener wiring (topic names, group ids, deserializers, error handlers, retries and dead-letter topics from [07-retries-and-dead-letter-topics.md](07-retries-and-dead-letter-topics.md)). A handful per listener.
- **Testcontainers**: brokers with no embedded option (RabbitMQ), or when the real broker's behaviour is the thing under test. Docker is needed on every machine and CI agent.

## Common pitfalls

- **`Thread.sleep` in tests.** Slow and flaky. Use Awaitility with `atMost`.
- **Sending before assignment.** With `latest`, the message is skipped. Wait with `ContainerTestUtils.waitForAssignment`, or use `earliest` in tests.
- **Shared topics and groups across tests.** Leftovers leak between tests. Use unique topics, groups, or ids.
- **Only asserting that nothing happened.** Passes when the listener never ran. Prove it processed the message first, then assert with `during`.
- **Testing the business logic only through the broker.** Slow and hard to debug. Move the logic into a plain service and unit-test it.

---

## Code examples

`CODE_EXAMPLES/13-testing-messaging/messaging-testing-demo/`. Run with `mvn -q test` (Docker must be running for `RabbitOrderFlowTest`). Read in this order:

1. `src/main/java/com/example/msgtest/ShippingService.java` — the business logic, no broker.
2. `src/test/java/com/example/msgtest/ShippingServiceTest.java` — layer 1: plain unit tests.
3. `src/main/java/com/example/msgtest/KafkaOrderListener.java` — a thin `@KafkaListener` with a `received()` counter.
4. `src/test/java/com/example/msgtest/KafkaOrderFlowTest.java` — layer 2: `@EmbeddedKafka`, waiting for assignment, Awaitility, reading the output topic, the typo test that proves nothing, and the fix.
5. `src/test/java/com/example/msgtest/BrokenTestPatternsTest.java` — sending before assignment, and leftovers in a shared topic, each next to its fix.
6. `src/main/java/com/example/msgtest/RabbitOrderListener.java` — the same service behind a `@RabbitListener`.
7. `src/test/java/com/example/msgtest/RabbitOrderFlowTest.java` — layer 3: Testcontainers RabbitMQ with `@ServiceConnection`.

---

## Try this yourself

1. In `KafkaOrderFlowTest`, replace the first `await()` in `validOrder_isShipped_andAShipmentEventIsPublished` with `Thread.sleep(50)`. Run it 10 times. Then try `Thread.sleep(5000)` and compare how long the suite takes.
2. Change the `@KafkaListener` topic to `orderz`. Which tests fail, and which still pass? What does that tell you about the passing ones?

## Self-check

1. Why does a test that sends a Kafka message immediately after the Spring context starts sometimes fail on CI, and what are two ways to fix it?
2. A test sends an invalid order and asserts no shipment was created. Explain how it can pass when the listener is completely broken, and rewrite it so it can't.
3. Which tests in a messaging service should need no broker at all, and what has to be true about the listener for that to be possible?
