# Thinking About Scale

"Will it scale?" is a question with a number in it. This topic is about getting that number before you build anything: how many requests a second, how much data a year, how many instances, how big each pool. The arithmetic is simple. The skill is writing down your assumptions so you, and anyone reviewing your design, can see which ones are guesses.

It also covers the two ways to grow a system (a bigger machine, or more machines) and the one thing that decides which you can use: where the system keeps its state.

---

## The problem this solves

A team designs a URL shortener. One engineer wants Kafka, a sharded database cluster, and a cache in every region. Another says one server is enough. Neither has a number. Twenty minutes of estimation settles it: the write load is about 12 requests a second, the data is under a terabyte after five years, and the read load fits on a handful of instances. One database, a few app instances, and a cache are enough, and the team can spend its effort elsewhere.

The opposite mistake is just as common: a service sized for the average load falls over every evening at peak, or a thread pool sized "by feel" runs out the first time the database slows down.

## How it works

### Vertical vs horizontal

- **Vertical scaling (scale up):** a bigger machine. More CPU, more memory, faster disks. Nothing in the code changes. Limits: machines only get so big, the price rises faster than the size, and it's still one machine, so when it fails or restarts, everything stops.
- **Horizontal scaling (scale out):** more machines running the same code behind a load balancer (topic 04). No ceiling in principle, and one machine failing takes out only its share. The cost: the instances must be interchangeable, which means no instance may keep anything another one needs. That's what **stateless** means (topic 03).

Application instances usually scale out easily, once they're stateless. Databases are the hard part, because they *are* the state: they scale up first, then out with read replicas (topic 06) and sharding (topic 07). That's why most scaling conversations end up being about data.

### Back-of-envelope estimation

The method, applied to the URL shortener in `UrlShortenerEstimateTest`:

**1. Write down the assumptions.**

| Assumption | Value |
|---|---|
| Daily active users | 10,000,000 |
| Short links created per user | 1 every 10 days → 1,000,000 a day |
| Redirects (clicks) per user | 10 a day → 100,000,000 a day |
| Size of a stored link | about 500 bytes (URL, code, owner, timestamps, index overhead) |
| Size of a redirect response | about 500 bytes |
| Peak vs average traffic | 3x |

**2. Turn "per day" into "per second".** A day has 86,400 seconds; round to 100,000 in your head.

```java
Estimate.perSecond(1_000_000);      // 11.6 writes/s
Estimate.perSecond(100_000_000);    // 1,157 reads/s
Estimate.peak(1_157, 3);            // 3,472 reads/s at peak
```

Reads outnumber writes 100 to 1. That ratio is the most useful number on the page: it says caching (module 15) and read replicas (topic 06) will pay off, and that the write path barely matters for load.

**3. Storage and bandwidth.**

```java
Estimate.bytesPerYear(1_000_000, 500);    // 182.5 GB a year, under 1 TB in five years
Estimate.bytesPerSecond(3_472, 500);      // 1.74 MB/s at peak
```

Under a terabyte in five years fits on one database server with room to spare. No sharding needed.

**4. Instances.** Load-test one instance to learn what it can do (say 1,000 redirects a second), then don't plan to run it at 100%:

```java
Estimate.instances(3_472, 1_000, 0.7, 1);   // 6: run at 70%, plus one spare
Estimate.instances(3_472, 1_000, 1.0, 0);   // 4: only if nothing ever spikes or fails
```

The spare (N+1) means one instance can crash or be redeployed without pushing the others over their limit.

### Little's law: sizing pools

How many requests are inside a system at once? **Little's law**: in-flight = arrival rate × time each one spends inside. It holds for any stable system: a shop, a thread pool, a connection pool.

```java
// One instance at 700 requests/s, 20 ms each, of which 5 ms holds a database connection:
Estimate.inFlight(700, 0.020);   // 14 requests in flight -> about 14 busy threads
Estimate.inFlight(700, 0.005);   // 3.5 connections busy

// The database slows to 200 ms per query; the request now takes 215 ms:
Estimate.inFlight(700, 0.215);   // 150.5 busy threads. Tomcat's default maximum is 200.
```

This is why a slow dependency exhausts thread pools (module 13 topic 09 on timeouts, topic 12 on bulkheads): nothing about the traffic changed, but each request stays ten times longer, so ten times as many are inside at once.

### What happens at the limit

