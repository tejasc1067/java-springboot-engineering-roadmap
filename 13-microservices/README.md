# 13 — Microservices

Every module before this one built **one** Spring Boot service. It had one process, one database, one deployment, and one place to look when something broke. A method call that failed did so instantly and locally: you got a stack trace. A transaction either committed or rolled back. The whole application was either up or down.

This module removes that assumption. From here on, your system is **many** services in **many** processes on **many** machines, talking to each other over a network — and the network is slow, sometimes fails, and never tells you the difference between "the other service is thinking" and "the other service is dead." A call to another service is no longer a method call; it's an I/O operation that can hang for 30 seconds, half-succeed, or return after your user has already given up. A "transaction" can no longer span the whole operation, because there is no single database to roll back.

The uncomfortable truth of this module: **microservices trade a set of problems you understand (a big codebase, coordinated deploys) for a set of problems that are genuinely harder (partial failure, distributed data, network latency, debugging across processes).** Most teams that adopt microservices do not need them and end up with a *distributed monolith* — all the operational pain of distribution with none of the independence. So this module teaches microservices honestly: what they cost, when the trade is worth it, and — when you do split — the patterns that keep a distributed system from falling over the first time one service gets slow.

You will build a small two-service system — an `order-service` that must check stock with an `inventory-service` before accepting an order — and take it from "works on my machine when everything is up" to "survives the downstream being slow, flaky, or down." Along the way you'll make service-to-service calls, add timeouts and retries and a circuit breaker, put a gateway in front, propagate the module-12 JWT and a trace id across the hop, and handle data consistency without the distributed transaction you can no longer have.

## Who this is for

- Anyone who finished the core (modules 01–12) and can build, secure, and test a single Spring Boot service, and now wants to understand how services are composed into a larger system.
- Developers who have seen `@FeignClient`, `resilience4j`, `spring-cloud-gateway`, or "the order service calls the inventory service" in a codebase and want to know what those pieces actually do and why they're there.
- Anyone who has heard "we should break this monolith into microservices" and wants to be able to say *when that's a good idea and when it isn't* — with reasons, not vibes.

## What you'll be able to do at the end

- [ ] Explain what microservices cost (operational complexity, partial failure, distributed data) and name the concrete conditions under which the trade is worth it — and the *distributed-monolith* trap when it isn't.
- [ ] Recite the fallacies of distributed computing and point to the line of code in a naive service call that assumes each false thing.
- [ ] Draw service boundaries along business capabilities / bounded contexts, and explain why two services sharing one database is not two services.
- [ ] Make a synchronous service-to-service call with a declarative HTTP interface, and explain why a bare `RestClient` call with no timeout is a latent outage.
- [ ] Evolve a service's API without breaking its callers (additive changes, tolerant readers, versioning) and explain what a "breaking change" is across a network boundary.
- [ ] Choose between a synchronous call and an asynchronous event, and explain what each buys and costs (coupling, latency, consistency).
- [ ] Build a read that aggregates data owned by several services (API composition) and reason about its failure modes.
- [ ] Add a **timeout** to every outbound call and show a caller hanging without one.
- [ ] Add **retries with backoff**, and explain why a retry is only safe on an idempotent operation.
- [ ] Add a **circuit breaker with a fallback**, and show a downstream outage cascading into the caller without one.
- [ ] Isolate a slow dependency with a **bulkhead** and cap load with a **rate limiter**, and explain how each differs from a circuit breaker.
- [ ] Register services with a **discovery** server and call them by name with client-side **load balancing**, instead of hardcoding host:port.
- [ ] Put an **API gateway** in front as the single entry point, and route/filter at the edge.
- [ ] Externalize configuration to a **config server** so config changes without a redeploy.
- [ ] **Propagate the security context** (the module-12 JWT) across a service hop so the downstream knows who the caller is.
- [ ] **Propagate a trace/correlation id** so one request across two services shows up as one trace in the logs.
- [ ] Handle a multi-service operation with a **saga** (there is no distributed transaction), and explain orchestration vs. choreography and eventual consistency.
- [ ] Make an operation safely repeatable with an **idempotency key**, and publish events reliably with the **outbox pattern**.
- [ ] **Test** a service in isolation by stubbing its downstream (WireMock/Testcontainers), and pin the contract between two services with **consumer-driven contract tests**.

If you can do all of those, the module worked.

## Prerequisites

- **Modules 08–09 done.** You need Spring Boot (auto-config, properties, profiles, fat jar) and REST (`@RestController`, `ResponseEntity`, status codes, JSON) cold — this module is those skills applied *across* services.
- **Module 11 done.** Errors now cross a network boundary; the `ProblemDetail`/`@RestControllerAdvice` contract still applies, and a downstream failure has to become a sane status for *your* caller.
- **Module 12 done.** Topic 16 propagates the JWT you learned to issue and validate in module 12 across a service call. You should already know what a bearer token is and how a resource server validates it.
- **Module 10 helpful.** Each service owns its own data with Spring Data JPA + H2; the saga and outbox topics assume you know what a transaction is.
- A JDK installed (Java 17+; examples target Java 21).
- For the messaging topic (07) and the outbox topic (19), a local broker runs via Testcontainers/Docker — that topic's README gives the one-command setup so you never need a hosted broker.

