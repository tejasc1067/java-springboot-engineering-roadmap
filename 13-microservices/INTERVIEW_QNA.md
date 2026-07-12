# Interview Q&A — Microservices

The questions you'd actually be asked about a distributed system. The answers explain *why* and *when*, not just *what* — microservices interviews reward judgement (when the trade is worth it, which failure each pattern addresses) far more than definitions.

---

## 1. When should you *not* use microservices, and what is a distributed monolith?

Default to a **monolith** (or a well-structured modular monolith) until you have a concrete reason not to. Microservices trade problems you understand — a big codebase, coordinated deploys — for problems that are genuinely harder: partial failure, network latency, distributed data, debugging across processes, operational overhead per service. That trade pays off when you have **independent scaling needs** (one part is CPU-bound and needs 20 instances while the rest needs 2), **independent deployability that teams actually use** (separate teams releasing on separate cadences), or **fault isolation** (one subsystem must not be able to take down another). It does *not* pay off for a small team on a young product still discovering its boundaries — you'll pay the distribution tax while your domain model is still moving.

A **distributed monolith** is the failure mode: you split into services but they're still coupled — they share a database, share DTOs as a compile-time dependency, or form synchronous call chains that can't tolerate version skew — so a routine change forces a lockstep deploy of several services. You get the operational cost of distribution with none of the independence. It's the default outcome of splitting without the discipline of private data and tolerant contracts, and it's strictly worse than the monolith you started with.

---

## 2. Name the fallacies of distributed computing. Which does a naive service call assume, and what fixes each?

The classic eight: the network is reliable; latency is zero; bandwidth is infinite; the network is secure; topology doesn't change; there is one administrator; transport cost is zero; the network is homogeneous. They're called *fallacies* because a naive in-process mindset assumes all of them, and each false assumption is a production incident waiting to happen.

Take one line — `stock = inventoryClient.getStock(sku)` — treated like a method call:

- Assumes **reliable**: the call can't fail. → it can (packet loss, downstream down). Fix: handle failure, add a circuit breaker + fallback.
- Assumes **zero latency**: the call is instant. → it isn't (30 ms, or 30 s when slow). Fix: **timeouts**; don't put N of these in a loop (chatty calls).
- Assumes it either succeeds or fails: → a timeout tells you *neither* — the work may have happened. Fix: idempotency, so a retry is safe.
- Assumes **topology is static**: the host:port is fixed. → instances come and go. Fix: **service discovery** + client-side load balancing instead of hardcoded addresses.
- Assumes **secure**: → the hop needs auth. Fix: propagate the token; TLS.

The fallacies are the bedrock under every resilience pattern in this module: each pattern exists to stop the code from assuming one more false thing.

---

## 3. How do you decide where a service boundary goes? Why isn't "two services on one database" two services?

Draw boundaries along **business capabilities / bounded contexts** — cohesive areas of the domain that change together and are owned by one team (ordering, inventory, payments), not along technical layers ("the database service," "the validation service," which just spreads one workflow across the network). A good boundary has **high cohesion inside** (the things that change together live together) and **loose coupling across** (services interact through stable contracts, not shared internals). A useful test: if implementing a typical feature requires touching several services in lockstep, your boundaries are wrong.

Two services sharing one database are **not** two services, because the database schema is an invisible shared contract: neither can change its tables without risking the other, neither can be deployed or scaled independently, and a bad migration in one is a bug in both. Independent data ownership — each service's storage private, reachable only through its API — is precisely what makes them separately evolvable, which is the whole point. Shared state is coupling by another name.

---

## 4. Synchronous call vs asynchronous event — what does each buy and cost, and how do you choose?

A **synchronous call** (order-service asks inventory over HTTP and waits) is simple and gives you an immediate, consistent answer — you know the result before you respond. Its cost is **temporal coupling**: the caller's availability and latency are now bounded by the callee's. If inventory is down, the call fails; if it's slow, you're slow. Chains of synchronous calls multiply this (each hop is another chance to fail, and latencies add).

