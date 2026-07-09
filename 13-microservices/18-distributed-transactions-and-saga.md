# Distributed Transactions and the Saga Pattern

Placing an order touches three services: reserve stock (inventory), charge the card (payment), record the order (order-service). In a monolith this is one `@Transactional` method — all three succeed or all three roll back, atomically. Once those live in separate services with separate databases (topic 04), **that transaction is gone**: there is no `@Transactional` that spans three databases on three machines. If you charge the card but the order write fails, you can't just "roll back" — the money already moved. This topic is how to keep a multi-service operation consistent *without* a distributed transaction, using the **saga** pattern: a sequence of local transactions, each with a compensating action to undo it.

The demo runs a place-order saga and shows the compensation fire when a step fails.

---

## The problem this solves

You need "reserve stock AND charge payment AND create order" to be all-or-nothing. Across services you have two theoretical options and one real one:

- **Two-phase commit (2PC / XA).** A coordinator asks every service to "prepare," then "commit." It *can* give atomicity across databases — but it's a poor fit for microservices: it **holds locks across network calls** (killing throughput), **blocks indefinitely if the coordinator dies** mid-commit, isn't supported by many datastores or cloud-managed databases, and couples all participants to one synchronous ceremony. In practice, distributed microservices avoid 2PC.
- **Do nothing / hope.** Charge, then create the order, and if the order write fails… the customer is charged for nothing. This is the bug 2PC was meant to prevent, and it's what you get by ignoring the problem.
- **The saga.** Give up atomicity; accept **eventual consistency**. Run the steps as independent local transactions, and if a later step fails, run **compensating** transactions to semantically undo the earlier ones. This is the pattern microservices actually use.

## How it works: local steps + compensations

A saga is a list of steps, each a forward action plus the action that undoes it. From the demo:

```java
new Saga()
    .step("reserve-stock",  () -> inventory.reserve(sku, qty),        () -> inventory.releaseReservation(sku, qty))
    .step("charge-payment", () -> chargeId.set(payment.charge(cents)),() -> payment.refund(chargeId.get()))
    .step("create-order",   () -> orderId.set(order.createOrder(...)),() -> order.cancelOrder(orderId.get()))
    .execute();
```

The orchestrator runs steps in order. If any action throws, it runs the compensations of the **already-completed** steps in **reverse** order, then reports failure:

```java
for (Step step : steps) { step.action().run(); completed.push(step); }
// on failure:
for (Step step : completed) { step.compensation().run(); } // most-recent first
```

The demo proves it: on the happy path all three commit; when payment is declined, the stock reservation is **released** (available returns to its starting value — no stock leaked, no charge, no order); when the third step fails, compensations run `refund` then `release-stock` — reverse of how they ran.

**Compensation is a semantic undo, not a rollback.** The reserve *committed*; you don't un-commit it, you run a *new* transaction that releases it. You don't un-charge a card; you *refund* it. This has consequences: there's a window where the earlier steps' effects are visible (the money was charged, then refunded — the customer may see both on their statement), and some actions can't be perfectly undone (you can't un-send a shipped package). You design compensations to be the best available semantic reversal, and you order steps so the hardest-to-undo actions come last.

## Orchestration vs. choreography

Two ways to coordinate the steps:

- **Orchestration** (the demo). A central coordinator (an "order saga") explicitly calls each service and, on failure, calls the compensations. Pros: the flow is in one place, easy to follow and change, easy to see where a saga is stuck. Cons: the orchestrator is a component you build and operate, and it knows about every participant.
- **Choreography.** No central coordinator — each service reacts to events (topic 07) and emits its own. `OrderCreated` → payment listens and charges → emits `PaymentCharged` → inventory listens and reserves → etc. Compensation is also event-driven (`PaymentFailed` → inventory listens and releases). Pros: no central component, services are loosely coupled. Cons: the flow is *implicit* — spread across services' event handlers — so it's hard to see the whole saga or debug where it stalled, and easy to create accidental cycles.

Rule of thumb: **orchestration for complex sagas** (many steps, conditional logic — you want the flow visible in one place); **choreography for simple ones** (a couple of steps, naturally event-driven). Many systems mix both.

## Eventual consistency and its consequences

A saga is not atomic and not isolated, and you must design for that:

