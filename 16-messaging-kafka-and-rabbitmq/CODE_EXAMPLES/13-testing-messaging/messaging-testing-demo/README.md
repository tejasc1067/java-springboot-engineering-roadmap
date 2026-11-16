# messaging-testing-demo

One `ShippingService` behind a Kafka listener and a RabbitMQ listener, tested in three layers, plus the broken test
patterns next to their fixes.

- `ShippingServiceTest` — the business logic with no broker and no Spring.
- `KafkaOrderFlowTest` — `@SpringBootTest` + `@EmbeddedKafka`:
  - waits for the listener's partition assignment before each test;
  - `validOrder_...` — Awaitility instead of sleeps, then reads the `shipments` topic;
  - `typoInTheTopic_...` — sends to the wrong topic and still passes: "nothing happened" proves nothing;
  - `invalidOrder_...` — first waits until the listener processed the message, then asserts with `during`.
- `BrokenTestPatternsTest` — plain Kafka clients:
  - a message sent before the consumer was assigned is skipped (`auto.offset.reset=latest`); waiting first fixes it;
  - a leftover message in a shared topic makes `getSingleRecord` throw; a topic per test fixes it.
- `RabbitOrderFlowTest` — Testcontainers RabbitMQ with `@ServiceConnection`.

The Kafka listener is in the `kafka` profile and the RabbitMQ one in the `rabbit` profile, so each test context
starts only the broker it uses.

Run (Docker running, for the RabbitMQ test):

```
mvn -q test
```

The suite takes about 15 seconds.
