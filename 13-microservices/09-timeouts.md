# Timeouts

Topic 02 called this the single most important line you can add to a distributed system, and topic 05 showed the scaffold's call has none. This is that fix. A **timeout** is a bound on how long you'll wait for a downstream before giving up. Without one, a downstream that is merely *slow* — not even down — will hold your threads hostage until your own service falls over. Every one of the resilience patterns that follow (retry, circuit breaker, fallback) depends on this one: you cannot retry or fall back on a call that never returns.

The demo runs a deliberately slow stub and shows the same call hanging for the full duration without a tight timeout, then failing fast with one.

---

## The problem this solves

A remote call has to wait for the network and the far side to respond. The dangerous question is: *how long are you willing to wait?* The default answer for many HTTP clients is **forever** (or minutes). That's fine when everything's healthy — but when inventory-service gets slow (GC pause, overloaded DB, a bad deploy), your call to it sits and waits.

Here's why that's worse than the downstream simply being down. If inventory-service is *dead*, your connection is refused instantly and you get an error you can handle. If it's *slow* (say, 30 seconds per response), each call parks a thread for 30 seconds. On the servlet stack you have a bounded pool of request threads (Tomcat default ~200). Under load, they all end up parked waiting on the slow downstream, and now order-service can't serve *anyone* — including callers who never needed inventory. **A slow dependency takes you down more thoroughly than a dead one**, and a timeout is what prevents it.

## How it works: bound the wait, fail fast

A timeout converts "wait indefinitely" into "wait at most N, then throw." The demo shows both behaviours against a stub that sleeps 1500ms:

```java
// generous read timeout: the call SUCCEEDS, but only after blocking the full 1500ms
RestClient slow = clientWithReadTimeout(Duration.ofSeconds(5));
String body = slow.get().uri("/api/inventory/SKU-BOOK").retrieve().body(String.class);
// elapsed >= ~1500ms — that thread was unavailable the whole time
```

```java
// tight read timeout: the call FAILS FAST at ~300ms instead of hanging
RestClient bounded = clientWithReadTimeout(Duration.ofMillis(300));
// throws ResourceAccessException (wrapping SocketTimeoutException) at ~300ms
```

Configuring it is a property of the request factory behind the client:

```java
SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
factory.setConnectTimeout(Duration.ofMillis(500)); // max time to establish the TCP connection
factory.setReadTimeout(Duration.ofMillis(300));    // max time to wait for the response after connecting
RestClient client = RestClient.builder().requestFactory(factory).build();
```

The failure (`ResourceAccessException`) is now a *fast, catchable* error — which is exactly what makes the next topics possible: you can retry it (topic 10), or return a fallback (topic 11), all within a bounded time budget.

## The two timeouts (they are not the same)

- **Connect timeout** — the cap on establishing the TCP connection. Hitting it usually means the host is unreachable or down. This should be *short* (hundreds of ms) — a healthy service accepts connections almost instantly.
- **Read (socket/response) timeout** — the cap on waiting for the response *after* connecting. Hitting it means the far side accepted your request but is taking too long to answer. This is sized to the operation's expected latency plus headroom.

Both matter. A short connect timeout alone won't save you from a service that accepts the connection and then stalls; a read timeout alone won't save you from a host that's silently dropping SYNs. Set both.

## Choosing the value

A timeout that's too long doesn't protect you; one that's too short fails healthy calls. Size it from the downstream's real latency, not a guess:

- Base it on the downstream's **p99 latency plus margin**, not its average. If inventory-service answers in 20ms at p50 but 400ms at p99, a 100ms timeout will fail ~1% of *healthy* calls. Aim above p99 (e.g. 500ms–1s here), not above p50.
- **Budget end to end.** If a user request fans out A→B→C, the timeouts must nest: A's timeout on B must be shorter than the client's timeout on A, and must account for B's own timeout on C. Otherwise the outer caller gives up while inner calls are still waiting — wasted work. Give each hop a slice of the total budget.
- **Different operations, different timeouts.** A fast key lookup and a heavy report don't share a timeout. Set them per call, not one global value.
- **A timeout is not a maximum on the work done.** It's a maximum on *your waiting*. Which leads to the trap below.

## The timeout ambiguity (and why it forces idempotency)

When a call times out, you know one thing: *you stopped waiting.* You do **not** know whether the server did the work. The request may have arrived, been processed, and only the response was lost — or it may never have landed. From the caller's side these are indistinguishable.

This is why "it timed out, so I'll just retry" is dangerous for anything that changes state: retrying a `POST /reserve` that actually succeeded reserves the stock twice. A timeout means "unknown outcome," not "didn't happen." Retries (topic 10) are only safe on operations that are idempotent (topic 19) — and this ambiguity is the reason.

## When to use it / when not to

- **Always, on every outbound network call.** There is no production case for an unbounded remote call. If you take one rule from this module, it's this.
- **Also on the whole operation, not just the socket.** A per-call read timeout bounds one hop; for a composed request (topic 08) also consider a total-request deadline so the *sum* of hops stays bounded.
- **Tune, don't copy-paste.** A 30s default lifted from a tutorial is not a timeout, it's a formality. Size it to the actual downstream.

## Common pitfalls

- **No timeout at all.** The default. A slow downstream then exhausts your thread pool. This is the mistake this entire topic exists to kill.
- **Setting only one of the two.** Connect timeout without read timeout (or vice versa) leaves a gap a slow/stalled downstream slips through. Set both.
- **A timeout longer than your own caller's timeout.** If your client waits 2s for you but you wait 5s for the downstream, your client gives up first and your extra 3s of waiting is pure waste, still holding a thread.
- **Retrying on timeout without idempotency.** The ambiguity trap — the timed-out request may have succeeded. Double charge, double reserve (topics 10, 19).
- **One global timeout for every call.** A cheap lookup and an expensive aggregation get the same bound, so one is too loose and the other too tight. Set per operation.

---

## Code examples

Runnable demo — `09-timeouts/timeout-demo/`. Read then `mvn test`:

1. `timeout-demo/src/test/java/com/example/timeout/TimeoutTest.java` — a slow (1500ms) stub inventory, called with a generous read timeout (waits the whole 1500ms) versus a tight one (fails fast at ~300ms with `ResourceAccessException`).

The scaffold's `InventoryClient` (topic 05) has *no* timeout — this demo is the fix you'd apply to its `RestClient` request factory. Topics 10–11 build retry and fallback on top of the fast failure a timeout gives you.

## Try this yourself

1. Lower the stub's sleep to 100ms and set the read timeout to 300ms — now the call succeeds (downstream is within budget). Raise the sleep above the timeout again — it fails. You're feeling out where the timeout should sit relative to real latency.
2. Set `connectTimeout` very low (1ms) and point the client at a real but distant host. Observe a *connect* timeout instead of a read timeout, and note they're different failures with different causes.
3. In the scaffold's `InventoryClientConfig`, add a `SimpleClientHttpRequestFactory` with connect/read timeouts to the `RestClient.builder()`. You've just removed the latent outage topic 05 pointed out.

## Self-check

1. Why is a *slow* downstream often more dangerous to your service than a *dead* one? What resource runs out, and what does a timeout protect?
2. What's the difference between a connect timeout and a read timeout, and why do you need both?
3. A `POST /reserve` call times out. Do you know whether the reservation happened? What does that imply about retrying it, and which later topics address the problem?
