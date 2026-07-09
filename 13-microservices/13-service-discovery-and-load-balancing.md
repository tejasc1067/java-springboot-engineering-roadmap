# Service Discovery and Load Balancing

The scaffold's order-service calls inventory-service at a hardcoded `http://localhost:8081` (topic 05). That works on your laptop and nowhere else. In a real deployment, services don't have fixed addresses: instances are started and stopped by an orchestrator, autoscaling adds and removes replicas, a crash-and-restart lands on a new IP, a deploy replaces every instance. The address you hardcoded is stale within the hour (fallacy 5, "topology doesn't change"). **Service discovery** solves the "where is it?" problem — services register themselves and look each other up by *name*. **Load balancing** solves the "which instance?" problem — when a name resolves to several instances, spread calls across them.

The demos split cleanly: a bootable Eureka system for discovery, and an automated test for load balancing.

---

## The problem this solves

You have three instances of inventory-service running for capacity, and they move around: instance on `10.0.4.17` dies, autoscaling starts a replacement on `10.0.9.3`, a deploy rolls all three onto new hosts. How does order-service know where to send its call?

- **Hardcoding `http://10.0.4.17:8081`** breaks the moment that instance moves, and only ever uses one of the three.
- **A config file of addresses** is stale the moment topology changes and needs a redeploy to update.
- **A load-balancer box in front of inventory-service** (a dedicated proxy) works but adds a network hop and a thing to operate, and still needs to know the instances.

Discovery replaces all of that with a **registry**: every instance registers itself under a logical name when it starts and de-registers (or times out) when it stops. Callers ask the registry "where is inventory-service?" and get the current live list. The address indirection means instances can come and go freely and callers never change.

## How it works: register, then look up

The demo's `eureka-discovery` system has three parts:

1. **A registry** — Eureka server (`@EnableEurekaServer`). Services register here and query here. Its dashboard at `:8761` lists everything currently registered.
2. **A service that registers** — inventory-service, with the `eureka-client` starter and `eureka.client.service-url` pointing at the registry. On startup it registers itself under `spring.application.name` (`inventory-service`) and sends heartbeats; if the heartbeats stop, the registry evicts it.
3. **A service that discovers** — order-service asks the registry where inventory-service is:

```java
List<ServiceInstance> instances = discoveryClient.getInstances("inventory-service");
ServiceInstance instance = instances.get(0);      // a live host:port, resolved by NAME
RestClient.create().get().uri(instance.getUri() + "/api/inventory/{sku}", sku)...
```

Run it (README) and open `:8761` to watch inventory-service and order-service appear. order-service never knows inventory's address — it looks it up. Kill inventory and restart it on a different port: order-service keeps working, because it re-resolves from the registry.

This is **client-side discovery** — the caller queries the registry and picks an instance itself. (The alternative, *server-side* discovery, puts a load balancer/router in the path that does the lookup; that's closer to what an API gateway (topic 14) or a Kubernetes Service does. Spring Cloud's default is client-side.)

## Load balancing: which of the instances?

When `getInstances("inventory-service")` returns *three* instances, you don't want to hammer the first one — you want to spread calls. That's client-side **load balancing**, and Spring Cloud LoadBalancer does it. The demo proves the core behavior with `RoundRobinLoadBalancer`:

```java
// two live instances of inventory-service (what the registry would report)
RoundRobinLoadBalancer lb = new RoundRobinLoadBalancer(supplier, "inventory-service");
// repeated choices rotate across BOTH instances, not pinned to one
```

The test asserts that over several `choose()` calls, *both* instance ports appear — the load is spread. In real code you don't call the load balancer by hand; you annotate a client `@LoadBalanced` and call `http://inventory-service/api/inventory/{sku}` — the `inventory-service` host is a *logical name* that the load balancer resolves (via discovery) to a live instance, balancing automatically. Discovery supplies the list; the load balancer picks from it. The two halves combine into "call by name, hit a healthy instance."

Round-robin is the default strategy; others exist (random, least-connections, weighted). Round-robin is fine for most cases where instances are roughly equal.

