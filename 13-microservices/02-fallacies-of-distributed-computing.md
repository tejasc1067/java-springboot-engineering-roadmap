# The Fallacies of Distributed Computing

The moment a method call becomes a network call, a set of assumptions your code has always been allowed to make quietly become false. These eight false assumptions have a name — the *fallacies of distributed computing* (L. Peter Deutsch and colleagues at Sun, 1990s) — and every distributed outage you will ever debug traces back to code that believed one of them. This topic is the bedrock: topics 09–12 (timeouts, retries, circuit breakers, bulkheads) are nothing more than the disciplined refusal to believe these eight things.

There's no runnable project here — but there is code, because the fallacies are easiest to see as the one line you forgot to write.

---

## The problem this solves

In a monolith, this is a normal, safe line:

```java
int available = inventory.checkStock(sku);   // a method call
if (available >= quantity) { ... }
```

It cannot hang. It cannot half-run. It returns in nanoseconds or throws an exception you can catch right here. Now you split `inventory` into its own service and the line becomes:

```java
int available = inventoryClient.checkStock(sku);   // an HTTP call over the network
if (available >= quantity) { ... }
```

It *looks* identical. That resemblance is the trap — the whole point of a good HTTP client is to make a remote call look like a local one. But the second line can hang for 30 seconds, return after the user left, succeed on the server but lose the response on the way back, or fail for one caller and not another. The code didn't change. The *assumptions* did. The fallacies are the checklist of assumptions that just broke.

## The eight fallacies

For each: the false belief, the naive code that assumes it, what actually happens, and the topic that fixes it.

### 1. The network is reliable

**The belief:** the call will go through and come back.
**Reality:** packets drop, connections reset, the downstream is mid-deploy, a switch reboots. Remote calls fail *routinely*, not exceptionally — at scale, something is always failing somewhere.

```java
// Assumes success. When the network blips, this throws — and if nothing
// upstream expects that, one blip becomes a 500 for the user.
var response = inventoryClient.checkStock(sku);
```

The fix isn't "make the network reliable" (you can't). It's to treat failure as a normal return path: **retries with backoff** for transient failures (topic 10) and a **circuit breaker** for sustained ones (topic 11). And retries force a second question — *is this operation safe to run twice?* (topics 10, 19).

### 2. Latency is zero

**The belief:** a call returns as fast as a method call.
**Reality:** a remote call is thousands to millions of times slower than an in-process call, and the tail is worse than the average — the p99 is what takes you down, not the median. A call that's usually 5 ms is occasionally 5 seconds.

The most expensive form of this fallacy is the **chatty call inside a loop** — the distributed N+1:

```java
// One network round-trip PER item. 100 items = 100 sequential remote calls.
// At 20 ms each that's 2 seconds; at the p99 of 500 ms each it's 50 seconds.
for (LineItem item : order.items()) {
    int stock = inventoryClient.checkStock(item.sku());   // <-- N calls
}
```

The fix is to stop pretending latency is free: **batch** (`checkStock(List<Sku>)` — one call), design coarse-grained APIs, and never put a remote call in a tight loop. This is also why fine-grained "nanoservices" (topic 01) hurt — they multiply round-trips.

### 3. Bandwidth is infinite

**The belief:** payload size doesn't matter, so return everything.
**Reality:** bandwidth is finite and shared. The convenient `GET /products` that returns all 50,000 products with every field serializes megabytes, saturates the link, and slows down every *other* call sharing it.

```java
// Returns the entire catalog on every call. Works with 10 products in a demo,
// falls over with 50,000 in production and starves neighboring calls.
return productClient.getAllProducts();
```

The fix: **pagination, projections/field selection, compression**, and asking for only what you need. Related to fallacy 2 — big payloads are also slow payloads.

### 4. The network is secure

**The belief:** it's an internal call between our own services, so it's safe.
**Reality:** "internal" is not a security boundary. Traffic between services can be sniffed or spoofed; a compromised service, or an attacker inside the network, can call any endpoint that trusts "internal" callers. And crucially, *identity does not travel by itself* — the downstream has no idea who the original user was unless you send it.

```java
// inventory-service trusts anyone who can reach it. If the only auth was at
// the gateway, any pod on the network can now reserve stock as anybody.
@PostMapping("/reserve")
public void reserve(@RequestBody Reservation r) { ... }   // no caller identity checked
```

The fix: authenticate service-to-service traffic and **propagate the security context** — carry the module-12 JWT across the hop so the downstream re-validates *who the user is* (topic 16). Defense in depth, not "the perimeter will save us."

### 5. Topology doesn't change

**The belief:** the downstream lives at a fixed address, so hardcode it.
**Reality:** in any real deployment, instances come and go constantly — autoscaling adds replicas, a crash removes one, a deploy replaces all of them with new IPs. The address you hardcoded is stale within the hour.

```java
// Hardcoded host:port. Breaks the moment inventory-service is redeployed,
// scaled out (which instance?), or moved.
var client = RestClient.create("http://10.0.4.17:8081");
```

