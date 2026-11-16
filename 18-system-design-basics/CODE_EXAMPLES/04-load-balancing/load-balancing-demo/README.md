# load-balancing-demo

A discrete-event simulation of a load balancer in front of four servers, in plain Java. No Spring, no Docker, no sleeps: simulated time, so every run gives the same numbers.

- `SlowServerTest`: one server 10× slower. Round-robin p99 201,700 ms; least connections 100 ms; power of two choices 268 ms; static weights work until the server degrades further.
- `StickySessionsTest`: after a scale-out the new instance gets 0 of the existing users' requests; killing one of 3 instances loses 300 of 900 sessions.
- `LongLivedConnectionsTest`: a layer 4 balancer leaves 2 new instances with 0 requests while clients keep their connections; reconnecting or layer 7 spreads 22,500 to each.

Run:

```
mvn -q test
```

The tests print the per-server counts and latency percentiles they assert on.
