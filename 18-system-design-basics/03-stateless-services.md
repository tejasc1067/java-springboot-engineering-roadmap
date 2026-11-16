# Stateless Services

Horizontal scaling (topic 01) and redundancy (topic 02) both assume one thing: any instance can handle any request, and any instance can disappear without anyone noticing. An instance like that is **stateless**: it keeps nothing between requests that another instance would need. Earlier modules fixed one kind of instance state at a time. This topic puts them side by side, because they're one problem with one fix: keep the state somewhere every instance shares, so each instance is disposable.

---

## The problem this solves

A shop runs one instance. It works. Traffic grows, so the team adds a second instance behind a round-robin load balancer, and three bug reports arrive the same day:

- "My cart keeps emptying." It holds alternately one item or the other.
- "My profile picture is broken every other time I reload."
- "The account lockout doesn't work." Security testing got 9 password guesses before being locked out, not 3.

Nothing in the code changed. All three bugs were there from the start, hidden because one instance held all the state.

## How it works

### What counts as state in an instance

Anything an instance remembers from one request that a later request depends on:

| State kept in the instance | What breaks with two instances | Where it was covered |
|---|---|---|
| `HttpSession` in memory (logins, carts) | user logged out or cart lost on the other instance | module 15 topic 12 |
| Local cache (Caffeine, a `HashMap`) | instances disagree after an update | module 15 topic 05 |
| Files written to local disk (uploads, generated reports) | file missing on the other instance; gone when the container is replaced | module 14 topic 07 |
| In-memory counters (rate limits, lockouts) | the limit is multiplied by the instance count | module 15 topic 10 |
| `synchronized` and in-process locks | each instance locks only itself | module 15 topic 09 |
| `@Scheduled` jobs | every instance runs the job | module 08 topic 13 |

### Broken: state in each instance

`CartService` stores carts in a map it's given. Give each instance its own map, and you have the classic in-memory session (`InMemorySessionTest`):

```java
CartService a = new CartService("a", new HashMap<>());
CartService b = new CartService("b", new HashMap<>());
LoadBalancer<CartService> balancer = LoadBalancer.roundRobin(List.of(a, b));

balancer.pick("ann").add("ann", "book");   // instance a
balancer.pick("ann").add("ann", "lamp");   // instance b

balancer.pick("ann").cart("ann");          // a: [book]
balancer.pick("ann").cart("ann");          // b: [lamp]
```

The same shape appears with files and counters (`LocalStateTest`):

- An avatar saved to instance A's disk: instance B answers "not found" for it, so every other page load shows a broken image.
- A lockout after 3 wrong passwords, counted per instance: with 3 instances an attacker gets **9** guesses before every instance has locked the account. Scaling out weakened a security control.

### Fixed: externalize the state

Give every instance the *same* store. In the demo that's one shared map or one shared directory, standing in for the real thing:

```java
var shared = new ConcurrentHashMap<String, List<String>>();   // stand-in for Redis
LoadBalancer<CartService> balancer = LoadBalancer.roundRobin(
        List.of(new CartService("a", shared), new CartService("b", shared)));

balancer.pick("ann").add("ann", "book");
balancer.pick("ann").add("ann", "lamp");
balancer.pick("ann").cart("ann");          // [book, lamp], from either instance
```

In a real system, each kind of state has its usual home:

| State | Shared home |
|---|---|
| Sessions | Redis via Spring Session (module 15 topic 12), or no session at all: a signed token on each request (module 12 topic 08) |
| Caches | a shared Redis cache (module 15 topic 07), or local caches with short TTLs and invalidation messages (module 15 topic 11) |
| Files | object storage (a service that stores files over HTTP, which every major platform offers) or a database; never the container's disk |
| Counters and rate limits | Redis `INCR` / Lua (module 15 topic 10) |
| Locks | a database constraint or row lock, or a Redis lock with fencing (module 15 topic 09) |
| Scheduled jobs | a lock row so one instance runs each job (ShedLock, module 08 topic 13), or a message queue (module 16) |