## How this module is organized

Topics 01–03 are **the honest framing** and are markdown-only. Before any code, you need to know what distribution costs (01), why the network breaks the assumptions your single-service code was built on (02), and where the boundaries between services actually go (03). Skipping these is how teams end up with a distributed monolith.

Topic 04 starts the code: two services, **each owning its own database**, which is what makes them two services at all.

Topics 05–08 are **communication**: the synchronous call (05), evolving its contract without breaking callers (06), the asynchronous-event alternative (07), and composing a view from several services (08).

Topics 09–12 are **resilience** — the heart of the module, because in a distributed system failure is normal, not exceptional. Timeouts (09), retries with backoff (10), circuit breaker with fallback (11), and bulkhead + rate limiting (12). Each shows the failure first, then the fix.

Topics 13–15 are the **infrastructure** that stops the system from being a pile of hardcoded URLs: discovery + load balancing (13), an API gateway (14), and centralized configuration (15).

Topics 16–17 are the **cross-cutting** concerns that have to travel *with* a request across the hop: the security context (16) and the trace id (17).

Topics 18–19 are **distributed data** — the hardest part — living without a distributed transaction: the saga (18) and idempotency + the outbox (19).

Topics 20–21 are **testing** a system whose pieces are separately deployable: isolating a service from its downstream (20) and pinning the contract between them (21).

```text
01-why-microservices-and-when-not        <- what distribution costs; when the trade is worth it; the distributed-monolith trap; markdown-only
02-fallacies-of-distributed-computing     <- the network is not reliable/instant/free; the bedrock under every resilience topic; markdown-only
03-service-boundaries-and-decomposition   <- bounded contexts, split by business capability, why shared state is not a boundary; markdown-only
04-database-per-service-and-data-ownership <- two services, two databases; the shared-database anti-pattern
05-synchronous-service-calls              <- order->inventory via a declarative HTTP interface / RestClient
06-inter-service-contracts-and-versioning <- evolving an API without breaking callers; additive change, tolerant reader, versioning
07-asynchronous-messaging-and-events      <- the event alternative to a sync call; when to choose it; coupling vs consistency
08-api-composition-and-aggregation        <- building a read that spans services; its failure modes
09-timeouts                               <- a slow downstream hangs the caller; bound every outbound call
10-retries-and-backoff                    <- exponential backoff + jitter; why a retry is only safe when idempotent
11-circuit-breaker-and-fallback           <- an outage cascades into the caller; the breaker + fallback stops it
12-bulkhead-and-rate-limiting             <- isolate a slow dependency; cap inbound load; how each differs from a breaker
13-service-discovery-and-load-balancing   <- register + call by name; client-side load balancing instead of hardcoded host:port
14-api-gateway                            <- single entry point; routing and edge filters
15-centralized-configuration              <- externalized config; change config without a redeploy
16-security-context-propagation           <- carry the module-12 JWT across the hop so the downstream knows the caller
17-distributed-tracing-and-correlation    <- one trace id across both services; one request = one trace in the logs
18-distributed-transactions-and-saga      <- no 2PC; orchestration vs choreography; eventual consistency
19-idempotency-and-the-outbox-pattern     <- idempotency keys; reliable event publishing; dedup
20-testing-microservices                  <- stub the downstream with WireMock/Testcontainers; test a service in isolation
21-contract-testing                       <- consumer-driven contracts so a breaking change fails a test, not production
```

```text
COMMON_MISTAKES.md       <- real distributed bugs with code: no timeout, retry storm, shared database, chatty calls, lost trace id, non-idempotent retry
INTERVIEW_QNA.md         <- interview-grade questions: when NOT to use microservices, CAP, saga vs 2PC, circuit breaker states, at-least-once vs exactly-once
```

## The toolkit (what we add to the pom)

Module 13 stays on the same Spring Boot version as modules 08–12 so projects move between modules without surprises, and adds the Spring Cloud release train that lines up with it. The core new pieces are a declarative HTTP client, Resilience4j (via Spring Cloud Circuit Breaker), and — for the infrastructure topics — a handful of Spring Cloud starters.

