# broker-comparison-demo

The same two scenarios on Kafka (embedded, no Docker) and RabbitMQ (Testcontainers, `rabbitmq:4.1-management-alpine`).

- `KafkaReplayAndScalingTest`
  - a consumer group added after billing has read 5 events still reads all 5;
  - 4 consumers in one group on a 2-partition topic: only 2 get partitions and do any work.
- `RabbitReplayAndScalingTest`
  - after billing's queue consumes 5 messages they're gone; a queue bound later gets nothing;
  - 4 consumers on one queue (prefetch 1) all receive work at the same time.

Run (Docker running):

```
mvn -q test
```

The suite takes about 10 seconds. The Kafka scaling test waits for the group to finish rebalancing before it checks
assignments: consumers join one at a time, and for a moment one consumer can own both partitions.