`withASharedStore_instancesAreDisposable` is the payoff: both instances are replaced by new ones (a deploy), and the cart is still there. Instances become cattle, not pets: you can add them when load rises, remove them when it falls, restart them when they're sick (module 17 topic 08), and roll out new versions one at a time, without anyone losing anything.

The cost is real: every session read is now a network call (about half a millisecond to Redis, topic 01), and the shared store is a new component that must itself be available (topic 02). That's almost always worth it.

### Sticky sessions: a stopgap

A load balancer can pin each user to one instance, usually by a cookie or a hash of the session id. The in-memory cart then appears to work (`stickySessions_hideTheProblem_whileNothingChanges`). But the state is still in one instance, so it goes away with that instance. In `stickySessions_loseEveryCartOnAnInstanceThatGoesAway`, 1,000 users are spread over two instances; one instance is removed (a deploy, a crash, a scale-down), and **500 carts** are gone.

Sticky sessions also spread load unevenly (one instance can end up with the heavy users), and they make every deploy log out a share of your users. Use them only to buy time while moving the state out, or for state that's cheap to lose. Topic 04 shows how load balancers implement them.

## When to use it / when not to

Make every service instance stateless as soon as it runs more than one copy, which in production is always (topic 02: one copy is a single point of failure). Containers (module 14) and Kubernetes assume it: they replace instances freely.

State that's *meant* to be per instance is fine when losing it is harmless: a small local cache with a short TTL, a connection pool, a warmed-up JIT. The test is: "if this instance vanished right now, would any user or any other instance notice?"

## Common pitfalls

- **"We only run one instance."** Then a deploy or a crash is downtime, and the day you add a second, these bugs appear all at once. Design stateless from the start.
- **Sticky sessions as the fix.** They hide the problem until the next deploy or scale-down, then lose a share of users' state. Move the state out.
- **Writing to local disk in a container.** The container's filesystem disappears with it (module 14 topic 07). Use object storage or a database, and log to stdout (module 17).
- **Forgetting the less obvious state.** Rate limits, lockouts, `synchronized` blocks, and `@Scheduled` jobs are state too. Review each one when you add an instance.

---

## Code examples

`CODE_EXAMPLES/03-stateless-services/stateless-demo/`. Plain Java, no Spring, no Docker. Run with `mvn -q test`. Read in this order:

1. `src/main/java/com/example/stateless/CartService.java` — a cart service whose storage is passed in: one map per instance, or one map for all.
2. `src/main/java/com/example/stateless/LoadBalancer.java` — round-robin and sticky (hash of the session id).
3. `src/test/java/com/example/stateless/InMemorySessionTest.java` — the lost cart, the shared store, and disposable instances.
4. `src/test/java/com/example/stateless/StickySessionTest.java` — sticky sessions working, then losing 500 of 1,000 carts when an instance goes.
5. `src/main/java/com/example/stateless/UploadService.java` and `LoginGuard.java` — two more kinds of instance state.
6. `src/test/java/com/example/stateless/LocalStateTest.java` — the missing upload and the lockout that allows 9 guesses, each with its fix.

---

## Try this yourself

1. Add a third instance to `stickySessions_loseEveryCartOnAnInstanceThatGoesAway` (three instances, then remove one). How many carts are lost now, and why are some users on the *surviving* instances affected too? (Hint: `hashCode % size` changes for everyone when `size` changes. Topic 07 shows consistent hashing, which fixes this.)
2. Write a `ScheduledReport` class that "sends" a daily report from each instance, and a test showing three instances send three reports. Then fix it with a shared "claimed" set, like module 08 topic 13's `JobClaims`.

## Self-check

1. List four kinds of state a Spring Boot instance can hold besides the HTTP session, and where each should live instead.
2. Why does sticky routing make the in-memory cart *seem* to work, and what event makes it fail?
3. A rate limit of 100 requests a minute is enforced in memory. The service scales from 2 to 6 instances. What's the effective limit now, and how do you fix it?
