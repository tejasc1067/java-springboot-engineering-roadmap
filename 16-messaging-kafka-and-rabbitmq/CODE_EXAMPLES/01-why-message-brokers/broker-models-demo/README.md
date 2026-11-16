# broker-models-demo

The two broker models in plain Java, with no broker and no Spring, so the difference is visible before any
configuration is.

- `QueueModelTest` — the queue model (RabbitMQ): a consumed message is gone; competing consumers split the work;
  two services on one queue each get half; a service added later sees nothing.
- `LogModelTest` — the log model (Kafka): reading doesn't remove; offsets are positions; every consumer group gets
  every message; a group added later replays from offset 0; a group can rewind after a bug.

Run:

```
mvn -q test
```