- **Intermediate states are visible.** Between "reserve stock" and "create order," the stock is reserved but no order exists. Model these explicitly — an order has a `PENDING` state before `CONFIRMED`, and a query might see stock that's reserved-but-not-yet-ordered. Don't pretend the operation is instantaneous.
- **No isolation between sagas.** Two concurrent sagas can interleave and see each other's intermediate state (the "dirty read" problem, at the service level). Countermeasures: *semantic locks* (mark a record "pending" so other sagas skip it), reordering steps, or accepting and reconciling anomalies. This is genuinely hard — it's the cost of giving up ACID.
- **Failure of a compensation.** What if the refund *also* fails? Compensations must be retried until they succeed (they can't themselves be compensated away), which means they must be **idempotent** (topic 19) — and a compensation that keeps failing needs alerting and, ultimately, human intervention. Sagas assume compensations eventually succeed.

## When to use it / when not to

- **Use a saga** when an operation genuinely spans multiple services' data and must be consistent *eventually*. It's the standard answer to "how do I keep things consistent without a distributed transaction."
- **Prefer NOT needing one.** The best fix is often to *not split* data that must change together (topic 03): if "reserve stock and create order" must always be atomic, maybe stock and orders belong in one service, one local transaction. Reach for a saga when the split is justified for other reasons — not as a consolation prize for a bad boundary.
- **Never 2PC across microservices** as a default. Its locking and blocking make it unsuitable for the loosely-coupled, independently-deployed world; sagas are the accepted pattern.
- **Keep the happy path a single local transaction where you can.** A saga is complexity; every step and compensation is code to write, test, and operate. Fewer steps, fewer failure modes.

## Common pitfalls

- **Assuming you still have atomicity.** Writing "charge then create order" with no compensation and no pending state, expecting a rollback that can't happen. That's the "charged for nothing" bug.
- **No compensation for a step that needs one.** Every step that commits a visible/irreversible-ish effect needs a compensation (or must be last). Forgetting one leaves the system stuck inconsistent on failure.
- **Non-idempotent steps or compensations.** Retries (topic 10) and at-least-once delivery (topic 07) mean a step or compensation can run twice — double-charge, double-refund. Make them idempotent (topic 19).
- **Hidden flow in choreography.** A pure event-choreographed saga with no central view is very hard to debug when it stalls halfway. Add observability (topic 17) and consider orchestration for complex flows.
- **Ignoring intermediate-state visibility.** Not modeling `PENDING`, so queries and other sagas see half-finished state as if it were final.
- **Reaching for a saga to patch a wrong boundary.** If two datasets are always modified together, that's topic 03 telling you they're one service. Don't saga around a decomposition mistake.

---

## Code examples

Runnable demo — `18-distributed-transactions-and-saga/saga-demo/`. Read then `mvn test`:

1. `saga-demo/src/main/java/com/example/saga/Saga.java` — the orchestrator: run steps forward, compensate completed steps in reverse on failure.
2. `saga-demo/src/main/java/com/example/saga/Participants.java` — three in-process services, each with a forward operation and its compensation.
3. `saga-demo/src/test/java/com/example/saga/SagaTest.java` — happy path commits; payment-failure compensates the reservation; compensations run in reverse order.

## Try this yourself

1. Add a fourth step (`send-confirmation-email`) *after* create-order, and make it fail. Note there's no real compensation for a sent email — so put irreversible/hard-to-undo steps LAST (or make them async and non-blocking, topic 07), where a failure compensates less.
2. Make the *compensation* for payment (`refund`) throw. Observe that the saga has no way to recover — this is why compensations must be retried until they succeed and be idempotent (topic 19). Sketch what you'd do operationally when a refund keeps failing.
3. Redesign the demo as *choreography*: describe the events each service would emit and listen for (`StockReserved`, `PaymentCharged`, `PaymentFailed`, …) and where the compensation triggers live. Notice how the overall flow is no longer visible in one place.

## Self-check

1. Why can't you use a single `@Transactional` for "reserve stock + charge payment + create order" across three services, and why isn't 2PC the answer in microservices?
2. What is a compensating transaction, and how is it different from a database rollback? Give an example where a perfect undo is impossible and say how you'd design around it.
3. Contrast orchestration and choreography for a saga: which puts the flow in one place, which is purely event-driven, and when would you pick each?
