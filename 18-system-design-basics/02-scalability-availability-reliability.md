# Scalability, Availability, and Reliability

Three words that get used as if they meant "good", and that mean different things. A system can scale to millions of users and still be down every Sunday; it can be up 99.99% of the time and still return wrong answers. This topic pins down each word, puts numbers on availability, and shows the arithmetic that decides whether adding a second copy of something helps, or helps much less than the diagram suggests.

---

## The problem this solves

An architecture review says "we run two app instances, so we're highly available". Three questions break that claim open: what sits in front of both instances (one load balancer?), what sits behind them (one database?), and what can make both fail at once (the same bad deploy, the same overloaded dependency)? Without the arithmetic, "two of everything" feels safe. With it, you can see which single component caps the whole system and which redundancy buys almost nothing.

## How it works

### Four words

| Word | Question it answers | Example of failing it |
|---|---|---|
| **Scalable** | Can it handle more load by adding resources? | Doubling instances doesn't double throughput, because they all wait on one database (topic 01). |
| **Available** | Is it answering requests right now? | Returns 503 during a deploy. |
| **Reliable** | Does it give *correct* answers, consistently, over time? | Up all month, but charges some customers twice (module 13 topic 19). |
| **Durable** | Once it says "saved", does the data survive failures? | Acknowledged writes lost when a server dies before replicating (topic 06). |

Availability is the one people put numbers on, so it gets the rest of this topic.

### The nines

Availability is the fraction of time the system works. Each extra nine allows ten times less downtime. `NinesTest` computes the table:

| Availability | Downtime per year | Downtime per month |
|---|---|---|
| 99% ("two nines") | 87 h 36 min (3.65 days) | 7 h 18 min |
| 99.9% | 8 h 45 min | 43 min 48 s |
| 99.99% | 52 min 34 s | 4 min 23 s |
| 99.999% | 5 min 15 s | about 26 s |

(A month here is a twelfth of a year, the convention behind most published tables.) At 99.9%, one bad deploy that takes 45 minutes to roll back spends the whole month's allowance. At 99.99%, a human can't even be paged and log in within the budget: it requires automated detection and rollback. Each nine costs much more than the last, so choose the target from what users need. Module 17 topic 09 turns it into an error budget and alerts.

### Availability from failure and repair times

```java
Availability.fromMtbfAndMttr(Duration.ofDays(30), Duration.ofHours(1));     // 99.86%
Availability.fromMtbfAndMttr(Duration.ofDays(30), Duration.ofMinutes(6));   // 99.986%
Availability.fromMtbfAndMttr(Duration.ofDays(300), Duration.ofHours(1));    // 99.986%
```

Availability = MTBF / (MTBF + MTTR): mean time between failures over that plus mean time to repair. Failing ten times less often and repairing ten times faster give the same result. Repair speed is usually the cheaper lever: alerts that fire fast (module 17), health checks that restart or remove a broken instance automatically (module 17 topic 08), and a deploy you can roll back in a minute.

### Components in series: multiply

A request that passes through a load balancer, an app instance, and a database needs *all three* to be up. Multiply their availabilities:

```java
Availability.series(0.9999, 0.999, 0.9995);   // 0.9984: lower than any one of them
```

Every dependency in the request path lowers the total. Ten services at 99.9% each, called one after another in one request, give 99.0%, which is 87 hours of downtime a year (`tenServicesAt999_inOneRequestPath_giveOnly99`). This is the arithmetic behind module 13's warning about long chains of synchronous calls, and behind timeouts and fallbacks (module 13 topics 09–11): a dependency that can fail without failing the request is no longer in series.

### Copies in parallel: multiply the downtime

If any one of several copies is enough, the system is down only when *all* are down at once. Multiply the chances of being down:

```java
Availability.parallel(0.99, 0.99);         // 1 - 0.01 x 0.01 = 0.9999
Availability.parallel(0.99, 0.99, 0.99);   // 0.999999
```

Two 99% instances behave like one 99.99% instance. That's the promise of redundancy. `FailureSimulation` checks both formulas by brute force: 2,000,000 simulated minutes, a dice roll per component per minute, counting the minutes the system worked. The simulated results match the formulas (`SeriesAndParallelTest`).

### Single points of failure