A pool of N workers that each take S seconds per request can finish at most **N / S** requests a second. `WorkerPool` simulates exactly that: 10 workers, 50 ms per request, so a ceiling of 200 a second. Arrivals are random, as with real users, and time is simulated, so the 60-second runs take milliseconds. `SaturationTest` (seed 42):

| Arrivals per second | Busy | Throughput | Average wait | Longest wait |
|---|---|---|---|---|
| 100 | 50% | 100/s | 0.2 ms | 35 ms |
| 160 | 80% | 159/s | 4.7 ms | 56 ms |
| 190 | 95% | 188/s | 29 ms | 192 ms |
| 250 | 125% | **200/s** | 7.2 s | 14.4 s |
| 250, with 20 workers | 63% | 248/s | 0.1 ms | 19 ms |

Two lessons:

- **Waiting grows long before the pool is full.** At 80% busy, requests barely wait; at 95%, they wait six times longer. Random arrivals bunch up, and a nearly busy pool can't absorb the bunches. That's why the instance estimate above plans for 70%.
- **Past the ceiling, throughput stops growing and the queue never drains.** At 250 a second the pool still finishes 200, the other 50 a second pile up, and by the end of the minute a request waits 14 seconds for a 50 ms job. Real clients time out long before that, retry, and add even more load (module 13 topic 10). More workers raise the ceiling; nothing else does.

### Latency numbers worth knowing

Approximate orders of magnitude, enough to tell which design is plausible. Exact values depend on hardware.

| Operation | Roughly |
|---|---|
| Read from memory (a `HashMap` lookup) | under 1 microsecond |
| Round trip inside one data centre (a Redis `GET`) | about 0.5 ms |
| Read from an SSD | about 0.1 ms |
| Simple indexed database query, over the network | about 1 ms |
| Round trip between continents | about 100–200 ms |

The useful conclusions: memory is a thousand times faster than a network hop, so a request that makes 50 sequential database calls (an N+1 query, module 10) spends most of its time waiting on the network; and a user on another continent pays over 100 ms per round trip no matter how fast your code is, which is what CDNs exist for (topic 05).

## When to use it / when not to

Estimate before choosing architecture: before adding a cache, a queue, a second database, or sharding. Ten minutes of arithmetic often removes a component.

Don't treat the result as a forecast. It's an order of magnitude: the goal is "tens, thousands, or millions per second?", not "3,472". Then measure: load-test one instance (module 17 shows the metrics to watch), and revisit the numbers when real traffic arrives.

## Common pitfalls

- **Sizing for the average.** Traffic peaks at 2–5x the daily average. Size for the peak, with headroom.
- **Planning to run at 100%.** Queueing explodes near saturation. Aim for 60–70% busy at peak, plus a spare instance.
- **Forgetting the read/write split.** "10,000 requests a second" means very different systems at 99% reads and at 50% writes. Always estimate them separately.
- **Pool sizes from folklore.** Size thread and connection pools with Little's law from measured latency, and remember they also need to cover a slow dependency for as long as your timeout allows.

---

## Code examples

`CODE_EXAMPLES/01-thinking-about-scale/scale-demo/`. Plain Java, no Spring, no Docker. Run with `mvn -q test`. Read in this order:

1. `src/main/java/com/example/scale/Estimate.java` — the arithmetic, one line per method.
2. `src/test/java/com/example/scale/UrlShortenerEstimateTest.java` — the worked example: assumptions at the top, then requests, storage, bandwidth, instances, and pool sizes.
3. `src/main/java/com/example/scale/WorkerPool.java` — a discrete-event simulation of a fixed worker pool with random arrivals.
4. `src/test/java/com/example/scale/SaturationTest.java` — the pool below, near, and past its ceiling, then with more workers.

---

## Try this yourself

1. Redo the estimate for a photo-sharing app: 10M daily users, each uploading 1 photo a week (2 MB) and viewing 50 a day. Which number changes the design compared with the URL shortener? (Hint: look at storage and bandwidth, then read topic 05.)
2. In `SaturationTest`, change the service time to 100 ms and find the arrival rate where the average wait first exceeds 50 ms. How close to the new ceiling (100/s) is it?

## Self-check

1. A service has 5 million daily users making 20 requests each, and peak is 3x average. How many requests a second at peak? Show the steps.
2. Each request holds a database connection for 30 ms, and an instance serves 400 requests a second. How many connections does it use on average, and what happens to that number when queries slow to 300 ms?
3. Why can application instances usually scale horizontally while a database can't, and what must be true of an instance before you can add a second one?