An **asynchronous event** (order-service publishes `OrderPlaced`; inventory reacts on its own time) decouples them: the publisher succeeds regardless of the consumer's health, which buys **availability and resilience**, and it's the natural fit for one-to-many ("several services care about a new order"). Its cost is **eventual consistency and complexity**: the result isn't known immediately, delivery is at-least-once (so consumers must be idempotent), ordering and duplicate handling become your problem, and the overall flow is harder to see because it's spread across event handlers.

Rule of thumb: **synchronous when you need the answer now to proceed** (order-service genuinely can't confirm without knowing stock); **asynchronous when the downstream isn't required for the response** (sending a confirmation email — publish an event and move on). Coupling a critical operation's availability to a non-critical downstream via a synchronous call is a common, avoidable mistake.

---

## 5. Why is a call with no timeout a latent outage, and why is a slow downstream more dangerous than a dead one?

A call with no timeout blocks its thread until the OS eventually gives up (which can be minutes) or the downstream finally answers. Under load, requests keep arriving while threads sit parked on the slow call; the request thread pool fills with waiters; and then the service can't handle *any* request — including endpoints that never touch the slow dependency. One slow dependency becomes a total outage. That's why "we'll add timeouts later" is a latent outage, not a nice-to-have.

A **dead** downstream is comparatively kind: the connection is refused *immediately*, so the call fails fast and the thread is freed to fall back or error out. A **slow** one gives you no such signal — it *will* respond, eventually, so without a timeout you wait, and waiting is what exhausts the pool. Fail-fast beats hang. The timeout converts an unbounded hang into a bounded, controllable failure you can retry, fall back from, or surface as a clean error — and a bulkhead ensures even that bounded failure can't consume threads other dependencies need.

---

## 6. When is a retry safe, and what are backoff, jitter, and a retry storm?

A retry is safe only when the operation is **idempotent** — repeating it has the same effect as doing it once. The reason is that a failed call (especially a timeout) doesn't tell you whether the server already did the work; if it did, retrying a "charge" or "reserve" applies it twice. So retry reads freely, but retry writes only when they carry an idempotency key (or are naturally idempotent, like an absolute "set status = CONFIRMED"). Also retry only **transient** failures (timeouts, `503`, connection resets) — retrying a `400`/`404` just wastes attempts on a permanent answer.

- **Backoff** — wait longer between attempts (exponential: 200 ms, 400 ms, 800 ms). Retrying instantly hammers a downstream that's already struggling.
- **Jitter** — randomize the delay. Without it, every client that failed at the same instant retries at the same instant — a **thundering herd** that synchronizes load spikes.
- **Retry storm** — the amplification failure: N callers each retry M times against a struggling service, multiplying its load M-fold exactly when it can least handle it, turning a transient blip into a sustained outage. Backoff + jitter + a low attempt cap + a circuit breaker are what keep retries from becoming the outage.

---

## 7. Walk the circuit breaker's states. What should and shouldn't a fallback do?

A circuit breaker wraps a call and tracks its recent failure rate through three states:

- **Closed** (normal): calls pass through; the breaker counts failures. If the failure rate over a window crosses the threshold, it trips to open.
- **Open**: calls **fail fast immediately** without touching the downstream — this is the point, it stops hammering a failing dependency and stops your threads piling up on it. It stays open for a cool-off period.
- **Half-open**: after the cool-off, it lets a *limited* number of trial calls through. If they succeed, it closes (recovered); if they fail, it re-opens. This probes for recovery without flooding a still-sick service.

The breaker addresses **cascading failure** — without it, a downstream outage propagates upstream as everyone's threads block on the dead dependency. A **fallback** is what you return while the breaker is open, and the rule is that it must be **local, fast, and independent of the thing that failed**: a cached value, a safe default, an empty result, or a clean degraded response. A fallback that makes another remote call (into the same trouble) or does slow work defeats the breaker. And a fallback should degrade *honestly* — return "stock unknown" rather than fabricating "in stock," which could confirm an order you can't fulfil.

---

## 8. Bulkhead, rate limiter, circuit breaker — how do they differ?

All three protect a service, but against different things:

- **Circuit breaker** — reacts to a dependency's **failure**. It watches error rates and, when a downstream is failing, stops calling it (fail fast) until it recovers. Trigger: downstream health.
- **Bulkhead** — provides **isolation** by resource. It gives each dependency its own bounded pool of threads/permits, so a slow dependency can only exhaust *its* compartment, not the shared pool every other call needs. Trigger: concurrency limit per dependency. (Named after ship bulkheads: a breach floods one compartment, not the whole hull.)
- **Rate limiter** — caps **throughput**. It bounds how many requests are admitted per unit time (inbound, to protect *you* from being overwhelmed; or outbound, to respect a downstream's quota). Trigger: request rate.

They compose: a bulkhead confines a slow dependency, a circuit breaker stops calling it once it's clearly down, and a rate limiter keeps inbound load from overwhelming the service in the first place. A timeout underlies all of them — it's what makes "slow" become "failed" so the breaker can count it and the bulkhead's permits are released.

---

## 9. Why can't you use a distributed transaction (2PC) across microservices, and what replaces it?

A single `@Transactional` can't span multiple services' databases — there's no shared connection to commit or roll back. The textbook alternative, **two-phase commit (2PC/XA)**, *can* give atomicity across databases, but it's a poor fit for microservices: it **holds locks across network calls** (killing throughput), **blocks indefinitely if the coordinator dies** mid-commit, isn't supported by many datastores/cloud databases, and couples every participant into one synchronous ceremony. Distributed systems avoid it.

The replacement is the **saga**: model the operation as a sequence of **local** transactions, each with a **compensating** action that semantically undoes it. Run the steps forward; if a later step fails, run the compensations for the completed steps in reverse (release the reservation, refund the charge). You **give up atomicity and accept eventual consistency** — intermediate states are visible (an order is `PENDING` before `CONFIRMED`), there's no isolation between concurrent sagas, and compensation is a *new* transaction (a refund), not a rollback (you can't un-charge, you refund). Two coordination styles: **orchestration** (a central coordinator calls each step and its compensations — flow visible in one place, good for complex sagas) and **choreography** (services react to each other's events — no central component, but the flow is implicit and harder to debug). Best of all is often to *not need* a saga: if two datasets must always change together, that's a hint they belong in one service.

---

## 10. What does the CAP theorem actually say, and how does it apply to a service call?

CAP says that when a **network partition** (P) occurs — some nodes can't reach others — a distributed system must choose between **consistency** (C: every read sees the latest write, or an error) and **availability** (A: every request gets a non-error response, possibly stale). You cannot have both *during a partition*. The common "pick 2 of 3" phrasing is misleading: partitions are not optional (networks fail), so the real choice is **C vs A when P happens**; when there's no partition, you can have both.

Concretely, when `order-service` can't reach `inventory-service` (a partition between them), you choose: **CP** — refuse to answer rather than risk an inconsistent decision (reject the order, return an error), or **AP** — stay available with a possibly-stale or degraded answer (a circuit breaker fallback returning cached/"unknown" stock). Neither is universally right: a bank balance leans CP (better to error than show wrong money); a product page leans AP (better to show slightly stale stock than go down). The circuit-breaker-with-fallback pattern is an explicit AP choice, and the saga's eventual consistency is CAP's "you traded strong consistency for availability" made concrete.

---

## 11. At-least-once, at-most-once, exactly-once — which is realistic, and how do you make at-least-once safe?

Three delivery guarantees:

- **At-most-once** — deliver and don't retry. Simple, but messages can be **lost** (a crash before processing loses the message). Rarely acceptable for anything that matters.
- **At-least-once** — retry until acknowledged. Nothing is lost, but a message can be **delivered more than once** (the consumer processed it, then crashed before acking, so it's redelivered). This is what real brokers and the outbox give you.
- **Exactly-once** — every message effected exactly one time. The one everyone wants and nobody gets end-to-end over a network: true exactly-once *delivery* is impossible in the presence of failures. What's actually achievable is **effectively-once processing** = at-least-once delivery + **idempotent** consumers.

So the realistic contract is **at-least-once**, and you make it safe by making consumers **idempotent**: dedupe on the message/event id (a `processed_events` table or a unique constraint) so a redelivery is a no-op. This is why idempotency and the outbox are two halves of one solution — the outbox reliably *emits* at-least-once (never lost), and idempotent consumers safely *absorb* at-least-once (never double-applied). One without the other is either lossy or duplicative.

---

## 12. Explain the dual-write problem and the transactional outbox.

The **dual write** is trying to update two systems — commit a business change to your database *and* publish an event to a broker — as if they were one atomic operation. They aren't, and you can't make them atomic without a distributed transaction. So there's a gap: crash after the DB commit and before the publish → the change exists but the event is **lost**; publish first and then fail the DB write → you emitted an event for a change that didn't happen (**phantom event**). Every naive `save(); publish();` has this bug; it just hides until a crash lands in the window.

The **transactional outbox** removes the gap. Instead of publishing directly, write the event into an `outbox` table **in the same local transaction** as the business row — now they commit together or not at all, with no distributed transaction because both writes go to the *same* database. A separate **relay** (a background poller, or change-data-capture tailing the DB log, e.g. Debezium) reads unpublished outbox rows, publishes them to the broker, and marks them published. The relay may publish, crash before marking, restart, and publish again — so publishing is **at-least-once**, which is exactly why consumers must be idempotent (Q11). It's the standard, correct answer to "publish an event reliably when I commit a change."

---

## 13. What is an idempotency key — where does it come from, and where do you store it?

An idempotency key is a unique id for **one logical request** that the client generates and sends with a state-changing call, so the server can recognise a repeat and *not do the work twice*. It's what makes an otherwise-unsafe operation (reserve, charge, increment) safe to retry: the server processes the first request under key `K`, stores the result, and for any later request with the same `K` returns the stored result instead of re-applying the effect.

Two things matter. **The key must be stable across retries** — the client generates it **once per logical request** and reuses it on every retry of *that same* request; a fresh key per attempt defeats the purpose because each looks new. **The key must be persisted**, not held in memory: store processed keys in the database (a `processed_requests` table, or a unique constraint on the key column) so idempotency survives a restart and works across instances. A clean implementation is to insert the key inside the same transaction as the work — a duplicate-key violation *is* the "already processed" signal. (For sagas, a natural key is something stable per saga-instance + step, so a retried step doesn't double-apply.)

---

## 14. How do you test a microservice in isolation, and what does a contract test catch that a stub does not?

Test a service **in isolation** by **stubbing its downstreams** — stand up a fake HTTP server (WireMock) programmed with canned responses and point the service's *real* client at it. This runs the real HTTP path (URL, serialization, status handling, timeout config) without needing the real downstream up, and — crucially — lets you reproduce failures on demand (500s, slow responses, malformed bodies) so your resilience code is actually exercised. It's better than mocking the client *object* (Mockito), which deletes the real client and so can't catch a wrong URL, a JSON mapping bug, or mishandled status. It's the "service/component test" layer of the pyramid: many of these, few end-to-end tests.

The gap a stub leaves is **drift**: your stub encodes *your belief* about the downstream's contract, frozen in your test. If the real downstream renames a field or changes a status and you don't update the stub, your tests stay green while production breaks. **Contract testing** (Spring Cloud Contract) closes this: a single shared **contract** generates *both* the consumer's stub *and* a provider verification test the provider runs on its own build. If the provider stops honouring the contract, the **provider's** build goes red — the break is caught at build time on the side that caused it, not in production. And **consumer-driven** contracts mean the provider carries its consumers' real expectations, so it learns exactly which consumers a change would break before shipping. Stub for "do I handle this response correctly"; contract-test for "do the two services still agree."
