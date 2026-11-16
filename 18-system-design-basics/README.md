# 18 — System Design Basics

Every module so far zoomed in on one piece: a class, a query, a service, a container, a cache, a broker, a dashboard. This last module zooms out. **System design** is deciding how those pieces fit together so a system keeps working as traffic grows, machines fail, and data spreads across more than one database. It's also what "senior" backend interviews and design reviews are mostly about.

The goal here isn't to memorise architectures of famous companies. It's to learn the handful of ideas every design is built from (estimating load, removing single points of failure, keeping instances stateless, balancing load, caching at the edge, replicating and partitioning data, and choosing consistency) well enough to reason about a system you've never seen. Each topic has a small plain-Java simulation, so the claims are tested rather than asserted: the replica that shows an old value, the shard that melts, the load balancer that piles requests onto the slow instance. The module ends with a full design walkthrough that uses everything from modules 14–18.

## Who this is for

- Anyone who has finished modules 13–17 and can build and run services, but hasn't had to decide how many, where, and with what data layout.
- Developers preparing for system design interviews who want the reasoning, not a list of buzzwords.
- Anyone who has been asked "will this scale?" and wasn't sure how to answer with numbers.

## What you'll be able to do at the end

- [ ] Turn "10 million daily users" into requests per second, storage per year, and a number of instances, with a back-of-envelope estimate.
- [ ] Compute availability for components in series and in parallel, find the single points of failure in a design, and explain why redundancy needs independent failures.
- [ ] List every kind of state that stops a service instance from being disposable, and where each should live instead.
- [ ] Choose a load-balancing algorithm and explain why round-robin fails when one instance is slow.
- [ ] Set `Cache-Control` and ETags so a CDN caches what it should and never serves one user's data to another.
- [ ] Explain replica lag and its symptoms (users not seeing their own writes, values going back in time), and the fixes.
- [ ] Choose a shard key, explain hot shards, and show why consistent hashing moves far fewer keys than `hash % N`.
- [ ] State CAP precisely, explain quorums, and say which consistency model a feature needs.
- [ ] Walk through the design of a URL shortener from requirements to failure modes.

If you can do all nine, the module worked, and so did the roadmap.

## Prerequisites

- **Modules 13–17.** This module refers back to them constantly: service calls and resilience (13), containers and probes (14), caches and Redis (15), brokers (16), and metrics and SLOs (17). Topics link to the exact topic where each idea was taught, so you can go back when something is unfamiliar.
- **No Docker.** Every demo is plain Java and JUnit (topic 05 uses Spring Boot's MockMvc for HTTP caching headers), run with `mvn -q test`.

## How this module is organized

Topics 01–03 are **the foundations**: estimating load and capacity (01), what scalable, available, and reliable mean and how to compute availability (02), and the stateless services that make horizontal scaling possible (03).

Topics 04–05 are **traffic**: spreading requests across instances (04), and serving them from caches near the user before they reach you (05).

Topics 06–08 are **data**: copying it with replication (06), splitting it with partitioning (07), and the consistency trade-offs both force on you (08).

Topic 09 is **the capstone**: a URL shortener designed end to end.

```text
01-thinking-about-scale                 <- vertical vs horizontal; estimates; Little's law and saturated pools
02-scalability-availability-reliability <- nines, series vs parallel availability, SPOFs, correlated failure
03-stateless-services                   <- sessions, files, counters, locks, jobs: what makes an instance disposable
04-load-balancing                       <- L4 vs L7; round-robin vs least-connections; sticky sessions; draining
05-cdn-and-edge-caching                 <- Cache-Control, ETags, Vary, fingerprinted assets; a private page cached publicly
06-replication-and-read-replicas        <- leader/follower, sync vs async; replica lag; read-your-writes
07-partitioning-and-sharding            <- shard keys, hot shards, hash % N vs consistent hashing
08-cap-and-consistency-models           <- CAP and PACELC; consistency models; quorums; last-write-wins
09-design-walkthrough                   <- a URL shortener from requirements to failure modes
```

```text
COMMON_MISTAKES.md       <- real design mistakes with the simulation that shows each
INTERVIEW_QNA.md         <- system design interview questions with the reasoning behind the answers
```

## How to run the examples

Each topic has its own Maven project under `CODE_EXAMPLES/NN-topic-name/`. The tests are the lesson:

```bash
cd 18-system-design-basics/CODE_EXAMPLES/07-partitioning-and-sharding/<demo-dir>
mvn -q test
```

The simulations use seeded random numbers and a simulated clock, so they give the same result every run and finish in seconds. Change the parameters in a test (the number of replicas, the replica lag, the slow instance's latency) and re-run it: that's the fastest way to build intuition.

## Checkpoint

You're done with this module, and with the roadmap, when you can:

1. Estimate peak requests per second and five-year storage for a service from its daily users, and say how many instances you'd start with and why.
2. Explain why two 99.9% components in series are less available than either one, and why two replicas on the same power supply aren't 99.9999%.
3. Name five kinds of state hiding in a typical Spring Boot service, and where each should move.
4. Explain why round-robin is a bad choice when one instance is slow, and what sticky sessions cost after a scale-out.
5. Write the `Cache-Control` header for a fingerprinted JavaScript file, a public product page, and a user's account page.
6. Explain why a user can update their profile and immediately see the old one, and two ways to fix it.
7. Explain why adding one shard to a `hash % N` scheme moves most keys, and how consistent hashing avoids it.
8. Explain what a system must give up during a network partition, and why W + R > N matters for quorum reads.
9. Present a URL shortener design in 20 minutes: requirements, estimates, API, code generation, storage, caching, scaling, and what happens when Redis or the database goes down.

That's the end of the roadmap. From here, the fastest way to grow is to build something real with these modules and read postmortems of systems that failed.

## A note on what's *not* here

- **No cloud architecture.** Regions, availability zones, and managed load balancers, databases, and CDNs appear only as concepts. Vendor-specific design belongs in a cloud roadmap.
- **No famous-company architecture tours.** "How X built their feed" articles are useful once the basics here are solid. They're stories about trade-offs, and this module teaches the trade-offs.
- **No consensus algorithms in depth.** Raft and Paxos are named in topic 08, not implemented. Using a system that runs them is a backend skill; building one isn't.
- **No big-data processing.** Batch and stream processing frameworks, data lakes, and warehouses are a data-engineering subject.
