# Interview questions — System design basics

Each answer is what an interviewer hopes to hear: the reasoning and the trade-off, with numbers where they help. The numbers come from this module's simulations, which are deliberately simple models; in an interview, state your assumptions the same way.

---

## 1. Estimate the load for a URL shortener with 10 million daily users. What does the estimate tell you?

State assumptions first: each user creates a link every 10 days and clicks 10 links a day, a link takes about 500 bytes, and the peak is 3× the average.

That gives 1M writes a day (about 12 per second) and 100M reads a day (about 1,160 per second on average, 3,500 at peak): a 100:1 read/write ratio. Storage grows by about 182 GB a year, under 1 TB in five years, and bandwidth stays under 2 MB/s.

The conclusions matter more than the arithmetic: one database holds the data with no sharding; the read path dominates, so cache it and add read replicas if needed; writes are trivial. At 1,000 requests per second per instance, run 6 instances: peak at about 70% busy plus a spare, not 4 at 100%, because queueing explodes near saturation (topic 01). Size connection pools with Little's law: 700 requests per second holding a connection for 5 ms needs about 3.5 connections, but a database that slows down multiplies everything in flight. Then replace the guesses with a load test.

## 2. "We run two of everything, so we're highly available." How do you check that claim?

Draw the request path. Components a request needs *in series* multiply their availabilities, so every extra hop lowers the total: ten services at 99.9% give 99.0%. *Redundant* copies multiply their downtime: two 99% copies give 99.99%.

Then look for what still has one copy: the load balancer, the database primary, DNS, one data centre. A single point of failure caps everything; two 99% app instances between a 99.9% load balancer and a 99.9% database give about 99.8% (topic 02).

Then ask what the copies share. The parallel formula assumes independent failures; copies that share a host, a deploy, a configuration, or a dependency fail together. With a fully shared cause, two 99% replicas give 99%, the same as one. The fixes are spreading copies apart, rolling out changes gradually, a fallback for dependencies the request can live without, and faster repair: availability is MTBF / (MTBF + MTTR).

## 3. What does "stateless service" mean, and what typically stops a Spring Boot service from being one?

An instance is stateless when any request can go to any instance, and an instance can be killed or added at any moment without losing anything. That's what makes horizontal scaling, rolling deploys, and autoscaling safe.

The usual hidden state: HTTP sessions in memory, local caches (stale on other instances, module 15 topic 05), uploaded files on local disk, counters and rate limits in memory (a 3-attempt lockout becomes 9 attempts with 3 instances, topic 03), `synchronized` used as if it were a cluster-wide lock (module 15 topic 09), and `@Scheduled` jobs that run on every instance (module 08 topic 13). Each moves to a shared home: Redis or tokens for sessions, Redis for counters and limits, object storage for files, a database row or lock for job ownership. Sticky sessions hide the problem until the fleet changes, then lose the state of every user pinned to the instance that went away.

## 4. One of four instances behind your load balancer becomes 10× slower but still passes health checks. What happens, and how do you design against it?

With round-robin it keeps getting a quarter of the traffic. If that share exceeds what it can process, its queue grows without limit: in topic 04, p99 climbed to over 200 seconds while p50 stayed at 10 ms, because three quarters of the requests were fine. Health checks don't help, since the instance is alive and ready, just slow.

