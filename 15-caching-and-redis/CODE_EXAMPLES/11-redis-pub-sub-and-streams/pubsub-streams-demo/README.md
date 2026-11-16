# pubsub-streams-demo

Redis Pub/Sub (broadcast, fire-and-forget) next to Redis Streams (a stored log with consumer groups and acknowledgements).

- `PubSubTest` — a running subscriber receives a message; a message published while nobody is subscribed is dropped
  (`PUBLISH` returns 0) and never arrives later; two subscribers each get their own copy.
- `CacheInvalidationTest` — two instances with their own in-memory caches. An update on A leaves B stale until A
  broadcasts the eviction over Pub/Sub; then B reloads the new price.
- `StreamsTest` — messages added while no consumer runs are waiting for it; consumers in one group share the work;
  a consumer that crashes before `XACK` leaves the message pending, and another consumer claims it with `XCLAIM`;
  a restarted consumer re-reads its own pending messages; acknowledged messages stay in the stream until trimmed.

Run (Docker running):

```
mvn -q test
```

Pub/Sub delivery is asynchronous, so those tests wait with Awaitility (at most 5 seconds) instead of sleeping.
