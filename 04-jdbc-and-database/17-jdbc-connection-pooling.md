# 17 — Connection Pooling and HikariCP

Up until now every example called `DriverManager.getConnection(...)` and got a fresh connection. That's fine for scripts. It's catastrophic for a real backend serving many requests.

A **connection pool** is a small cache of pre-opened, ready-to-use connections. Your code "borrows" one, uses it, returns it. The pool keeps them warm for the next caller.

This topic explains why pools exist, how they fix the problem, and how to use HikariCP — the standard Java pool, used by Spring Boot by default.

> **Setup needed:** by this point you should have all four jars from `SETUP.md` in your `lib/` folder (H2 + HikariCP + slf4j-api + slf4j-simple).

---

## Why opening a connection is expensive

Each `DriverManager.getConnection(...)` involves:

1. TCP handshake to the database server (~1ms on a fast LAN, more over the internet).
2. TLS handshake if encryption is enabled (~10–50ms).
3. Authentication (the database verifies credentials).
4. Session setup (server allocates resources for your session).

Total: maybe 20–100ms per connection. **Per request.** A web app handling 1000 requests/sec doing this is spending most of its time in connection setup, not in real work.

Once opened, running an actual query takes ~1ms or less. The waste is enormous.

---

## What a connection pool does

```
  Web request 1 ─┐
  Web request 2 ─┤
  Web request 3 ─┼──→  [pool: 10 open connections]  ──→  database
  Web request 4 ─┤
  Web request 5 ─┘
```

The pool keeps, say, 10 connections open at all times. Each incoming request "checks one out" (an O(1) hashmap-ish operation), uses it, returns it. No TCP handshake per request.

When traffic spikes and more concurrent requests come in than there are connections, additional requests *wait* briefly until one returns. When traffic drops, idle connections sit ready.

**The pool also handles:**

- Detecting dead connections (network blip, database restart) and replacing them.
- Bounding total open connections so you don't overwhelm the database.
- Timing out requests that wait too long.
- Detecting leaked connections (borrowed but never returned).

---

## DriverManager vs DataSource

The old API: `DriverManager.getConnection(url, user, pass)`. Creates a fresh connection every call.

The pool API: a `DataSource` object you call `getConnection()` on. Looks identical to your code, but instead of creating a new connection, the pool hands you a pooled one. When you call `conn.close()`, the connection isn't closed — it's returned to the pool.

```java
DataSource dataSource = createPool();

try (Connection conn = dataSource.getConnection()) {
    // use conn normally
}
// "close" actually returns to pool
```

**Your application code doesn't change.** Connection-pool magic happens behind the `DataSource` interface.

---

## Why HikariCP

HikariCP is the production standard. Some others (Apache DBCP, C3P0) exist but are mostly historical at this point. Spring Boot uses HikariCP by default.

Reasons it won:

- Fastest, by a wide margin.
- Smallest config surface — you can run it correctly with five lines of setup.
- Honest defaults that work for most apps.
- Excellent leak detection.

The basics:

```java
HikariConfig config = new HikariConfig();
config.setJdbcUrl("jdbc:h2:mem:demo");
config.setUsername("sa");
config.setPassword("");
config.setMaximumPoolSize(10);

HikariDataSource pool = new HikariDataSource(config);

// later, anywhere in the app:
try (Connection conn = pool.getConnection()) {
    // use it like any JDBC connection
}

// at shutdown:
pool.close();
```

---

## Key configuration knobs

| Setting | What it does | Typical value |
|---------|-------------|---------------|
| `maximumPoolSize` | Max simultaneous open connections | 5–20 for typical apps |
| `minimumIdle` | Keep this many warm even when idle | Same as max (HikariCP recommends) |
| `connectionTimeout` | How long a `getConnection()` call waits for a free slot before throwing | 30s default |
| `idleTimeout` | How long an idle connection sits before being closed | 10m default |
| `maxLifetime` | Force-close connections after this age (regardless of use) | 30m default |
| `leakDetectionThreshold` | Warn if a connection is checked out longer than this | 0 (off); 60s in dev |

### Sizing the pool

The most common question: "what should `maximumPoolSize` be?"

**The honest answer:** smaller than you think.

A pool size of **10** handles a surprising amount of traffic. The pool only needs to be as large as your *concurrent in-flight* queries, not your total request rate. If queries take 5ms and you do 1000/sec, average concurrency is 5 — a pool of 10 has slack.

Bigger isn't better — too many connections hurt the database (each open connection costs memory and CPU on the database side). Production databases often max out around 200 connections total across all clients. If 30 app servers each have a pool of 50, you've already exceeded that.

Start with 10. Measure. Increase only if `getConnection()` is regularly waiting.

---

## What goes wrong without pooling

Three production-grade failures, all from missing or broken pooling:

1. **"Too many connections"** — your app opens connections faster than the database can serve, hits the database's connection cap. Every new request fails.
2. **Slow responses under load** — connection setup time becomes most of each request's latency.
3. **Cascading failures** — when the database briefly hiccups, every request that was mid-handshake fails. With a pool, healthy already-open connections still work.

---

## What goes wrong WITH pooling, when used badly

Two production-grade failures specific to pools:

1. **Pool exhaustion** — the pool is full, every connection in use, new requests wait, time out, fail. Symptoms: latency cliff, then errors. Often caused by long-running transactions (a request holds a connection for seconds while talking to a slow external API).
2. **Connection leaks** — a code path borrows a connection and forgets to close it. Eventually the pool runs out. Same symptoms as #1, but caused by code, not load.

This is why `try-with-resources` (topic 13) and short transactions (topic 15) matter — both prevent these.

---

## Code examples

1. `NewConnectionPerCallSlow.java` — opens a new connection per query. Times 100 queries.
2. `HikariCpBasic.java` — same 100 queries via a HikariCP pool. Times the difference.
3. `PoolExhaustion.java` — pool of size 3, 5 threads holding connections forever. Sixth request times out.
4. `HikariConfiguration.java` — walks through each major config option, what it does, and what good values look like.

---

## Try this yourself

1. In `HikariCpBasic.java`, raise the query count from 100 to 10,000. How does the per-query latency compare to `NewConnectionPerCallSlow.java` at the same count?
2. In `PoolExhaustion.java`, raise the pool size to 6. Does the sixth request still time out?
3. In `HikariConfiguration.java`, set `leakDetectionThreshold` to 1000 ms and deliberately hold a connection longer than that. Does the warning appear?

---

## Self-check

1. Why is `DriverManager.getConnection(...)` per request a problem for a high-traffic web app?
2. What does `conn.close()` actually do when `conn` came from a HikariCP `DataSource`?
3. You see "Connection is not available, request timed out" in the logs. Name two likely root causes.