Defences: a load-aware algorithm (least connections sends it work only when it's idle, p99 100 ms; power of two choices samples two instances and picks the less busy, p99 268 ms, and needs no global view, which matters with several balancer nodes); outlier ejection based on latency or errors; timeouts with retries to another instance for idempotent requests; and per-instance p99 dashboards rather than fleet averages. Static weights only help when you know the slowdown in advance.

## 5. You put a CDN in front of a Spring Boot API. How do you set the caching headers, and what can go wrong?

The CDN is a shared cache keyed by URL, and the only thing that controls it is your response headers. The dangerous case is personalised data: a plain `max-age` lets the edge store it, the session cookie isn't part of the key, and one user's account page is served to the next (topic 05).

Rules of thumb: personal data gets `no-store` (or `private` if the browser may keep it); public data gets `public`, `max-age` for browsers, and a separate `s-maxage` for the edge; negotiated responses get `Vary`; data that changes unpredictably gets `no-cache` plus an ETag, so the edge revalidates with cheap 304s (a version-based ETag with `checkNotModified` avoids building the response, `ShallowEtagHeaderFilter` only saves bandwidth); static assets get content-fingerprinted URLs cached for a year, so a deploy needs no purge. Every TTL is accepted staleness: a changed product stays old at the edge until `s-maxage` runs out.

## 6. Your service writes to a database leader and reads from two asynchronous replicas. What can users see, and how do you fix it?

Replica lag produces three anomalies (topic 06). **Not seeing your own write:** a user saves their profile and the next read, from a replica, shows the old one. **Going back in time:** with two replicas lagging by different amounts, successive refreshes show SHIPPED, PAID, SHIPPED. **Lost writes on failover:** writes the leader acknowledged but hadn't shipped vanish when a replica is promoted.

Fixes: route a user's reads to the leader for a while after they write, or until the replica has applied the user's last write position; pin each user to one replica; keep decision-making reads (balance, stock, permissions) on the leader in the same transaction that acts on them; use synchronous or semi-synchronous replication for data you can't lose, accepting the extra latency on every write; and monitor replication lag as an SLI. In Spring, routing `readOnly` transactions to replicas with an `AbstractRoutingDataSource` inherits exactly these anomalies.

## 7. How do you shard a table, and what happens when you add a shard?

First choose the shard key from the query patterns: the most frequent queries should hit one shard. Queries by anything else become scatter-gather across all shards, and cross-shard joins and transactions become application work. The key must spread load evenly: an increasing id with range sharding sends every new write to the last shard, and a key with one huge value (a celebrity's id) creates a hot shard (topic 07).

With `hash(key) % N`, adding one shard moves about N/(N+1) of all keys: 80% going from 4 to 5. Consistent hashing places shards and keys on a ring, so a new shard takes only the keys between it and its neighbour, about 1/(N+1), and all moved keys go *to* the new shard. Virtual nodes (many points per shard on the ring) even out the load: with one point per shard the load's standard deviation was 49.5% of the average, with 100 points 4.6%. Many systems use the related trick of a fixed number of slots (Redis Cluster uses 16,384) moved between servers as whole units.

## 8. Explain CAP, and why "we chose CA" isn't an answer.

When the network partitions, a replica that can't reach the others must either refuse requests, keeping consistency, or answer from its own copy, keeping availability at the cost of stale or conflicting data. Partitions happen whether you choose them or not, so the real choice is C or A *during a partition*; without one you can have both.

PACELC adds the everyday cost: even without a partition, stronger consistency means waiting for more replicas. With replicas 5, 10, and 80 ms away, waiting for 1, 2, or 3 costs 5, 10, or 80 ms (topic 08). Quorums make the trade tunable: with N replicas, writes acknowledged by W and reads from R, W + R > N guarantees a read overlaps the latest write, and a minority side can't reach the quorum, so it refuses. W = R = 1 keeps both sides writable but needs conflict resolution, and last-write-wins with skewed clocks silently drops newer writes. Choose per operation: payments, locks, and unique usernames need consistency; feeds, carts, caches, and view counts can be eventually consistent.

## 9. Design a URL shortener. Walk through the key decisions.

**Requirements:** shorten, redirect, optional custom alias and expiry; redirects must be fast and highly available; heavily read-dominated. **Estimates** (100M links a month): about 40 writes and 4,000 reads per second on average, about 20,000 reads at peak, 6 billion rows and 3 TB over five years, about 33 GB of hot data (topic 09). Writes are trivial; a cache absorbs most reads.

**API:** `POST /urls` with an idempotency key, so a retried create doesn't produce a second link; `GET /{code}` with 302, because a 301 is cached by browsers and you lose click counts and the ability to change or expire links. **Codes:** 7 base62 characters give 3.5 trillion values. A truncated hash collides early (birthday bound) and can't be retried without a salt; a counter never collides but is enumerable unless scrambled, and should be handed out in ranges per instance; random codes need a unique constraint and a retry (at 6 billion links a 7-character code is taken 0.17% of the time, against 10.6% at 6 characters).

**Storage:** a lookup by primary key, so a relational table with a unique code is fine at this scale, with read replicas; shard by code or move to a key-value store when the numbers demand it. **Reads:** cache-aside in Redis with TTL jitter, stampede protection for viral links, and brief negative caching against random-code scans; a CDN can cache redirects. **Scale:** stateless instances behind a load balancer, and read-your-writes for brand-new links because replicas lag. **Analytics:** publish click events to Kafka instead of writing to the database on every redirect, and keep redirects working when Kafka is down. **Abuse:** distributed rate limiting on creates and malicious-URL checks. **Observability:** SLOs for redirect success and p99 latency with burn-rate alerts. **Failure walk:** without Redis the database takes every read; without the primary, creates fail but redirects continue from replicas and cache; without Kafka, only analytics are lost.
