# Service Boundaries and Decomposition

Topic 01 said split only along a boundary that's already hurting. This topic answers the question that leaves open: *where is the boundary?* Drawing it in the wrong place is the single most expensive mistake in this module — a bad boundary is what turns a monolith into a distributed monolith (topic 01), and moving a boundary after the fact means re-splitting data, rewriting contracts, and coordinating deploys across teams. Get this right and the rest of the module is mechanics; get it wrong and no amount of circuit breakers will save you.

No code here — this is a modeling skill. It's the last of the three framing topics; topic 04 turns the boundary you draw into two real services.

---

## The problem this solves

You've decided to split. You have one domain — an e-commerce backend with orders, inventory, customers, payments, shipping, pricing, reviews. Into how many services, and along which lines?

The tempting-but-wrong cuts:

- **By technical layer.** A "web service," a "business-logic service," a "data service." Now *every* request traverses all three over the network (fallacy 2, latency), no single team owns a feature end-to-end, and a change to one entity touches all three. This is a monolith sliced the wrong way — the worst of both worlds.
- **By entity/table.** An "orders service," a "line-items service," a "addresses service." These are so fine-grained that no service can do anything alone; placing one order means a dozen cross-service calls (nanoservices, topic 01). You've distributed your database schema, not your system.
- **By "it felt about the right size."** Boundaries drawn by gut, with no principle, drift and overlap, and two services end up both needing to write the same data.

The right cut follows the *business*, not the code. And the tool for finding it has a name.

## How it works: bounded contexts

The core idea comes from Domain-Driven Design: a **bounded context** is a boundary within which a model and its vocabulary are consistent, and *across* which the same word can mean something different. A service should map to one bounded context.

The classic tell is a word that means different things to different parts of the business. Take **"Customer"**:

- To **Sales**, a Customer is a lead: contact info, pipeline stage, likelihood to buy.
- To **Billing**, a Customer is a payment account: invoices, credit limit, tax id.
- To **Support**, a Customer is a ticket history: open issues, entitlements, satisfaction score.
- To **Shipping**, a Customer is barely a Customer at all — it's an *address* and a delivery preference.

In a monolith, teams fight to cram all of this into one `Customer` class with forty fields, and every team's change risks breaking every other team. That God-class is a *sign of a boundary you haven't drawn yet*. Split into contexts and each service has its own, smaller `Customer` model containing only what *that* context needs — linked by a shared id, not a shared table. The `order-service` and `inventory-service` you'll build both have a notion of "product," but the order side cares about price and name at time-of-purchase, and the inventory side cares about stock levels and warehouse location. Same word, two models, two services.

**The heuristic:** where the language changes, there's a boundary. Where one team says "status" and means order status, and another says "status" and means shipment status, you've found a seam.

## Split by business capability

The most reliable decomposition is **by business capability** — the things the business *does*, each owned end-to-end by one team:

```
Order Management   -> place, cancel, track orders
Inventory          -> stock levels, reservations, restocking
Payments           -> charge, refund, payment methods
Shipping           -> labels, carriers, delivery tracking
Catalog / Pricing  -> products, descriptions, prices, promotions
```