| Library | Coordinates | What it does | First used in |
|---------|-------------|--------------|---------------|
| Spring Boot Starter Parent | `org.springframework.boot:spring-boot-starter-parent:3.5.14` | parent POM; locks versions | every topic |
| Spring Cloud BOM | `org.springframework.cloud:spring-cloud-dependencies` (release train `2025.0.x`, aligned to Boot 3.5) | locks all Spring Cloud versions | topics using `spring-cloud-*` |
| Spring Boot Starter Web | `org.springframework.boot:spring-boot-starter-web` | `@RestController`, MVC, embedded Tomcat | every service |
| Spring Boot Starter Data JPA + H2 | `...data-jpa`, `com.h2database:h2` | each service owns its own database | topic 04 |
| Spring Web `RestClient` / HTTP Interface | built into `spring-web` | the synchronous service-to-service call | topic 05 |
| Spring Cloud Circuit Breaker (Resilience4j) | `org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j` | timeouts, retries, circuit breaker, bulkhead, rate limiter | topics 09–12 |
| Spring Cloud Netflix Eureka | `...spring-cloud-starter-netflix-eureka-server` / `-client` | service registry + client-side load balancing | topic 13 |
| Spring Cloud Gateway | `org.springframework.cloud:spring-cloud-starter-gateway` | API gateway: routing + edge filters | topic 14 |
| Spring Cloud Config | `...spring-cloud-config-server` / `spring-cloud-starter-config` | centralized, refreshable configuration | topic 15 |
| Spring Boot Starter OAuth2 Resource Server | `...spring-boot-starter-oauth2-resource-server` | validate the propagated JWT at the downstream | topic 16 |
| Micrometer Tracing + Brave/OTel bridge | `io.micrometer:micrometer-tracing-bridge-*` | trace/correlation id propagation across the hop | topic 17 |
| Spring for Apache Kafka (or RabbitMQ) via Testcontainers | `org.springframework.kafka:spring-kafka`, `org.testcontainers:*` | the async event bus; a real broker for tests | topics 07, 19 |
| WireMock | `org.wiremock:wiremock-standalone` (or Spring Cloud Contract WireMock) | stub the downstream to test a service in isolation | topic 20 |
| Spring Cloud Contract | `org.springframework.cloud:spring-cloud-starter-contract-verifier` | consumer-driven contract tests | topic 21 |

Exact versions are pinned by the Boot parent and the Spring Cloud BOM; the first demo's `pom.xml` is the source of truth and is verified to build.

## How to run the examples

The demos live under `CODE_EXAMPLES/`. The shared two-service system (`order-service` + `inventory-service`) is the reference the topics build on; each topic that needs a variation has its own focused, runnable project under `CODE_EXAMPLES/NN-topic-name/`. A typical topic demo:

```bash
cd 13-microservices/CODE_EXAMPLES/09-timeouts/timeout-demo
mvn -q test                 # runs the assertion: caller hangs without a timeout, fails fast with one
mvn -q spring-boot:run      # boots the service(s) so you can curl the behavior yourself
```

Because a distributed demo needs the downstream running, each topic's README says exactly what to start and in what order (often just one command — the demo boots a stub downstream, or uses Testcontainers to bring a broker up for the test). Every demo prints or logs the thing being demonstrated: the request that hung, the retry that fired, the breaker that opened, the trace id that survived the hop.

## Checkpoint

You're done with this module when you can:

1. Given a team proposing to split their monolith, list three questions whose answers decide whether microservices help or just add a network in the middle — and describe what a distributed monolith looks like.
2. Take a naive `order-service` call to `inventory-service` and name every distributed-computing fallacy it assumes, then add the timeout, retry, and circuit breaker that stop each assumption from taking the service down.
3. Explain why `order-service` and `inventory-service` must not share a database, and what you do instead when `order-service` needs inventory data.
4. Walk the circuit breaker's states (closed → open → half-open) and say what a fallback should and should not do.
5. Explain why you cannot wrap "reserve stock" and "create order" in one transaction across two services, and describe the saga (with a compensating action) that replaces it.
6. Explain why a retried "reserve stock" call can double-reserve, and how an idempotency key or the outbox pattern prevents it.
7. Show one request flowing through the gateway to both services and appearing as a single trace id in the logs, and explain how the id and the JWT got there.
8. Test `order-service` with `inventory-service` completely stubbed, and explain what a consumer-driven contract test catches that the stub does not.

If those eight feel comfortable, the core-plus-breadth of distributed backend work is in place; module 14 (Docker & DevOps) is how you actually run these services somewhere.

## A note on what's *not* here

- **No Kubernetes, service mesh, or deployment.** Running and orchestrating these services (containers, k8s, Istio/Linkerd, blue-green/canary) is module 14 and beyond. This module is the *application-level* patterns; how you deploy them is a separate skill.
- **No Kafka internals.** Topic 07 uses messaging as the async alternative to a sync call and topic 19 uses it for the outbox, but partitions, consumer groups, offsets, delivery semantics, and stream processing are module 17 (Kafka & event-driven). We use just enough of a broker to make the pattern real.
- **No distributed caching.** Redis, cache-aside, and cache invalidation across services are module 18. A slow downstream here is fixed with resilience patterns, not a cache.
- **No metrics dashboards or full observability stack.** Topic 17 covers trace/correlation id *propagation* — the microservices-specific part — but Prometheus/Grafana/alerting and log aggregation infrastructure are module 19.
- **No reactive / WebFlux.** The roadmap is servlet-stack throughout; reactive service calls (`WebClient` on Reactor, R2DBC) are analogous but out of scope.
- **No building your own service registry, gateway, or config server from scratch.** You *use* Eureka, Spring Cloud Gateway, and Spring Cloud Config; writing your own is not a fundamentals task.
