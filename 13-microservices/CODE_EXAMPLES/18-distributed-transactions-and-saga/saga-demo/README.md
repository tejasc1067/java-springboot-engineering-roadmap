# saga-demo

There is no transaction across services, so a "place order" that spans inventory,
payment, and order can't be one atomic commit. A **saga** replaces it: a sequence of
local transactions, each with a **compensating** action to undo it if a later step fails.

Three in-process "services" and a tiny orchestrator make the forward-then-compensate
flow observable.

`SagaTest` proves, all green:

- **happy path commits every step** — stock reserved, payment charged, order created.
- **when payment fails, the stock reservation is compensated** — the saga fails, the
  earlier reservation is released, and stock returns to its starting value (nothing leaked);
  no charge, no order.
- **compensations run in reverse order of completion** — with the 3rd step failing, the
  completed steps compensate as `refund` then `release-stock` (reverse of how they ran).

## Run

```bash
mvn -q test
```

## The key idea

Compensation is a *semantic undo*, not a rollback: the earlier step really committed, and
a second transaction reverses its effect (release the reservation, refund the charge). In
between, the system was briefly inconsistent — a saga gives you **eventual** consistency,
not atomicity. Steps and compensations get retried, so both must be idempotent (topic 19).
