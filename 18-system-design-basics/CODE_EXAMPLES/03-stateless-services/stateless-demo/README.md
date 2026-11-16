# stateless-demo

Two instances behind a load balancer, and the state that breaks when it lives inside an instance. Plain Java, no Docker.

- `InMemorySessionTest` — a cart kept in each instance's memory splits across instances under round-robin; a shared
  store fixes it and makes instances replaceable.
- `StickySessionTest` — sticky routing hides the problem until an instance goes away, then 500 of 1,000 carts are lost.
- `LocalStateTest` — an upload saved to one instance's disk is missing on the other, and a 3-attempt lockout counted
  per instance allows 9 guesses with 3 instances.

The shared `Map` and shared directory stand in for Redis and object storage.

```
mvn -q test
```
