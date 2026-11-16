# rabbit-basics-demo

RabbitMQ's routing model against a real broker (`rabbitmq:4.1-management-alpine`, started by Testcontainers).

- `ExchangeTypesTest` — the default exchange, direct, topic (`*` and `#`), and fanout exchanges; Spring's durable and
  persistent defaults.
- `UnroutableTest` — a message no binding matches is dropped while the publish succeeds; the mandatory flag returns
  it; an alternate exchange catches it.
- `PrefetchTest` — two competing consumers, one stuck: unlimited prefetch strands half the messages on it, prefetch 1
  strands one.

Run (Docker running):

```
mvn -q test
```

First run pulls the RabbitMQ image (about 260 MB on disk). After that the suite takes about 10 seconds.
