# Prometheus and PromQL

Module 08 topic 12 stopped at "the app exposes `/actuator/prometheus`". This topic is the other side: **Prometheus**, the server that collects those numbers every few seconds and stores them as time series, and **PromQL**, the query language every dashboard (topic 05) and alert (topic 09) is written in. PromQL looks simple and is easy to get subtly wrong: a query can return a confident number that means nothing. The demo runs a real Prometheus against a real Spring Boot app and shows four of those numbers.

---

## The problem this solves

Your app's metrics endpoint only tells you about *now*, and only for one instance. "How many requests per second did checkout handle over the last 5 minutes, across all 6 instances?" and "what latency did 95% of users get?" need two more things: something that records the values over time, from every instance, and a language to aggregate them. That's Prometheus.

## How it works

### Pull: Prometheus scrapes your app

Prometheus **pulls**. Every `scrape_interval` it sends `GET /actuator/prometheus` to each target, timestamps every line of the answer, and stores it. The demo's configuration, scraping every second:

```yaml
global:
  scrape_interval: 1s          # 15s-30s in production
scrape_configs:
  - job_name: orders-app
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ['host.testcontainers.internal:51234']
```

Prometheus adds two labels to every series it scrapes: `job` (`orders-app`) and `instance` (the target's address). That's how six instances of one app stay six separate series that you can add up. In Kubernetes, targets are discovered automatically instead of listed.

Pull has a useful side effect: if a target doesn't answer, Prometheus records `up{job="orders-app"} 0`, so "the app is down" is itself a metric.

### What a scrape returns: the exposition format

Each line is one **time series**: a metric name, labels in braces, and the current value. `ExpositionFormatTest` reads it straight from the app:

```text
# TYPE orders_placed_total counter
orders_placed_total 2.0
# TYPE orders_pending gauge
orders_pending 0.0
# TYPE http_server_requests_seconds histogram
http_server_requests_seconds_bucket{method="GET",outcome="SUCCESS",status="200",uri="/orders/{id}",le="0.001"} 0
http_server_requests_seconds_bucket{method="GET",outcome="SUCCESS",status="200",uri="/orders/{id}",le="0.001048576"} 0
http_server_requests_seconds_bucket{method="GET",outcome="SUCCESS",status="200",uri="/orders/{id}",le="0.001398101"} 1
http_server_requests_seconds_bucket{method="GET",outcome="SUCCESS",status="200",uri="/orders/{id}",le="0.001747626"} 2
...
http_server_requests_seconds_bucket{method="GET",outcome="SUCCESS",status="200",uri="/orders/{id}",le="+Inf"} 3
http_server_requests_seconds_count{method="GET",outcome="SUCCESS",status="200",uri="/orders/{id}"} 3
http_server_requests_seconds_sum{method="GET",outcome="SUCCESS",status="200",uri="/orders/{id}"} 0.0276
```

(The `error` and `exception` labels are trimmed here.) The four Prometheus metric types:

| Type | What it is | How it looks | Example |
|---|---|---|---|
| **Counter** | only goes up; resets to 0 when the process restarts | name ends in `_total` | `orders_placed_total` |
| **Gauge** | a current value that goes up and down | no suffix | `orders_pending`, `jvm_memory_used_bytes` |
| **Histogram** | counts of observations in buckets, plus a count and a sum | `_bucket{le="..."}`, `_count`, `_sum` | `http_server_requests_seconds_*` |
| **Summary** | percentiles computed inside the app | `{quantile="0.95"}` | Micrometer's `publishPercentiles` |

Read a histogram carefully. `le` means "less than or equal", so buckets are **cumulative**: the `le="0.01"` bucket counts every request that took 10 ms *or less*, including the ones already counted in `le="0.005"`. The `le="+Inf"` bucket counts everything and always equals `_count`. `aTimerWithAHistogram_becomesCumulativeBuckets_plusCountAndSum` checks both. Spring Boot only publishes buckets for timers you ask for:

```yaml
management.metrics.distribution.percentiles-histogram.http.server.requests: true
```

Prefer histograms over summaries: a summary's percentiles are computed per instance and **can't be combined** across instances (see "Broken: averaging percentiles" below). Histogram buckets can.

### PromQL: instant vectors and range vectors

```promql
http_server_requests_seconds_count{uri="/orders/{id}", status="200"}
```

That's an **instant vector**: the latest value of every matching series. `{...}` filters by label: `=`, `!=`, `=~` (regex), `!~`.

```promql
http_server_requests_seconds_count{uri="/orders/{id}"}[1m]
```

That's a **range vector**: every sample from the last minute, per series. You can't graph it directly; functions like `rate()` take it and return an instant vector. `PromqlBasicsTest.instantVersusRangeVector` shows the two result types (`vector` and `matrix`) from Prometheus's HTTP API.

### `rate()`, `increase()`, `irate()`

A counter's raw value is a total since the process started. It's almost never what you want to see. `theRawCounter_isATotalSinceTheAppStarted_rateTurnsItIntoPerSecond`:

```promql
http_server_requests_seconds_count{uri="/orders/{id}",status="200"}            # 30 - since the app started
rate(http_server_requests_seconds_count{uri="/orders/{id}",status="200"}[1m])  # per second, over the last minute
increase(http_server_requests_seconds_count{...}[1m])                          # how many in the last minute
```

- `rate(x[5m])`: average per-second increase over the window. Use it for graphs and alerts.
- `increase(x[5m])`: `rate` × window length, "how many in the last 5 minutes". Prometheus extrapolates to the window's edges, so it's usually not a whole number.
- `irate(x[5m])`: uses only the last two samples. Very spiky; fine for a zoomed-in graph, wrong for alerts.

Make the window at least 4× the scrape interval, so it always contains several samples. Grafana's `$__rate_interval` (topic 05) does that for you.

### `sum by`: aggregating across series and instances

```promql
sum by (status) (rate(http_server_requests_seconds_count{uri="/orders/{id}"}[5m]))
```

`sum by (status)` adds up every series and keeps only the `status` label: one line per status, with instances, methods, and everything else summed away (`sumBy_keepsTheLabelsYouName_andAddsUpTheRest`). `sum without (instance)` is the opposite: drop only the labels you name. Other aggregations: `avg`, `max`, `min`, `count`, `topk(5, ...)`.

**Always `rate` first, then `sum`.** `sum(rate(x))` is correct. `rate(sum(x))` is wrong: when one instance restarts, its counter drops, the sum drops, and `rate` thinks the whole fleet reset.

### Percentiles from a histogram

```promql
histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket[5m])))
```

Read it inside out: per-second rate of each bucket, summed across instances keeping `le`, then the 95th percentile. Keep `le` in every `by`; add other labels to get one percentile per endpoint: `sum by (le, uri)`. The result is an **estimate**: Prometheus knows how many requests fell in each bucket, not their exact times, so it interpolates linearly inside the bucket. More buckets near your latency targets give better estimates.

---

## Broken: four queries that return a confident wrong number

### 1. Averaging percentiles

Two pods. Pod a served 1,000 requests, all under 10 ms. Pod b served 100: 90 fast and 10 that took 2 seconds. A dashboard shows p95 per pod, and someone averages them. `PercentileAveragingTest.theAverageOfTwoP95s_isNotTheP95`:

```promql
# Broken: p95 per pod, then average
avg(histogram_quantile(0.95, sum by (pod, le) (rate(checkout_seconds_bucket[5m]))))   # 0.88 s

# Right: add up the buckets, then one quantile
histogram_quantile(0.95, sum by (le) (rate(checkout_seconds_bucket[5m])))             # 0.0096 s
```

Pod a's p95 is 9.5 ms and pod b's is 1.75 s, so the average says 880 ms. But only 10 of 1,100 requests (0.9%) were slow, so 95% of all requests took under 10 ms. Averaging ignores that pod a served ten times more traffic. Percentiles can't be averaged, added, or combined after the fact; only bucket counts can. (It works the other way too: averaging can hide a slow pod just as easily as invent one.)

### 2. `rate()` on a gauge

`rate` assumes its input only goes up, so it reads every drop as a counter reset. `RateOnGaugeTest`: a queue goes 10 → 50 → 20, a net change of +10.

```promql
increase(queue_depth[5m])        # 60 or more: 40 up, then "reset to 0 and grew to 20"
```

Prometheus 3 notices and adds a note to the response, which Grafana shows as a warning: `PromQL info: metric might not be a counter, name does not end in _total/_sum/_count/_bucket`. For gauges, read the value as it is, or use `max_over_time`, `avg_over_time`, `delta`, or `deriv`:

```promql
queue_depth                      # 20 right now
max_over_time(queue_depth[5m])   # 50, the peak
delta(queue_depth[5m])           # about +10
```

### 3. Subtracting counter values across a restart

Every deploy restarts the app, and every counter starts again from 0. `CounterResetTest`: jobs processed go 100 → 200 → 300, the app restarts, then 10 → 20. 220 jobs were really processed.

```text
last value - first value     = 20 - 100 = -80     (broken: "we processed minus 80 jobs")
resets(jobs_processed_total[5m])   = 1
increase(jobs_processed_total[5m]) >= 220         (rate and increase handle the reset)
```

Any drop in a counter is treated as a reset, and the value after it counts as growth from 0. That's why you always go through `rate`/`increase` instead of doing arithmetic on raw counter values, and why `rate` comes before `sum`.

### 4. Graphing a counter without `rate`

```promql
http_server_requests_seconds_count     # graphed: a line climbing since the last deploy
```

The graph goes up even at 3 a.m. with no traffic (it just stays flat at a high number), drops to zero after every deploy, and the slope you'd need to see a problem is invisible at that scale. Wrap counters in `rate()`. Topic 05's dashboard lint catches this.

---

## When to use it / when not to

- **Prometheus is for numbers**: rates, latencies, saturation, business counters. It answers "how much / how fast / how often", not "what happened to order 42". That's logs and traces (topics 02 and 06).
- **Keep label values bounded.** Every distinct label combination is a separate series stored in memory. `uri="/orders/{id}"` is one series; a user id or order id as a label is millions (topic 03). `ExpositionFormatTest.theUriTagIsTheTemplate_notTheRealPath` shows Spring tagging the template on the server side, and shows the trap on the client side: the test's own `TestRestTemplate`, given a finished URL string, tagged `http_client_requests` with every real path.
- **It isn't long-term storage by default.** Prometheus keeps 15 days locally unless configured otherwise. Longer retention and many-cluster setups use remote storage (Thanos, Mimir), which is platform work.
- **Short-lived jobs** that end before the next scrape need the Pushgateway or a different approach; a scrape can't catch a process that's gone.

## Common pitfalls

- **Averaging percentiles across instances or endpoints.** Sum the buckets `by (le)` first, then `histogram_quantile` once.
- **`rate()` on a gauge, or raw arithmetic on a counter.** `rate`/`increase` for counters only; `*_over_time`, `delta`, `deriv` for gauges.
- **`rate(sum(...))` instead of `sum(rate(...))`.** One instance restarting looks like a fleet-wide reset.
- **A range shorter than ~4 scrape intervals.** `rate(x[30s])` with a 15 s scrape interval often has fewer than two samples and returns nothing.
- **Expecting a new series to show its first events.** A series appears on its first event. If several events happen before Prometheus first scrapes it, it appears already at, say, 5, and `rate`/`increase` (which need two samples) never count those 5. Topic 05's `demo.sh` hits exactly this with the first errors of a new `status="500"` series.

---

## Code examples

`CODE_EXAMPLES/04-prometheus-and-promql/promql-demo/`. Run with `mvn -q test` (Docker running; the first run pulls `prom/prometheus:v3.5.0`, about 420 MB). Read in this order:

1. `src/main/java/com/example/promql/OrderController.java`: a counter, a gauge, and endpoints whose requests Spring times.
2. `src/main/resources/application.yml`: exposes `/actuator/prometheus` and turns on histogram buckets.
3. `src/test/java/com/example/promql/ExpositionFormatTest.java`: no Docker; what a scrape returns (`_total`, cumulative `le` buckets, `+Inf` = `_count`, URI templates).
4. `src/test/java/com/example/promql/PrometheusEnvironment.java`: starts the app, a fake target, and a real Prometheus scraping both every second; `query()` calls `/api/v1/query`.
5. `src/test/java/com/example/promql/FakeTarget.java`: a scrape target whose values the test sets, so it can "restart" or report exact buckets.
6. `src/test/java/com/example/promql/PromqlBasicsTest.java`: raw counter vs `rate`/`increase`, `sum by`, instant vs range vectors.
7. `src/test/java/com/example/promql/PercentileAveragingTest.java`: the average of p95s vs the real p95.
8. `src/test/java/com/example/promql/RateOnGaugeTest.java`: `increase` invents growth on a gauge; Prometheus 3's warning.
9. `src/test/java/com/example/promql/CounterResetTest.java`: a restart, raw subtraction vs `increase` and `resets`.

---

## Try this yourself

1. In `PercentileAveragingTest`, give pod b 1,000 requests with the same 10% slow. Predict the real p95 before running it, then check. When does averaging the p95s happen to give the right answer?
2. In `PromqlBasicsTest`, add a query for `rate(sum(http_server_requests_seconds_count)[1m])`. It's not even valid PromQL. Rewrite it as a subquery, `rate(sum(...)[1m:1s])`, then explain why the topic still tells you to write `sum(rate(...))`.
3. Start the stack from topic 05 (`docker compose up -d` in its demo folder), open Prometheus at `http://localhost:17051`, and try each query from this topic against real traffic.

## Self-check

1. A teammate graphs `http_server_requests_seconds_count{uri="/checkout"}` and says traffic "keeps growing". What is the graph really showing, and what query should it be?
2. Three instances report p95 latencies of 50 ms, 60 ms, and 2 s. Can you say what the service's p95 is? What query gives it?
3. Why does `increase(jobs_total[1h])` give the right answer across a deploy that restarted the app, when "value now minus value an hour ago" doesn't?
