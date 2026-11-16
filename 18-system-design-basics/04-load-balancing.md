# Load Balancing

A load balancer sits in front of several instances of a service and decides which one gets each request. It's what makes "add more servers" actually work. Module 13 topic 13 did this inside the caller (client-side, with Spring Cloud LoadBalancer). This topic is the other common shape: a separate balancer (nginx, HAProxy, Envoy, a Kubernetes Service, or a managed cloud load balancer) that every client talks to. The demo simulates it in plain Java, so you can see what each algorithm does to latency when one server goes bad, and what sticky sessions and long-lived connections do to a scale-out.

---

## The problem this solves

You ran the numbers in topic 01 and one instance isn't enough, so you run four. Now three questions need answers:

- **Which instance gets this request?** Spread evenly, or by how busy each one is?
- **What happens when an instance is slow or dead?** A balancer that keeps sending traffic to a sick server turns one bad machine into slow responses for a quarter of your users.
- **What happens when you add or remove an instance?** Scaling out only helps if the new instance actually receives work, and scaling in or deploying shouldn't cut off requests in progress.

```text
                         +--> instance A
clients --> load balancer +--> instance B
                         +--> instance C
                         +--> instance D
```

## How it works

### Layer 4 vs layer 7

The two kinds of balancer differ in how much of the traffic they understand. The names come from the network layers: 4 is TCP, 7 is the application protocol (HTTP).

| | Layer 4 (TCP) | Layer 7 (HTTP) |
|---|---|---|
| Sees | IP addresses and ports | the whole request: method, path, headers, cookies |
| Decides | once per **connection** | once per **request** |
| Can route by path (`/api` vs `/images`), header, or cookie | no | yes |
| Terminates TLS (decrypts HTTPS) | no, it passes encrypted bytes through | usually yes, so it can read the request |
| Can retry a failed request on another instance | no | yes, for idempotent requests |
| Cost | very cheap, very fast | more CPU per request |

A Kubernetes `Service` (module 14 topic 12) is layer 4. An Ingress controller, nginx, Envoy, or Spring Cloud Gateway (module 13 topic 14) is layer 7. The difference matters most for long-lived connections (below).

### The algorithms

`Strategy.java` implements the common ones. Each is told how many requests every server is still working on (`outstanding`), and picks one:

```java
static Strategy roundRobin() {
    return (outstanding, n) -> (int) (n % outstanding.length);     // 0, 1, 2, 3, 0, 1, ...
}

static Strategy leastConnections() {
    return (outstanding, n) -> {                                   // the server with the fewest in flight
        int best = 0;
        for (int i = 1; i < outstanding.length; i++) {
            if (outstanding[i] < outstanding[best]) best = i;
        }
        return best;
    };
}

static Strategy powerOfTwoChoices(long seed) {                     // two at random, take the less busy
    Random random = new Random(seed);
    return (outstanding, n) -> {
        int a = random.nextInt(outstanding.length);
        int b = random.nextInt(outstanding.length - 1);
        if (b >= a) b++;
        return outstanding[b] < outstanding[a] ? b : a;
    };
}
```

- **Round-robin**: each server in turn. Simple and fair when every server and every request is the same.
- **Weighted round-robin**: round-robin where a bigger server gets more turns, for example weights 2:1 for a machine with twice the cores.
- **Least connections**: the server with the fewest requests in progress. A slow server finishes fewer requests, so it accumulates in-flight work and gets picked less. It adapts on its own.
- **Least response time**: like least connections, also weighting by recent response times.
- **Power of two choices**: pick two servers at random, use the less busy one. Almost as good as least connections, and the balancer doesn't need an exact, up-to-date count for *every* server. That matters when there are many balancers, each seeing only part of the traffic.
- **Hashing** (by client IP, user id, or URL): the same key always goes to the same server. Useful when a server caches per-key data. Plain `hash % servers` moves almost every key when a server is added; consistent hashing moves only a few ([topic 07](07-partitioning-and-sharding.md)).

### The simulation

`LoadBalancerSimulation.run(...)` is a discrete-event simulation: time is a number the program advances, nothing sleeps, and the same input always gives the same result. A request arrives every 4 ms (250 per second) for 40 seconds, 10,000 requests in all. Each of the 4 servers handles one request at a time, in arrival order. The result is how many requests each server got and the latency percentiles (module 17 topic 04 explains why p99 matters more than an average).

