# Common mistakes — System design basics

Design mistakes, each one reproduced by a simulation in this module. They rarely show up in development: one instance, one database, no lag, no failures. They show up at the first scale-out, the first failover, or the first viral spike, which is the worst moment to discover them.

---

## 1. Sizing for the average at 100% busy

```java
int instances = (int) Math.ceil(averageRps / maxRpsPerInstance);
```

**What goes wrong:** traffic peaks well above the average, and queueing time explodes long before 100%. In topic 01's `SaturationTest`, the average wait at 95% busy is 6 times the wait at 80%. Past capacity, throughput stays flat and the queue never drains: a 7.2-second average wait at 125% load. Losing one instance at peak then pushes the rest past the edge.

**Fix:** size for the *peak* at about 70% busy, plus one spare instance (6 instances instead of 4 in topic 01's estimate). Size thread and connection pools with Little's law: in-flight = requests per second × seconds per request.

---

## 2. "We run two of everything" with a shared cause of failure

```text
app-1 ─┐
       ├── one load balancer ── one database primary
app-2 ─┘          (both app instances on the same host, deployed together)
```

**What goes wrong:** the parallel formula (two 99% replicas give 99.99%) assumes the copies fail independently. Copies that share a host, a deploy, a configuration, or a dependency fail together: with a fully shared cause, two 99% replicas give 99%, the same as one (topic 02's `CorrelatedFailureTest`). And the single load balancer and database still cap the whole system at about 99.8% (`SeriesAndParallelTest`).

**Fix:** draw the request path, multiply what's in series, find what has only one copy, and ask what the copies share. Spread copies apart, roll out changes gradually, and cut repair time: availability is MTBF / (MTBF + MTTR), so repairing ten times faster is worth as much as failing ten times less often.

---

## 3. State kept inside the instance

```java
private final Map<String, List<String>> carts = new HashMap<>();          // per instance
private final Map<String, Integer> failedLogins = new HashMap<>();        // per instance
Files.write(Path.of("/tmp/uploads", name), bytes);                        // per instance
```

**What goes wrong:** behind a round-robin load balancer, a cart splits across instances and each request shows a different half; an uploaded file exists only on the instance that received it; and a 3-attempt login lockout allows 9 guesses with 3 instances, so scaling out weakens a security control (topic 03's `InMemorySessionTest`, `LocalStateTest`).

**Fix:** keep instances disposable: sessions in Redis or stateless tokens (module 15 topic 12, module 12 topic 08), counters and limits in Redis (module 15 topic 10), files in object storage or a database, scheduled jobs guarded by a lock row (module 08 topic 13).

---

## 4. Sticky sessions as the fix for state

```text
load balancer: session affinity = on
```

**What goes wrong:** it works until the fleet changes. After a scale-out, existing users stay pinned to the old instances and the new one gets none of their requests (5,000 / 5,000 / 0 in topic 04's `StickySessionsTest`). When an instance dies or is redeployed, everyone pinned to it loses their session: 300 of 900 users with 1 of 3 instances gone (and 500 of 1,000 carts in topic 03's `StickySessionTest`).

**Fix:** move the state out of the instance (mistake 3). Keep stickiness, if at all, as a cache-locality optimisation on top of a shared store.

---

## 5. Round-robin in front of instances that can slow down

```nginx
upstream app { server a; server b; server c; server d; }   # round-robin by default
```

**What goes wrong:** an instance that is slow but still passes health checks keeps getting its full share. If that share is more than it can finish, its queue grows for as long as it stays slow. In topic 04's `SlowServerTest`, p99 reached 201,700 ms while p50 stayed at 10 ms, so a median-based dashboard looks healthy.

**Fix:** least connections (p99 100 ms in the same simulation) or power of two choices (p99 268 ms, and it needs no global view), plus outlier ejection and per-instance p99 dashboards. Static weights only help until the slowdown changes.

---

## 6. Expecting a layer 4 balancer to rebalance long-lived connections

```text
clients hold one HTTP keep-alive / gRPC / WebSocket connection for hours
```

**What goes wrong:** a layer 4 balancer chooses a server once per *connection*. After a scale-out, every existing connection stays where it was and the new instances get nothing (45,000 requests each on the old instances, 0 on the new ones in topic 04's `LongLivedConnectionsTest`).

**Fix:** balance per request at layer 7 (an HTTP-aware proxy, or client-side balancing), or make clients reconnect periodically (every 100 requests in the demo evens the load to 22,500 each). Drain connections on deploy, not just requests.

---

## 7. A plain `max-age` on a personalised response behind a CDN

```java
return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)))
        .body(accountOf(user));
```

**What goes wrong:** without `private` or `no-store`, a shared cache may store the response. The session cookie isn't part of the cache key, so the edge serves Ann's account to Bob, and the application never even sees Bob's request (topic 05's `PersonalizedResponseTest`). Content negotiation has the same flavour: Spring doesn't add `Vary`, so whichever format was cached first goes to everyone (`VaryTest`).

**Fix:** `CacheControl.noStore()` for personal data, or `cachePrivate()` when only the browser may keep it. `varyBy("Accept")` for negotiated responses. `public` plus `s-maxage` only for responses that are identical for every user.

---

## 8. Reading your own write from a replica

```java
@Transactional(readOnly = true)                     // routed to a read replica
public Profile myProfile(long userId) { ... }       // called right after updateProfile(...)
```

**What goes wrong:** the replica hasn't applied the write yet, so the user sees their old data right after "saved". With round-robin across replicas that lag by different amounts, a refresh can go back in time: SHIPPED, then PAID, then SHIPPED (topic 06's `ReadYourWritesTest`, `MonotonicReadsTest`).

**Fix:** route a user's reads to the leader for a window after they write, or until the replica has applied the user's last write position (`ReadYourWritesReads`); pin each user to one replica for monotonic reads (`StickyReads`); and keep decision-making reads (balance, stock, permissions) on the leader.

---

## 9. Failing over to an asynchronous replica and assuming nothing was lost

```text
leader acknowledges writes before the replica has them; leader dies; replica is promoted
```

**What goes wrong:** every write the leader acknowledged but hadn't yet shipped is gone: 2 of 3 acknowledged orders in topic 06's `FailoverTest`. The client was told "saved".

**Fix:** synchronous (or semi-synchronous) replication for data you can't lose, accepting a follower round trip on every write (3,000 ms for 3 writes in the demo's exaggerated setup). Otherwise, know your replication lag, monitor it, and plan how to reconcile after a failover.

---

## 10. `hash(key) % N` with N = the number of servers

```java
int shard = Math.floorMod(hash(key), servers.size());
```

**What goes wrong:** adding one server changes the result for about N/(N+1) of all keys: 80,021 of 100,000 keys moved going from 4 to 5 servers in topic 07's `ModuloReshardTest`. Every moved key is a cache miss or a data migration. Kafka partitions have the same problem (module 16 topic 05).

**Fix:** consistent hashing with virtual nodes (19,073 keys moved, all of them to the new node, in `ConsistentHashingTest`), or a fixed, large number of slots that are moved between servers as whole units.

---

## 11. A shard key that concentrates load

```java
rangeSharding.shardFor(orderId);     // orderId only grows
hashSharding.shardFor(authorId);     // one author has millions of followers
```

**What goes wrong:** range sharding on an increasing id sends every new write to the last shard (all 100,000 in topic 07's `HotSpotTest`). Hash sharding on a key with one huge value puts that value's traffic on one shard (47,207 writes on one shard against about 7,500 on the others).

**Fix:** hash a key with many, evenly used values. For a known hot key, split it with a salt (`authorId#0..7`) and read all the salted parts; it helps but isn't perfect (the busiest shard dropped to 22,387, not 12,500, because salts land on the same shards).

---

## 12. Last-write-wins with server clocks

```java
if (incoming.timestamp() > stored.timestamp()) store(incoming);   // timestamps from each server's clock
```

**What goes wrong:** during a network partition both sides accept writes. When it heals, a server whose clock runs 1,500 ms slow makes the *newer* write lose, silently, on every replica (topic 08's `PartitionTest`).

**Fix:** majority quorums (W + R > N, and a minority side refuses writes) for data that can't lose updates; version numbers or version vectors with an application-level merge where both sides must stay writable.

---

## 13. Truncating a hash to make short codes

```java
String code = Base62.padded(Math.floorMod(sha256Prefix(url), Base62.capacity(7)), 7);
```

**What goes wrong:** the birthday bound makes collisions arrive far sooner than intuition says: a 50% chance after about 2.2 million URLs at 7 characters, under a day at 100M URLs a month. Retrying gives the same hash again, so the second URL can't be stored at all (topic 09's `HashCodeGeneratorTest`).

**Fix:** random codes with a unique constraint and a retry, or a counter (handed out in ranges per instance) scrambled before encoding. Sequential codes without scrambling are enumerable: anyone can read the next user's link (`SequenceCodeGeneratorTest`).
