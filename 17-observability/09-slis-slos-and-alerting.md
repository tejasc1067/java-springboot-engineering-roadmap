# SLIs, SLOs, and Alerting

Topics 03–05 gave you metrics and dashboards. Dashboards are for looking. Alerts decide when a person is interrupted, possibly at 3am, and that decision needs a definition of "the service is broken enough to matter". This topic builds one: an **SLI** (what you measure), an **SLO** (how good it must be), an **error budget** (how much failure that allows), and **burn-rate alerts** that page when the budget is being spent dangerously fast, and stay quiet otherwise.

The demo is a set of real Prometheus rules for a Spring Boot service, with unit tests run by `promtool`, the tool that ships with Prometheus.

---

## The problem this solves

Most teams start with alerts like these:

```yaml
- alert: HighCpu
  expr: process_cpu_usage > 0.8
- alert: AnyServerError
  expr: sum(rate(http_server_requests_seconds_count{status=~"5.."}[1m])) > 0
```

Both page people for nothing. CPU at 80% is a *cause* that might matter or might not: a batch job at 90% CPU with every request fast is fine. "Any 5xx" fires for every pod restart, every deploy, every client that sends garbage; the demo shows it paging for a 2-minute blip that fixed itself. After a few weeks of this, people mute the channel, and the alert that matters is missed. That's **alert fatigue**.

The alternative: alert on **symptoms users feel** (requests failing, requests slow), and only when there's **enough** of them to threaten a target you agreed on in advance.

## How it works

### SLI: what you measure

A **service level indicator** is a ratio of good events to valid events:

```text
availability SLI = requests that did not fail with 5xx / all requests
latency SLI      = requests faster than 300 ms         / all requests
```

Decide what counts. Here a `404` is a valid, *good* request: the service worked, the client asked for something that doesn't exist. A `503` is bad. Health checks and other internal traffic are usually excluded from "valid".

Spring Boot already exports what you need (`MetricNamesTest` checks the exact names):

```text
http_server_requests_seconds_count{method="GET",outcome="SERVER_ERROR",status="503",uri="/orders/{id}",...} 1
http_server_requests_seconds_bucket{...,status="200",uri="/orders/{id}",le="0.3"} 1
```

The `_bucket{le="0.3"}` series, "requests that took at most 0.3 s", exists only if 300 ms is a histogram boundary:

```yaml
management.metrics.distribution.slo.http.server.requests: 300ms
```

Without it there are no `_bucket` series at all (`NoSloBucketTest`), and a latency rule written against `le="0.3"` silently matches nothing and never fires. (This setting adds exactly that boundary. Topic 04's `percentiles-histogram` adds dozens of boundaries, but the two nearest 300 ms are `0.268435456` and `0.357913941`, so `le="0.3"` still doesn't exist.)

### SLO: the target

A **service level objective** is a target for an SLI over a window:

```text
99.9% of requests succeed, measured over the last 30 days
99%   of requests finish in under 300 ms, over the last 30 days
```

100% is the wrong target. It's impossible, and chasing it freezes every change. Pick the level below which users actually complain, and make it a business decision, not an engineering wish.

### Error budget: the failure the SLO allows

`1 - SLO` is the **error budget**. For 99.9% over 30 days (`ErrorBudgetTest`):

| SLO | Allowed failure | As total downtime per 30 days |
|---|---|---|
| 99% | 1% | 7 h 12 min |
| 99.9% | 0.1% | 43.2 min |
| 99.99% | 0.01% | 4 min 19 s |

At 1,000 requests a minute, 99.9% allows 43,200 failed requests a month. The budget turns reliability into something you can spend: a risky migration, a deploy that drops a few requests. When it's nearly gone, the team slows down releases and works on reliability. When there's plenty left, nobody needs to be paged for small things.

### Burn rate: how fast you're spending it

```text
burn rate = observed error ratio / allowed error ratio
```

A burn rate of 1 spends exactly the whole budget by the end of the 30 days. At 5% errors against a 0.1% budget, the burn rate is 50, and the month's budget is gone in 14.4 hours (`burnRate_isTheErrorRatioDividedByTheBudget`).

