# scale-demo

Back-of-envelope estimation and what happens to a worker pool at its limit. Plain Java, no Docker.

- `UrlShortenerEstimateTest` — 10M daily users: 11.6 writes/s, 1,157 reads/s (3,472 at peak), 182.5 GB a year,
  6 instances at 70% plus a spare, and pool sizes from Little's law.
- `SaturationTest` — 10 workers at 50 ms each top out at 200 requests/s. At 95% busy the average wait is six times
  what it is at 80%; at 125% the queue never drains and late requests wait over 10 s.

The simulation uses simulated time and a fixed random seed, so it's fast and gives the same numbers every run.

```
mvn -q test
```