With 4 healthy servers at 10 ms per request, round-robin is perfect: 2,500 requests each, p99 = 10 ms (`SlowServerTest.allHealthy_roundRobinIsPerfect`).

## Broken: round-robin and one slow server

Now server 0 gets sick: 100 ms per request instead of 10 ms. A failing disk, a noisy neighbour on the same host, long garbage-collection pauses. It isn't down, so health checks still pass. The fleet can still handle 3 × 100 + 10 = 310 requests per second, more than the 250 arriving.

`roundRobin_keepsFeedingTheSlowServer_andItsQueueGrowsForever`:

| | per server | p50 | p99 | max |
|---|---|---|---|---|
| round-robin, one slow server | 2,500 / 2,500 / 2,500 / 2,500 | 10 ms | **201,700 ms** | 210,016 ms |

Round-robin sends the slow server a quarter of the traffic, 62.5 requests a second, and it can finish 10. Its queue grows for the whole run, and the last requests wait three and a half minutes. The median still says 10 ms. A dashboard showing only the average or the median looks fine while one user in four is waiting longer and longer.

## Fixed: let the balancer see how busy each server is

Same servers, same traffic (`leastConnections_...` and `powerOfTwoChoices_...`):

| | requests to the slow server | p50 | p99 | max |
|---|---|---|---|---|
| least connections | 400 | 10 ms | **100 ms** | 100 ms |
| power of two choices | 402 | 12 ms | **268 ms** | 384 ms |

With least connections, the slow server only ever got a request when it had nothing in progress, so no request waited behind another: the worst case is exactly one slow request (100 ms). Power of two choices sometimes compares the slow server with an idle fast one, both at zero, and the tie can pick the slow one. A few requests wait behind it, so p99 is 268 ms. That's a few hundred milliseconds instead of minutes, with far less information.

Weighted round-robin can also protect the slow server, if you know its speed in advance. Weights 1:10:10:10 send it 323 requests, about 8 a second, under its 10, and p99 is 100 ms. But weights don't react: if that server degrades further to 200 ms, the same weights give a p99 of 17,148 ms (`weightedRoundRobin_splitsByWeight_butStillIgnoresHowBusyAServerIs`).

The deeper fix is to notice the slow server and take it out. That is what health checks are for (below), and what outlier detection does in Envoy-style proxies: eject a server whose error rate or latency is far worse than its peers'.

## Broken: sticky sessions

Sticky sessions (session affinity) make the balancer send every request from one user to the same instance, usually with a cookie it sets on the first response. Teams turn it on when sessions live in each instance's memory, because otherwise the next request lands on an instance that has never heard of the user ([topic 03](03-stateless-services.md)). It works until you scale.

`StickySessionsTest`, with 1,000 users logged in across instances a and b:

- **Scale-out does nothing for existing users.** Add instance c because a and b are overloaded, and the next 10,000 requests from those users split a = 5,000, b = 5,000, **c = 0** (`afterAScaleOut_theNewInstanceGetsNoneOfTheExistingUsers`). Only brand-new users reach c: 100 of the next 300 (`onlyNewUsersReachTheNewInstance`). The overloaded instances stay overloaded for as long as those sessions last.
- **An instance dying logs its users out.** With 900 users on 3 instances, killing one loses 300 sessions (`whenAnInstanceDies_everySessionOnItIsLost`). A deploy that restarts instances one at a time logs everyone out, a third at a time.

The fix isn't a smarter balancer. It's making instances interchangeable: move the session to a shared store (Redis, module 15 topic 12) or use stateless tokens (module 12 topic 08). Then any algorithm above works, and stickiness becomes an optional cache-locality optimisation instead of a correctness requirement.

## Broken: long-lived connections behind a layer 4 balancer

HTTP keep-alive, connection pools, gRPC, and WebSockets reuse one TCP connection for many requests. A layer 4 balancer chooses an instance when the connection opens and never again.

`LongLivedConnectionsTest`: 100 clients each send 1,000 requests. After their 100th request, the pool grows from 2 to 4 instances. Requests after the scale-out:

| | instance 1 | instance 2 | instance 3 (new) | instance 4 (new) |
|---|---|---|---|---|
| layer 4, connections never closed | 45,000 | 45,000 | **0** | **0** |
| layer 4, reconnect every 100 requests | 22,500 | 22,500 | 22,500 | 22,500 |
| layer 7, per request | 22,500 | 22,500 | 22,500 | 22,500 |

The new instances run, pass their health checks, and receive nothing, because every client is still on its old connection. Fixes: a layer 7 balancer that balances each request (for gRPC this is the usual answer, since it multiplexes everything over one connection), or a limit on connection lifetime so clients reconnect and get rebalanced (a maximum connection age or requests-per-connection on the server or proxy).

## Health checks, draining, and the balancer itself

- **Health checks.** The balancer polls each instance and stops sending traffic to one that fails. In Spring Boot that is the readiness endpoint, which also turns `REFUSING_TRAFFIC` during startup and shutdown (module 17 topic 08). Readiness catches dead and unready instances, but not slow ones like server 0 above, which is why the algorithm still matters.
- **Connection draining on deploy.** Before an instance stops, the balancer must stop sending it new requests and let the in-flight ones finish. Spring Boot's graceful shutdown handles the instance side (module 14 topic 09); the balancer's deregistration delay or drain timeout handles its side. Without both, every deploy fails a few requests.
- **The balancer is a single point of failure.** If it's one box and it dies, every instance behind it is unreachable. Production balancers run as a redundant pair or cluster, often sharing one virtual IP, with DNS or anycast spreading clients across them ([topic 02](02-scalability-availability-reliability.md)).

## When to use it / when not to

- **Use a server-side (proxy) balancer** for traffic from outside (browsers, mobile apps, partners), where clients can't run your balancing code, and wherever you need TLS termination, path routing, or one entry point.
- **Use client-side balancing** (module 13 topic 13) for service-to-service calls inside your system when you want to skip the extra hop. A Kubernetes Service does a simple version for you.
- **Default to least connections or power of two choices** over round-robin when request times vary or servers can degrade. Round-robin is fine when every request costs the same and servers are identical.
- **Avoid sticky sessions as a requirement.** Fix the statefulness instead.

## Common pitfalls

- **Round-robin with uneven requests or uneven servers.** One slow server gets the same share and its queue grows without limit. Use least connections or power of two choices.
- **Sticky sessions with in-memory state.** Scale-outs don't help existing users and every restart logs people out. Externalise the session.
- **Long-lived connections behind layer 4.** New instances stay idle after a scale-out. Balance per request at layer 7, or cap connection age.
- **Judging the balancer by average latency.** The average hides the one slow server. Watch p99 per instance.

---

## Code examples

`CODE_EXAMPLES/04-load-balancing/load-balancing-demo/`. Plain Java, no Spring, no Docker. Run with `mvn -q test`; the tests print the numbers in the tables above. Read in this order:

1. `src/main/java/com/example/lb/Strategy.java`: round-robin, weighted round-robin, least connections, power of two choices.
2. `src/main/java/com/example/lb/LoadBalancerSimulation.java`: the discrete-event simulation.
3. `src/test/java/com/example/lb/SlowServerTest.java`: one slow server under each strategy.
4. `src/main/java/com/example/lb/StickySessions.java` and `src/test/java/com/example/lb/StickySessionsTest.java`: scale-out and instance failure with sticky sessions.
5. `src/main/java/com/example/lb/ConnectionBalancing.java` and `src/test/java/com/example/lb/LongLivedConnectionsTest.java`: layer 4 vs layer 7 with long-lived connections.

---

## Try this yourself

1. In `SlowServerTest`, move the slow server from index 0 to index 3 for least connections. It now receives 0 requests and p99 drops to 10 ms. Why? (Look at how ties are broken, and how often the three fast servers are all busy at once.) What would a real balancer, which breaks ties randomly, do?
2. Change `ARRIVAL_EVERY` to 3,000 µs: 333 requests a second, more than the fleet's 310. Least connections' p99 rises to about 14 seconds too. What does that tell you about what a load balancer can and can't fix?

## Self-check

1. Four servers, one of them 10× slower but passing health checks. Explain what round-robin does to p50 and p99, and why least connections avoids it.
2. You add a fifth instance because the other four are at 90% CPU, and it receives no traffic. Name two different causes, and the fix for each.
3. What can a layer 7 balancer do that a layer 4 balancer can't, and what does it cost?
