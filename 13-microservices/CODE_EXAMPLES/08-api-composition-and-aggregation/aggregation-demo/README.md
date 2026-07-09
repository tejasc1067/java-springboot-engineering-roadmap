# aggregation-demo

API composition: calling several services and joining their results into one response.
Each downstream is modelled as an injected function that can be made fast, slow, or
failing, so the interesting parts — failure modes and fan-out latency — are testable
without starting real services.

`OrderViewComposerTest` proves, all green:

- **composes one view from two services** — order-service + payment-service → one `OrderView`.
- **degrades to a partial view when an optional source is down** — payment-service
  throws, the view still returns with order data and a null payment section.
- **fails when a required source is down** — order-service is essential; no order, no
  view. Not everything can degrade; you choose what's essential.
- **parallel fan-out is faster than sequential** — two 200ms calls take ~400ms
  sequentially but ~200ms in parallel.

## Run

```bash
mvn -q test
```