The fix: **service discovery** — look services up by *name* from a registry, and let a client-side load balancer pick a live instance (topic 13). You call `http://inventory-service/...` and the infrastructure resolves it to whichever instances are currently up.

### 6. There is one administrator

**The belief:** one person understands and controls the whole system, so config and coordination are simple.
**Reality:** each service is owned by a different team, deployed on its own schedule, configured its own way. There is no single brain. A config change that's obvious to you is invisible to the team whose service you depend on, and version skew (you're on v2, they're still calling v1) is permanent.

The fix is organizational and mechanical: **centralized, versioned configuration** so config isn't scattered and untraceable (topic 15), explicit **contracts and versioning** so teams can evolve independently without breaking each other (topics 06, 21), and **distributed tracing** so that when something breaks across four teams' services, one trace id reassembles the whole request (topic 17).

### 7. Transport cost is zero

**The belief:** making the call is free.
**Reality:** every call has real cost — CPU to serialize/deserialize JSON, memory for buffers, connections from a finite pool, and (in the cloud) literal money for cross-AZ/egress traffic. Serialization alone can dominate the cost of a small call.

```java
// Each hop serializes the object to JSON and the next service parses it back.
// A→B→C→D means 3 serialize/deserialize round-trips for one user action —
// pure overhead the monolith never paid.
```

The fix: fewer, coarser calls (fallacy 2 again), reuse connections via **pooling** (a `RestClient` backed by a pooled connection factory, not a new connection per call), and prefer **async events** over synchronous fan-out when you don't need an immediate answer (topic 07).

### 8. The network is homogeneous

**The belief:** everything speaks the same formats and versions, so an object here deserializes cleanly there.
**Reality:** services are written in different languages, on different framework versions, evolving on different schedules. The `LocalDateTime` your service serializes one way, another parses another way. A field you added is a field an older consumer chokes on.

```java
// Serializes with your Jackson config + your enums + your date format.
// A Python service, or your own service one version behind, may not agree.
return new OrderDto(id, status /* a Java enum */, createdAt /* LocalDateTime */);
```

The fix: **explicit, language-neutral contracts** (well-specified JSON, stable field names, documented formats), be a **tolerant reader** (ignore unknown fields, don't break on additions), and pin the agreement with **contract tests** (topics 06, 21).

## Putting it together: the naive call, annotated

Every fallacy in one innocent-looking line, and the topic that disarms each:

```java
int available = inventoryClient.checkStock(sku);
//              └─ assumes: it will succeed (F1 → retry/breaker, topics 10–11)
//                          it returns fast (F2 → timeout, topic 09; batching)
//                          the caller is trusted / identity travels (F4 → propagation, topic 16)
//                          inventory-service is at a known address (F5 → discovery, topic 13)
//                          the call is free (F7 → pooling, coarse APIs)
//                          both sides agree on the format (F8 → contracts, topics 06/21)
```

The single most important line you can add — the one that addresses the deadliest fallacy (the network is reliable *and* fast) — is a **timeout**. Without it, a downstream that is merely *slow* (not even down) will hang your threads until your own service falls over. That's topic 09, and it's next after the boundaries are drawn.

## Common pitfalls

- **Making a remote call look exactly like a local one and then treating it like one.** The abstraction is convenient but it lies about failure and latency. Every remote call needs a timeout and a failure plan; a local one doesn't.
- **No timeout.** The default for many HTTP clients is *wait forever*. A slow downstream then exhausts your thread pool and takes you down with it — a slow dependency is more dangerous than a dead one (topic 09).
- **Retrying a non-idempotent operation.** "The network is unreliable, so I'll retry" is correct — but retrying `POST /charge` after a timeout can charge the customer twice, because the first call may have *succeeded* and only the response was lost (topics 10, 19).
- **Assuming a timeout means it didn't happen.** A timeout tells you *you gave up waiting*, not that the server didn't do the work. The reserve may have gone through. This ambiguity is the root of most distributed data bugs (topics 18–19).

---

## Try this yourself

No project to run — do the analysis, it's the skill:

1. Take the annotated `checkStock` line above and write, for each fallacy, *one concrete symptom* a user would see if you did nothing about it (e.g. F2 → "checkout spinner hangs for 30 s during a traffic spike"). Turning abstract fallacies into user-visible symptoms is exactly how you justify the resilience work in the rest of the module.
2. Find a synchronous call in any codebase you have access to and check: does it have a timeout? What happens to the caller if the downstream takes 60 seconds? If you can't answer, you've found a latent outage.

## Self-check

1. Why is "the network is reliable" false in a way that matters even when the network is *usually* fine? (Hint: what does "at scale" do to a one-in-a-million failure?)
2. A slow downstream (responding in 30 s) is often more dangerous to your service than a completely dead one. Why? Which fallacy and which fix does this point to?
3. You retry a `POST` that timed out, and now the customer is charged twice. Which fallacy did the retry logic ignore, and what would have prevented the double charge?