A **single point of failure (SPOF)** is any component whose failure alone stops the system. Redundancy in one layer can't beat a SPOF in another:

```java
// Two app instances (99% each) behind one load balancer and in front of one database (99.9% each):
Availability.series(0.999, Availability.parallel(0.99, 0.99), 0.999);   // 0.9979
```

The redundant app layer is at 99.99%, but the system is capped at 0.999 × 0.999 ≈ 99.8% by the two single components. Typical SPOFs in a small backend: the one load balancer, the one database primary (topic 06 covers failover), the one machine or data centre everything runs in, a single DNS provider, and the one engineer who knows how to restore backups.

### Redundancy only helps if failures are independent

The parallel formula assumes the copies fail independently: the dice are rolled separately. Real copies often share a cause: the same rack or power supply, the same bad deploy rolled to both, the same configuration error, the same overloaded database they both call. `CorrelatedFailureTest` runs two replicas that are each up 99% of the time, and changes only *why* they fail:

| Why each replica fails | Two replicas together |
|---|---|
| Only its own faults (independent) | 99.99% |
| Half shared cause, half its own faults | 99.5% |
| Only a shared cause | **99%: the second replica added nothing** |

The formula promised 99.99% in every row. So when you add redundancy, ask what the copies share, and spread them apart: different machines, racks, or zones; deploy to one copy first and watch it before the rest (a canary); don't let both depend on the same single component.

### Graceful degradation

Not every failure has to be total. When the recommendations service is down, the product page can show the product without recommendations instead of a 500 (module 13 topic 11's fallback). When the database is struggling, the app can serve slightly stale data from a cache (module 15). Degrading one feature takes that dependency out of the series calculation for the main request.

## When to use it / when not to

Use the series and parallel arithmetic whenever someone proposes a new dependency in the request path or claims "redundant, therefore available". It's quick and exposes the weakest link.

Don't over-trust the numbers. Published availabilities are targets, not measurements; failures cluster; and the biggest real-world causes of outages, bad deploys and configuration changes, hit every copy at once. Use the arithmetic to compare designs, and measured availability (module 17) to judge the result.

## Common pitfalls

- **Counting only the app layer.** Two instances behind one load balancer and one database are not "highly available". Find the SPOFs first.
- **Assuming independence.** Two replicas deployed together, configured together, and calling the same dependency fail together. Spread them and roll changes out gradually.
- **Long synchronous chains.** Each service called in series multiplies in its unavailability. Use timeouts, fallbacks, or asynchronous messaging (module 16) for anything the request can live without.
- **Chasing nines users don't need.** Each nine costs roughly ten times more. Pick the target from user needs and spend the effort on repair time first.

---

## Code examples

`CODE_EXAMPLES/02-scalability-availability-reliability/availability-demo/`. Plain Java, no Spring, no Docker. Run with `mvn -q test`. Read in this order:

1. `src/main/java/com/example/availability/Availability.java` — downtime from nines, series, parallel, MTBF/MTTR.
2. `src/test/java/com/example/availability/NinesTest.java` — the downtime table and the repair-time comparison.
3. `src/main/java/com/example/availability/FailureSimulation.java` — the Monte Carlo simulation (seeded, so results repeat).
4. `src/test/java/com/example/availability/SeriesAndParallelTest.java` — formulas checked against the simulation, and the SPOF cap.
5. `src/test/java/com/example/availability/CorrelatedFailureTest.java` — the same two replicas with independent, partly shared, and fully shared failure causes.

---

## Try this yourself

1. A checkout request calls the order service, which calls payment, inventory, and pricing in sequence, each at 99.9%. Compute the checkout's availability. Then make pricing optional with a fallback to a cached price and compute it again.
2. In `CorrelatedFailureTest`, find the split between shared and own faults at which two replicas reach 99.9%. What does that tell you about how much a shared cause you can tolerate?

## Self-check

1. Your service must be 99.9% available. How many minutes of downtime is that per month, and why does it matter how fast you can roll back a deploy?
2. A request goes through a load balancer (99.99%), two redundant app instances (99.5% each), and one database (99.9%). What's the system's availability, and which component would you improve first?
3. Two replicas are each up 99% of the time, but the system with both is still only up 99%. Give two real causes and how you'd remove them.
