# Why Microservices — and When Not To

A microservice architecture splits one application into many small services, each with its own codebase, its own database, and its own deployment. This topic exists to answer the question everyone skips: *should you?* Because microservices are not a better version of a monolith — they are a different trade, one that solves organizational and scaling problems by taking on a much harder operational and correctness problem. Most systems that adopt them do not need them.

There is no code in this topic. The rest of the module is all code — timeouts, circuit breakers, sagas — and none of it will make sense until you know what problem the split was supposed to solve, and what it costs when you make the split for the wrong reason.

---

## The problem this solves

Start with the thing that works: a **monolith**. One Spring Boot app, one database, one deployable jar. Orders, inventory, users, payments — all packages inside one process. This is not an insult. A monolith is the correct default, and a well-structured one runs enormous companies for years.

The monolith starts to hurt in specific, nameable ways — and *only* these ways justify splitting it:

1. **The team got too big for one codebase.** Fifty engineers merging into one repo, one deploy pipeline, one release train. Every deploy is a negotiation. One team's risky change blocks everyone else's safe change. This is the real, original reason microservices exist — it's an *organizational* problem, not a technical one. Small teams share a codebase fine.

2. **One part needs to scale independently of the rest.** Your image-processing does 100× the CPU work of everything else. In a monolith you scale the *whole app* — every replica carries the user CRUD and the reporting code it doesn't need — just to get more image-processing capacity. Splitting image-processing into its own service lets you scale that one thing.

3. **One part needs a different technology or a different failure boundary.** The reporting subsystem should be able to fall over without taking checkout down. Or one component genuinely needs a different language/runtime. In a monolith, one part crashing (an `OutOfMemoryError`, a runaway thread) takes the whole process with it.

4. **Independent deployment cadence.** The payments team must ship on a slow, audited, compliance-gated schedule; the marketing-banner team wants to ship ten times a day. One deployable forces both onto the slowest cadence.

Notice what's *not* on that list: "microservices are more modern," "it's best practice," "it'll be cleaner," "we want to use [technology]." Those are the reasons most splits actually happen, and they are the reasons most splits fail.

## What the split actually costs

Here is the honest ledger. Everything a monolith gave you for free, you now pay for:

| In a monolith | After you split |
|---|---|
| A method call — nanoseconds, can't fail on its own | A network call — milliseconds at best, can hang, time out, or half-succeed |
| One database, ACID transactions across everything | Many databases; **no transaction can span them** (topics 18–19) |
| One stack trace when something breaks | A failure in service D shows up as a timeout in service A, three hops away (topic 17) |
| Refactor across modules with one IDE rename | Change an API and you might break a caller you don't even know about (topics 06, 21) |
| Run it: `java -jar app.jar` | Run *all of them*, in the right order, with discovery/config/gateway wired up (topics 13–15) |
| "Is the app up?" — yes or no | "Is the system up?" — it's always *partially* down somewhere (topic 02) |
| One log file | Logs scattered across N services; you need a correlation id to reassemble one request (topic 17) |
| Deploy = one artifact | Deploy = N artifacts, each versioned, each independently rollback-able — and now version skew is real |

Every single topic in the rest of this module exists to buy back *one row of that table*. That's the frame: microservices don't give you new powers, they give you back — at real cost, with real code — the guarantees a monolith handed you for free. You take that deal only when the four benefits above are worth this bill.

## The distributed monolith — the trap

The most common outcome of a microservices migration is not microservices. It's a **distributed monolith**: you paid the entire cost column above and got *none* of the benefits. It looks like this:

- The services can't be deployed independently — releasing `order-service` requires releasing `inventory-service` at the same time because their contracts are entangled. (You didn't get independent deployment.)
- The services share one database. (You didn't get independent data or independent scaling; see topic 04.)
- Every user action fans out into a chain of synchronous calls, A→B→C→D, and if any hop is down the whole action fails — *more* fragile than the monolith, where those were method calls that couldn't fail. (You didn't get a failure boundary; you added failure surface.)
- A single logical change touches five repos and five deploys. (You made refactoring *harder*, not the team more independent.)

The tell is simple: **if you can't deploy one service without deploying another, you don't have microservices — you have a monolith that also has to deal with the network.** All the cost, none of the independence. This is worse than the monolith you started with. Topics 03 (boundaries), 04 (data ownership), and 06 (contracts) are the three things that, done wrong, produce a distributed monolith — which is why they come early.

