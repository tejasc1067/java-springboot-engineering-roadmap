# service-discovery-and-load-balancing demos

Two sub-demos — one automated, one bootable — because discovery (dynamic registration)
is a running-system thing while load balancing (spreading calls) is unit-testable.

## `loadbalancer-demo/` — the load-balancing half (automated)

Proves client-side round-robin load balancing with the real Spring Cloud LoadBalancer:
given a list of instances for `inventory-service`, repeated choices rotate across all of
them, and a logical service name resolves to a concrete `host:port`.

```bash
cd loadbalancer-demo
mvn -q test        # Tests run: 2, green
```

No server needed — the instance list is supplied directly (in production it comes from
discovery).

## `eureka-discovery/` — the discovery half (bootable)

A real Eureka registry plus two services that use it. `mvn test` proves all three
contexts build; to see registration and name-based calls, run them:

```bash
# terminal 1 — the registry (dashboard at http://localhost:8761)
cd eureka-discovery/eureka-server && mvn -q spring-boot:run

# terminal 2 — inventory-service registers itself as "inventory-service"
cd eureka-discovery/inventory-service && mvn -q spring-boot:run
#   optional 2nd instance on another port:
#   cd eureka-discovery/inventory-service && mvn -q spring-boot:run -Dspring-boot.run.arguments=--server.port=8082

# terminal 3 — order-service finds inventory BY NAME (no hardcoded URL)
cd eureka-discovery/order-service && mvn -q spring-boot:run
```

Then:

```bash
curl localhost:8080/api/check/SKU-BOOK
# -> {"sku":"SKU-BOOK","available":5,"servedByPort":8081}
```

Open http://localhost:8761 to watch `inventory-service` and `order-service` appear in the
registry. `order-service` never knows inventory's address — it asks the registry.

> `order-service` here resolves the name explicitly with `DiscoveryClient` (picks the
> first instance) to show the mechanism. Automatic round-robin across instances is what
> `loadbalancer-demo` demonstrates; in real code a `@LoadBalanced` client combines both.
