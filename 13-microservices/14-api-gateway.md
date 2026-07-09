# API Gateway

Once you have several services (each discoverable, topic 13), a new question appears: what do *clients* — a browser, a mobile app, a partner — talk to? You don't want them calling a dozen internal services directly: they'd need to know every service's address, every service would need to handle auth and CORS and rate limiting itself, and your internal topology would leak to the outside world. An **API gateway** is a single entry point that sits in front of your services: clients call the gateway, and it routes each request to the right internal service, applying cross-cutting concerns once, at the edge.

The demo runs a real (servlet-stack) Spring Cloud Gateway that routes, rewrites a path, and adds a header — the shape of every gateway concern.

---

## The problem this solves

Without a gateway, a client that needs orders, inventory, and payments has to:

- Know the address of *each* service (and re-learn it when they move — topic 13).
- Authenticate against each one, handle CORS for each one, get rate-limited by each one.
- Deal with your internal decomposition — if you split payments into two services, every client changes.

And each *service* has to independently implement auth, CORS, rate limiting, request logging, TLS termination — the same cross-cutting code, duplicated N times, inconsistently.

A gateway collapses all of that to one front door: clients hit `https://api.example.com/...`, the gateway authenticates once, applies policy once, and routes to whichever internal service handles that path. Clients see a stable, unified API; services stay internal and focus on their own logic.

## How it works: route + filter at the edge

A gateway is fundamentally **predicate → route → filters**. From the demo:

```java
route("inventory-route")
    .route(path("/inventory/**"), http(inventoryUri))                        // predicate -> destination
    .before(rewritePath("/inventory/(?<segment>.*)", "/api/inventory/${segment}")) // edge filter
    .before(addRequestHeader("X-Gateway", "spring-cloud-gateway-mvc"))       // edge filter
    .build();
```

- **Predicate** — which requests this route matches (`path("/inventory/**")`; could also be by host, header, method, etc.).
- **Destination** — where matched requests go (`http(inventoryUri)` — an internal service; in production, often a discovered service name via topic 13's load balancer rather than a fixed URL).
- **Filters** — what happens to the request/response on the way through. `before` filters run on the way in (rewrite the path, add/strip headers, check auth), `after` filters on the way back (add response headers, collect metrics).

The demo's test proves all three: a client GET to the gateway's `/inventory/SKU-BOOK` is routed to the downstream, arrives there rewritten as `/api/inventory/SKU-BOOK`, and carries the `X-Gateway` header the gateway added. The client never knew inventory-service's address.

A note on the flavour: Spring Cloud Gateway comes in a reactive (WebFlux) and a **servlet (MVC)** build. This roadmap is servlet-stack throughout, so the demo uses `spring-cloud-starter-gateway-server-webmvc` — same routing/filter model, no reactive dependency.

## What belongs at the gateway (and what doesn't)

The gateway is the natural home for **edge cross-cutting concerns** — things every external request needs, regardless of which service handles it:

- **Authentication / token validation** — verify the caller's JWT once at the edge (topic 16 then *propagates* the verified identity inward). Reject anonymous traffic before it reaches any service.
- **Rate limiting / throttling** (topic 12, applied at the edge) — protect all services from abusive clients in one place.
- **CORS** — configure browser cross-origin rules centrally.
- **TLS termination**, request/response logging, adding a correlation id (topic 17), routing and path rewriting, request-size limits.

What does **not** belong at the gateway:
- **Business logic.** The gateway routes and applies policy; it must not know that an order needs stock checked. A gateway that accumulates business rules becomes a new monolith — and a single point of failure with logic in it. Keep it thin.
- **Response aggregation as a rule.** Composing several services into one response (topic 08) is sometimes done at the gateway, but heavy aggregation usually belongs in a dedicated BFF or composition service, not the routing layer.

## Gateway vs. BFF vs. load balancer

Three things that all "sit in front" but do different jobs:

- **Load balancer** (topic 13) — picks *which instance* of one service to call. Operates below routing; a gateway often *uses* a load balancer to reach its chosen service.
- **API gateway** — routes *which service* a request goes to, and applies edge policy. One gateway for the whole system (or one per broad client class).
- **BFF (Backend for Frontend)** — a gateway-like layer *tailored to one specific client* (e.g. a mobile BFF that shapes and aggregates responses for the mobile app). You might have several BFFs behind one gateway, or BFFs instead of a general gateway. It's a gateway with a specific audience and often some composition (topic 08).

## When to use it / when not to

- **Use a gateway** as soon as external clients need to reach more than one or two services, or you want auth/CORS/rate-limiting/logging enforced consistently at the edge. It's standard for any non-trivial microservice system.
- **You may not need to run your own** if your platform provides one — a Kubernetes Ingress, a cloud API Gateway (AWS API Gateway, GCP API Gateway), or a service mesh's edge often fill this role. Spring Cloud Gateway is the app-level option and the one that makes the mechanism explicit.
- **Don't route *internal* service-to-service calls through the gateway** by default. order-service calling inventory-service should go direct (topics 05/13), not out through the gateway and back — that adds a hop and a bottleneck. The gateway is for *edge* (north-south) traffic, not internal (east-west) traffic.
- **Keep it thin.** The moment business logic or heavy state lives in the gateway, you've built a distributed monolith's worst node.

## Common pitfalls

- **Business logic in the gateway.** It becomes a monolith and a fat single point of failure. Routing and policy only.
- **The gateway as an unguarded single point of failure.** *Everything* enters through it, so if it's down, everything is unreachable. Run it highly available (multiple instances behind a load balancer) and keep it simple so it rarely fails.
- **Routing internal traffic through it.** east-west calls should be direct; only north-south (client→system) goes through the gateway.
- **Duplicating auth in the gateway *and* every service naively.** Validate at the edge, then *propagate* identity inward (topic 16) so services trust the gateway's verification rather than each re-doing the full flow — but services still must not blindly trust unauthenticated internal callers (fallacy 4).
- **Leaking internal structure through routes.** If gateway paths mirror internal service names 1:1, splitting a service later changes the public API. Design the external API deliberately; the gateway is where you decouple it from internal topology.

---

## Code examples

Runnable demo — `14-api-gateway/gateway-demo/`. Read then `mvn test`:

1. `gateway-demo/src/main/java/com/example/gateway/GatewayApplication.java` — one route: `/inventory/**` → inventory-service, with a path rewrite and an added `X-Gateway` header (predicate → destination → filters).
2. `gateway-demo/src/test/java/com/example/gateway/GatewayRoutingTest.java` — boots the gateway with a stub downstream and asserts the request is routed, the path rewritten, and the header added.

The README also shows running the gateway in front of the scaffold's real inventory-service (`curl localhost:8080/inventory/SKU-BOOK`).

## Try this yourself

1. Add a second route to `GatewayApplication` for `/orders/**` → an order-service URI. Confirm the gateway now fronts two services on one port — that's the "single entry point" for the whole system.
2. Add an `after` filter that adds a response header (e.g. `addResponseHeader("X-Handled-By", "gateway")`). Observe it on the response — that's the return-trip half of edge processing.
3. Sketch (no code) where you'd put JWT validation: at the gateway, or in each service? Explain how topic 16 (propagation) lets the gateway validate once while services still know *who* the caller is.

## Self-check

1. What two problems does a gateway solve that having clients call services directly does not — one for the client, one for the services?
2. Name three cross-cutting concerns that belong at the gateway and one thing that must *not* live there. Why is the "must not" so important?
3. Distinguish an API gateway from a load balancer and from a BFF. Which handles "which service?", which "which instance?", and which "shaped for which client?"