## Discovery and load balancing vs. the alternatives

- **DNS** can do a form of this (a name resolving to multiple A records), but DNS caching and slow TTLs make it a poor fit for fast-changing instance lists — a dead instance stays in DNS long after it's gone. Discovery registries evict dead instances quickly via heartbeats.
- **Kubernetes** provides discovery and load balancing natively (a `Service` is a stable name that load-balances across healthy pods), so on k8s you often *don't* run Eureka — the platform does it. Eureka (and Spring Cloud LoadBalancer) is the application-level equivalent for when you're not on such a platform, and it's the one worth understanding because it makes the mechanism explicit. (Kubernetes itself is module 14 / out of scope here.)

## When to use it / when not to

- **Use a registry** when you run multiple, changing instances of services and need them to find each other without hardcoded addresses — the normal case for microservices not on a discovery-providing platform.
- **You may not need Eureka** if you're on Kubernetes (its `Service` does discovery + LB) or behind a platform that provides it. Don't run a second discovery system on top of one you already have.
- **Overkill for a fixed, tiny topology** — two services that never scale and never move can live with configuration. Discovery earns its keep when instances are dynamic.

## Common pitfalls

- **Hardcoding instance addresses** (the scaffold's `http://localhost:8081`). Breaks on any topology change and uses only one instance. Resolve by name.
- **No health checks / stale registrations.** If instances don't heartbeat (or the registry doesn't evict), callers get routed to dead instances. Ensure heartbeats and eviction are configured; combine with a circuit breaker (topic 11) so a briefly-stale entry doesn't cascade.
- **A single registry instance = a single point of failure.** If the registry is the only way to find anything and it's down, nothing can discover anything. Run the registry clustered (Eureka peers) in production.
- **Assuming discovery load-balances by itself.** `getInstances(...).get(0)` always picks the first — that's discovery *without* load balancing (the demo's order-service does this to show the mechanism). Use a `@LoadBalanced` client or the LoadBalancer to actually spread calls.
- **Long client-side caches of the instance list.** Callers cache the registry's list for performance, but too long a cache means slow reaction to instances dying. Balance freshness against registry load.

---

## Code examples

Under `13-service-discovery-and-load-balancing/` — two sub-demos:

**`loadbalancer-demo/` (automated `mvn test`):**
1. `loadbalancer-demo/src/test/java/com/example/lb/ClientSideLoadBalancingTest.java` — `RoundRobinLoadBalancer` spreads calls across two instances; a service name resolves to a concrete `host:port`.

**`eureka-discovery/` (bootable — see the folder README):**
2. `eureka-discovery/eureka-server` — the registry (`@EnableEurekaServer`).
3. `eureka-discovery/inventory-service` — registers itself by name.
4. `eureka-discovery/order-service` — finds inventory-service via `DiscoveryClient` and calls it, with no hardcoded address.

`mvn test` at `eureka-discovery/` proves all three contexts build; run them (README) to watch registration in the `:8761` dashboard.

## Try this yourself

1. Run the Eureka system, then start a *second* inventory-service instance on port 8082 (`-Dspring-boot.run.arguments=--server.port=8082`). Watch both register in the dashboard. (order-service's explicit `getInstances(0)` still picks the first — that's the difference between discovery and load balancing.)
2. In `loadbalancer-demo`, add a third instance to the list and confirm `choose()` now spreads across all three. Then remove one mid-list and confirm it's simply not chosen — that's how a dead instance drops out once discovery stops reporting it.
3. Take the scaffold's `InventoryClientConfig` and describe (no code) how you'd change `inventory.base-url` from `http://localhost:8081` to a discovered `http://inventory-service` — what dependency and annotation you'd add. That's the concrete upgrade this topic enables.

## Self-check

1. Why does hardcoding a downstream's `host:port` fail in a real deployment, and what does a discovery registry replace it with?
2. What's the difference between *discovery* (finding instances) and *load balancing* (choosing among them)? Which does `getInstances(...).get(0)` do, and which does it not?
3. When might you *not* run a Eureka-style registry at all, and why?