## How to actually decide

A practical decision procedure, in order:

1. **Default to a monolith.** Especially early, when you don't yet know where the real boundaries are. You cannot correctly split a domain you don't understand yet, and a wrong split is far more expensive to undo than a wrong package boundary.
2. **Make it a *modular* monolith first.** Enforce clean module boundaries *inside* the one deployable — separate packages, no reaching into another module's tables, communicate through interfaces. This gets you most of the organizational clarity with none of the network. And it's the honest dress rehearsal: if you can't keep the boundaries clean in one process, splitting into services won't magically fix it — it'll just make every boundary violation a network call.
3. **Split only along a boundary that's already hurting**, and only for one of the four reasons above. Extract the one service that needs independent scaling / deployment / failure isolation. Leave the rest as the monolith. Most successful "microservices" architectures are one big service and a few small extracted ones — not fifty equal pieces.
4. **Ask the three questions** before any split:
   - *Can these two pieces be deployed independently?* If no, you'll build a distributed monolith.
   - *Can they own their data separately?* If they can't stop sharing tables, they aren't separate services (topic 04).
   - *Is a team boundary or a scaling/failure boundary actually forcing this?* If the only answer is "it feels cleaner," stop.

## When to use it / when not to

**Reach for microservices when:**
- Multiple teams are stepping on each other in one codebase / one deploy pipeline.
- One component's scaling profile is wildly different from the rest.
- A component needs an independent failure boundary (it can degrade without taking the core down) or an independent, faster/slower deploy cadence.
- The domain is well-understood enough that you know where the boundaries actually are.

**Stay a monolith (or modular monolith) when:**
- You have a small team (say, under ~15 engineers). The coordination problem microservices solve barely exists yet.
- The product is young and the domain is still shifting. Boundaries you draw now will be wrong, and moving a boundary between services is far costlier than moving one between packages.
- You're doing it for résumé/architecture-fashion reasons. "Everyone uses microservices" is how you end up operating a distributed system to run a CRUD app three people use.
- You can't yet answer the three questions above with a confident "yes."

The senior move is often to *not* split — or to split exactly one service off and stop. Knowing when not to is the harder, more valuable skill, and it's the one this topic is here to build.

## Common pitfalls

- **Splitting by technical layer instead of business capability.** A "controller service," a "service-layer service," and a "repository service" is not microservices — it's a monolith sliced the wrong way, where every request traverses all three. Split by *capability* (orders, inventory, payments), not by *tier*. (Topic 03.)
- **"Microservices will fix our messy code."** It won't. A tangled monolith becomes a tangled distributed system, which is strictly harder to untangle because now the tangles are over the network. Clean it up *as* a monolith first.
- **Nanoservices.** Splitting so finely that a single user action requires ten network hops. Each hop adds latency and a new way to fail. Services should be small, not tiny; a service that can't do anything useful without calling three others isn't a service, it's a distributed method.
- **Ignoring the operational bill.** Microservices need discovery, a gateway, centralized config, distributed tracing, and per-service CI/CD *before* they're workable (topics 13–17). A team that splits without this infrastructure ships a distributed monolith they can't debug.

---

## Try this yourself

No code — reason it through and write your answers down:

1. Take a system you know (a real one at work, or a familiar app like a food-delivery service). List its major capabilities (users, restaurants, orders, delivery, payments). For each pair, ask: *do these need to deploy independently, and can they own their data separately?* Circle the one or two boundaries where the answer is clearly "yes." Those — and only those — are extraction candidates.
2. Find the fastest-changing part and the most-scaled part of that system. Are they the same component? If a monolith forced them onto the same deploy cadence and the same scaling unit, write one sentence describing the pain that would cause. That sentence is your justification — or, if you can't write it convincingly, your reason to stay a monolith.

## Self-check

1. Name the four legitimate reasons to adopt microservices, and explain why "it's cleaner / more modern" is not one of them.
2. What is a distributed monolith, and what is the single clearest tell that you've built one?
3. Your team of six is building a new product whose domain is still changing weekly. A senior engineer proposes starting with eight microservices. Give the argument for staying a (modular) monolith instead.