So the question for an alert becomes: "at the current rate, how much of the budget are we spending?" The standard answer, from Google's SRE workbook, is two alerts:

| Alert | Fires when | Burn rate | Why that number |
|---|---|---|---|
| fast burn (page) | 2% of the month's budget spent in 1 hour | 14.4 | 0.02 × 720 h / 1 h |
| slow burn (page or ticket) | 5% of the budget spent in 6 hours | 6 | 0.05 × 720 h / 6 h |

At 14.4 the whole budget would be gone in 50 hours; at 6, in 5 days (`theFastBurnThreshold_...`, `theSlowBurnThreshold_...`). Anything slower is a ticket for working hours, not a page.

### Two windows per alert

Each alert checks a **long window** and a **short window** (1/12 of the long one), and both must be over the threshold:

```yaml
- alert: OrdersErrorBudgetFastBurn
  expr: |
    orders:http_errors:ratio_rate1h > (14.4 * 0.001)
    and
    orders:http_errors:ratio_rate5m > (14.4 * 0.001)
  for: 2m
  labels:
    severity: page
```

- The **long window** (1h) makes sure enough budget has really been spent. A short spike is diluted by the rest of the hour.
- The **short window** (5m) makes sure it's still happening. Without it the alert would keep firing for up to an hour after the fix, because the 1h ratio stays high.

`orders:http_errors:ratio_rate1h` is a **recording rule**: Prometheus computes the ratio every evaluation and stores it as a new series, so alerts and dashboards reuse it instead of recomputing it:

```yaml
- record: orders:http_errors:ratio_rate1h
  expr: |
    sum(rate(http_server_requests_seconds_count{job="orders", status=~"5.."}[1h]))
    /
    sum(rate(http_server_requests_seconds_count{job="orders"}[1h]))
```

### Testing the rules with `promtool`

An alert you haven't tested is a guess. `promtool test rules` feeds made-up series into the rules and checks which alerts fire at which times. A test case looks like this:

```yaml
- interval: 1m
  input_series:
    - series: 'http_server_requests_seconds_count{job="orders", uri="/orders/{id}", status="200"}'
      values: '0+950x120'            # +950 requests a minute, for 120 minutes
    - series: 'http_server_requests_seconds_count{job="orders", uri="/orders/{id}", status="500"}'
      values: '0+50x120'             # +50 failures a minute: 5% errors
  alert_rule_test:
    - eval_time: 65m
      alertname: OrdersErrorBudgetFastBurn
      exp_alerts:
        - exp_labels: { severity: page }
          exp_annotations: { summary: ... }
```

`slo-rules.test.yml` has six scenarios, all at 1,000 requests a minute:

| Scenario | Errors | Expected | Result |
|---|---|---|---|
| healthy | 0.05% | no alert | quiet |
| bad deploy | 5% (burn 50) | fast and slow burn page | both fire by 65 min |
| slow leak | 0.8% (burn 8) | slow burn only | slow fires; fast quiet (8 < 14.4) |
| blip | 2 minutes of 23% after 6 healthy hours | nobody paged | quiet: the 5m window spikes to 10.7%, the 1h window is only 1% |
| recovery | 5% for 70 min, then fixed | fast burn clears first | at 80 min fast is quiet, slow still fires; at 105 min both quiet |
| slow requests | 20% over 300 ms | latency page | fires |

The blip spent 600 failed requests, 1.4% of the month's budget: under the 2% line, so not worth waking anyone (`theTwoMinuteBlip_spendsTooLittleToPage`).

### Broken: "page on any 5xx"

```yaml
- alert: OrdersAnyServerError
  expr: sum(rate(http_server_requests_seconds_count{job="orders", status=~"5.."}[1m])) > 0
```

`naive-rules.test.yml` replays the same 2-minute blip. The alert fires at 6h01m and has resolved itself by 6h04m. The test passes, and that's the problem: it proves someone was woken up for a problem that was over before they opened their laptop.

