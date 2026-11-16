# availability-demo

Availability arithmetic, checked by simulation. Plain Java, no Docker.

- `NinesTest` — downtime per year and month for 99% to 99.999%, and MTBF/MTTR: repairing ten times faster is worth
  as much as failing ten times less often.
- `SeriesAndParallelTest` — components in series multiply (99.99% x 99.9% x 99.95% = 99.84%), redundant copies
  multiply their downtime (two 99% copies = 99.99%), and a single point of failure caps the whole system.
- `CorrelatedFailureTest` — two 99% replicas give 99.99% only when their failures are independent; with a shared
  cause they give 99%, the same as one.

The Monte Carlo runs use a fixed seed and 2,000,000 simulated minutes, so they repeat exactly and take well under a second.

```
mvn -q test
```