Each capability is cohesive (everything about inventory lives in one place) and loosely coupled to the others (inventory doesn't need to know how payments work). That's the goal of *any* modularization, distributed or not:

- **High cohesion** — things that change together live together. A change to how reservations work touches only `inventory-service`.
- **Low coupling** — a service depends on as few others as possible, and only through stable contracts. `order-service` asks `inventory-service` "is this in stock?" and doesn't care how it answers.

A good boundary means most feature changes fit inside *one* service. If a typical change requires editing three services and coordinating their deploys, the boundary is wrong — you've cut through the middle of a capability instead of around it.

## The acid test: can it own its data?

The sharpest, most practical test of a proposed boundary: **can this service own its data outright, with no other service reading or writing its tables?**

If `order-service` and `inventory-service` would need to share the `stock` table — one writing it, the other reading it directly — they are not two services. They're one service with two front doors and a shared, entangled heart. Every schema change now breaks two deploys; neither can be released independently; you've built a distributed monolith (this is exactly topic 04). When a service needs data another owns, it must *ask* (a call, topic 05) or *be told* (an event, topic 07) — never reach into the other's database.

So the boundary question and the data-ownership question are the same question. If you can't cleanly divide the data, you haven't found a real boundary yet.

## Coupling you can measure

Once you've sketched boundaries, sanity-check them against these smells — each means a boundary is in the wrong place:

- **Chatty boundaries.** One user action causes many back-and-forth calls between two services. They're too entangled to be separate; the data they pass around belongs on one side. (Latency, fallacy 2.)
- **Shared database / shared tables.** Covered above — the clearest sign two "services" are one.
- **Lockstep deploys.** You can never deploy service A without deploying service B. The contract between them is so tight they're effectively one unit (topic 06 is how you loosen this).
- **A change that always spans services.** If "add a field to an order" reliably means touching orders, line-items, and pricing services together, those were never separate capabilities.
- **Cyclic dependencies.** A calls B calls C calls A. Cycles mean the responsibilities are tangled; a clean decomposition is (mostly) a directed acyclic graph.

## Getting from a monolith to services: the strangler fig

You rarely draw boundaries on a blank page — you have a monolith and want to extract from it without a big-bang rewrite (which usually fails). The standard technique is the **strangler fig** (named after the vine that grows around a tree and gradually replaces it):

1. Put a facade/gateway in front of the monolith (topic 14).
2. Identify one capability with a clean boundary and a real reason to extract (a scaling or team pressure from topic 01).
3. Build it as a new service, move its data out, and route just that capability's traffic to the new service — everything else still hits the monolith.
4. Repeat, one capability at a time. The monolith shrinks; you're never in a state where nothing works.

This keeps you honest: you can only strangle off a piece that has a real boundary and can own its data. Pieces that resist extraction are telling you the boundary isn't there.

## When to use it / when not to

- **Draw boundaries by capability/bounded context**, verified by the data-ownership test, whenever you split. This is the only decomposition that survives contact with real feature work.
- **Don't decompose a domain you don't understand yet** (topic 01). Boundaries encode domain knowledge; if the domain is still shifting, keep it a modular monolith and let the *packages* be your trial boundaries — they're free to move. Promote a package boundary to a service boundary only once it's stable and a real pressure demands it.
- **Don't over-split.** Fewer, well-bounded services beat many fine-grained ones. Start coarse; you can always split a service later, but merging two services back together (re-joining their data) is painful.

## Common pitfalls

- **Slicing by technical layer.** Controller/service/repository as three services. Every request pays N network hops and no one owns a feature. Slice by capability instead.
- **The shared "common" or "core" database.** "We'll just have all services read from the one products table for convenience." That convenience is the coupling that makes them undeployable independently. Each service owns its data (topic 04).
- **Entity-per-service (nanoservices).** An `address-service`, a `line-item-service`. Too fine to be useful; a single order becomes a distributed transaction across a dozen services. Group by capability, not by table.
- **Boundaries that ignore Conway's Law.** Your service boundaries will end up mirroring your team boundaries whether you plan for it or not. If one team owns "orders," don't split orders across three services owned by three teams — the coordination cost will eat you. Align services with team ownership.

---

## Try this yourself

No project — model it on paper:

1. Take the e-commerce domain (orders, inventory, payments, shipping, catalog/pricing, reviews). Draw the services you'd create and, for each, list *the data it owns exclusively*. Then trace one user action — "customer places an order" — as a sequence of calls/events between your services. Count the hops. If it's more than three or four, or if two services both want to write the same data, redraw the boundaries and try again.
2. Find the word "status" (or "customer," or "product") in a domain you know and list every distinct meaning it has to different parts of the business. Each distinct meaning is a candidate bounded context. Notice how a single shared class would have forced all those meanings into one place.

## Self-check

1. Why is splitting a system by technical layer (web / logic / data) a worse decomposition than splitting it by business capability (orders / inventory / payments)?
2. What is the single most practical test of whether a proposed boundary between two services is real? What does it mean if the test fails?
3. Your monolith needs to become services but a rewrite is off the table. Describe the strangler-fig approach and explain what it forces you to confirm about each piece before you extract it.