One more thing this test taught: it uses a 15-second sample interval. With one sample a minute, `rate(...[1m])` returns *nothing* in Prometheus 3, because a range selector needs at least two samples inside the window and the window doesn't include its start. Make ranges at least four times the scrape interval (topic 04).

## When to use it / when not to

- **Page** only on SLO burn: symptoms users feel, fast enough to need a human now.
- **Ticket**, not page, for slow burns that will exhaust the budget in days, certificate expiry next week, a disk at 80%.
- **Dashboards, not alerts**, for causes: CPU, heap, GC, connection pool usage, consumer lag. Look at them when an SLO alert fires to find out *why*.
- **Every page needs an action.** If the response to an alert is "wait and see", delete it or turn it into a ticket. Link a runbook in the annotations.
- **Low-traffic services** are hard: at 10 requests an hour, one failure is a 10% error ratio. Use longer windows, count minimum traffic in the rule, or add synthetic checks that generate steady traffic.

## Common pitfalls

- **Alerting on causes.** `HighCpu` and `HighHeap` page when users are fine and stay quiet when users aren't. Alert on the SLI; put causes on the dashboard.
- **A single short window.** `rate(5xx[1m]) > 0` or even a 5m error ratio alone pages on every blip (`naive-rules.test.yml`). Use a long and a short window together.
- **A rule against a metric that doesn't exist.** A latency rule on `le="0.3"` with no such bucket, or a typo in a label, returns no data, and no data never fires. Check names against `/actuator/prometheus` (`MetricNamesTest`), and add an alert on `absent(...)` for critical series.
- **Counting 4xx as failures.** Clients sending bad requests then burn your budget. Count only what your service got wrong: 5xx, and timeouts.
- **Untested rules.** A rule that has never fired in a test may not fire in production either. `promtool test rules` in the build costs seconds.

---

## Code examples

`CODE_EXAMPLES/09-slis-slos-and-alerting/slo-demo/`. Run `mvn -q test` (no Docker), then `./demo.sh` (needs Docker; uses `prom/prometheus:v3.5.0`). Read in this order:

1. `src/main/java/com/example/slo/ErrorBudget.java` and `src/test/java/com/example/slo/ErrorBudgetTest.java` — budgets, burn rates, and where 14.4 and 6 come from.
2. `src/main/resources/application.yml` — the 300 ms SLO bucket.
3. `src/test/java/com/example/slo/MetricNamesTest.java` and `NoSloBucketTest.java` — the series the rules depend on, and what happens without the bucket.
4. `rules/slo-rules.yml` — recording rules, then the burn-rate alerts.
5. `rules/slo-rules.test.yml` — the six scenarios.
6. `rules/naive-rules.yml` and `rules/naive-rules.test.yml` — the "any 5xx" alert paging on the blip.
7. `demo.sh` — runs `promtool check rules` and `promtool test rules` in a throwaway container.

---

## Try this yourself

1. In `slo-rules.yml`, delete the `ratio_rate1h > ...` line and the `and` under it from the fast-burn alert, and run `./demo.sh`. Which scenario fails, and what does the failure say about what the long window was doing?
2. Add a third alert, `OrdersErrorBudgetTicket`, for a burn rate of 1 over 3 days and 6 hours, with `severity: ticket`. Work out the threshold with `ErrorBudget.burnRateThatSpends`, then write a test scenario it fires on and the slow-burn alert doesn't.
3. Change the availability SLO to 99.99%. Recompute the thresholds. Does the blip scenario still stay quiet? What does that tell you about choosing a target your service can't meet?

## Self-check

1. Your SLO is 99.9% over 30 days and the service has been returning 2% errors for the last hour. What's the burn rate, how much of the month's budget did that hour cost, and should someone be paged?
2. Why does a burn-rate alert check both a 1-hour and a 5-minute window? What goes wrong with only the 1-hour window, and with only the 5-minute one?
3. A teammate wants alerts for CPU above 80% and for any 5xx. What would you tell them to alert on instead, and where should CPU go?
