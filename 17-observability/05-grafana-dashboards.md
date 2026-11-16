# Grafana Dashboards

Prometheus stores the numbers and PromQL asks questions of them (topic 04). **Grafana** draws the answers: a dashboard is a page of graphs, each one a PromQL query. The hard part isn't the tool, it's deciding *what* to put on the page. A good service dashboard answers "is the service healthy right now, and if not, where?" in ten seconds. A bad one has forty graphs, shows averages and raw totals, and looks calm during an incident. This topic gives you two methods for choosing panels, the queries for each, and a broken dashboard to compare against.

---

## The problem this solves

At 2 a.m. the alert says "checkout is slow". You open the dashboard. If it shows average latency for the whole service, a graph of total requests climbing since the last deploy, and an "errors: 37" number, you can't tell whether 37 is bad, which endpoint is slow, or whether the database pool is exhausted. Every panel was "correct". None of them helps.

## How it works

### Two methods for choosing panels

**RED, for anything that handles requests** (an API, a message consumer):

| | Question | PromQL |
|---|---|---|
| **R**ate | how much traffic? | `sum by (uri) (rate(http_server_requests_seconds_count{uri!~"/actuator.*"}[$__rate_interval]))` |
| **E**rrors | what *share* of it fails? | `sum(rate(...{status=~"5.."}[$__rate_interval])) / sum(rate(...[$__rate_interval]))` |
| **D**uration | how long do users wait? | `histogram_quantile(0.99, sum by (le, uri) (rate(http_server_requests_seconds_bucket{...}[$__rate_interval])))` |

**USE, for every resource the service depends on** (connection pool, threads, memory, CPU):

| | Question | Connection pool example |
|---|---|---|
| **U**tilization | how busy is it? | `max(hikaricp_connections_active) / max(hikaricp_connections_max)` |
| **S**aturation | is work queuing for it? | `max(hikaricp_connections_pending)` (threads waiting for a connection) |
| **E**rrors | is it failing? | `sum(rate(hikaricp_connections_timeout_total[$__rate_interval]))` |

Google's SRE book calls a similar set the **four golden signals**: latency, traffic, errors, saturation. Same idea, different letters.

RED tells you *that* users are hurting; USE usually tells you *why*. Latency up and pending connections up together point at the database. Latency up with idle pools and CPU at 100% points at the app.

### The demo's dashboard

`dashboards/service-red.json` has nine panels:

1. **Rate**, requests per second by endpoint.
2. **Errors**, the share of requests answered with 5xx.
3. **Duration p95**, by endpoint.
4. **Duration p99**, by endpoint.
5. **Utilization**, connection pool in use (fraction of the maximum).
6. **Saturation**, threads waiting for a connection.
7. **Errors**, connection timeouts per second.
8. **Utilization**, heap used (fraction of the maximum).
9. **Utilization**, process CPU.

Three details in those queries matter:

- **`uri!~"/actuator.*"`** removes Prometheus's own scrapes and health checks. They're fast and constant, so they inflate the request rate and pull latency down. In `demo.sh`, the unfiltered rate is about 15% higher than the real traffic.
- **`$__rate_interval`** is a Grafana variable for the `rate()` window. Grafana sets it to at least 4 × the scrape interval it's told about (`timeInterval: 2s` in the datasource), so `rate()` always has enough samples. In the demo it becomes `[8s]`.
- **`jvm_memory_max_bytes{area="heap"} > 0`.** Some heap pools report a maximum of -1 ("no fixed limit"), and adding them would subtract from the total. `> 0` keeps only real limits.

### Dashboards as code: provisioning

Click a dashboard together in the UI and it lives only in that Grafana's database: no review, no history, gone with the container. Instead, keep the JSON in Git and let Grafana load it at startup. The demo mounts two folders:

```yaml
# grafana/provisioning/datasources/prometheus.yaml
datasources:
  - name: Prometheus
    uid: prometheus           # the dashboards refer to this uid
    type: prometheus
    url: http://prometheus:9090
    jsonData:
      timeInterval: 2s        # the scrape interval; used for $__rate_interval
```

```yaml
# grafana/provisioning/dashboards/dashboards.yaml
providers:
  - name: files
    type: file
    options:
      path: /var/lib/grafana/dashboards     # ./dashboards, mounted in compose.yaml
```

A panel in the JSON is small:

```json
{
  "type": "timeseries",
  "title": "RED - Duration: p99 latency, by endpoint",
  "datasource": { "type": "prometheus", "uid": "prometheus" },
  "fieldConfig": { "defaults": { "unit": "s" } },
  "targets": [{
    "refId": "A",
    "expr": "histogram_quantile(0.99, sum by (le, uri) (rate(http_server_requests_seconds_bucket{uri!~\"/actuator.*\"}[$__rate_interval])))",
    "legendFormat": "{{uri}}"
  }]
}
```

The usual workflow: build or tweak the dashboard in the UI, export its JSON (Export → Export as JSON), commit it, and let provisioning deploy it everywhere.

`demo.sh` starts the app, Prometheus, and Grafana with Compose, sends traffic, and then checks through Grafana's HTTP API that both dashboards were provisioned (`/api/search`) and that every one of the nine panel queries returns a number (`/api/ds/query`, the same endpoint the browser uses to draw a panel).

---

## Broken: a dashboard that hides every problem

`dashboards/service-broken.json` has four panels that look reasonable:

| Panel | Query | What it hides |
|---|---|---|
| Requests | `http_server_requests_seconds_count` | a total since startup: a line that only climbs, and drops to 0 on every deploy |
| Errors | `sum(http_server_requests_seconds_count{status="500"})` | a count, not a ratio: is 5 a lot? Out of 100 requests or 100,000? |
| Latency | `sum(rate(..._sum)) / sum(rate(..._count))` | the **average**: a slow 5% of requests barely moves it |
| Request rate | `sum(rate(http_server_requests_seconds_count[...]))` | everything added together: one slow endpoint vanishes into the total, and Prometheus's scrapes are counted as traffic |

`demo.sh` puts numbers on it. The app's checkout takes 20 ms, except every 20th call, which takes 1.5 s:

```text
PASS: average latency 0.08s looks fine; p99 is 1.70s: 1 user in 100 waits over a second
PASS: the 'Errors' panel says 5 (of how many?); the error ratio says 0.057
PASS: without uri!~"/actuator.*" the request rate counts Prometheus's own scrapes: 1.63 vs 1.41 req/s
```

The average says 80 ms. One in twenty checkouts takes 1.5 seconds, and the average would only look worrying once a large share of requests were slow.

**Too many panels** is the other common failure. A 40-panel dashboard with every JVM metric is a dashboard nobody reads during an incident. Put RED and USE at the top; move detailed panels to a second "drill-down" dashboard and link to it.

### A dashboard lint

Dashboards are code, so review them like code. `DashboardLintTest` (no Docker) reads both JSON files and applies the rules above:

```java
assertThat(DashboardLint.check(load("service-red.json"))).isEmpty();

assertThat(DashboardLint.check(load("service-broken.json"))).containsExactlyInAnyOrder(
        "Requests: COUNTER_WITHOUT_RATE",
        "Requests: INCLUDES_ACTUATOR_TRAFFIC",
        "Errors: COUNTER_WITHOUT_RATE",
        "Errors: ERRORS_NOT_A_RATIO",
        "Errors: INCLUDES_ACTUATOR_TRAFFIC",
        "Latency: AVERAGE_LATENCY",
        "Latency: INCLUDES_ACTUATOR_TRAFFIC",
        "Request rate: INCLUDES_ACTUATOR_TRAFFIC",
        "dashboard: NO_PERCENTILE_LATENCY",
        "dashboard: NO_ENDPOINT_BREAKDOWN");
```

A third test copies panels until there are 20 and gets `TOO_MANY_PANELS (20)`. The rules are simple regular expressions, not a PromQL parser, which is enough to catch the common mistakes in review.

---

## When to use it / when not to

- **One RED + USE dashboard per service** is the baseline. Same layout for every service, so on-call engineers know where to look.
- **Dashboards are for diagnosis, not detection.** Nobody watches a dashboard all night. Alerts (topic 09) detect problems; the dashboard is where the alert links to.
- **Business dashboards** (orders per minute, revenue) are worth having too, built from your own counters (topic 03), but keep them separate from the operational one.
- **Don't graph what you can't act on.** If nobody would do anything differently when a panel changes, delete it.

## Common pitfalls

- **Averages instead of percentiles.** Show p95 and p99 per endpoint. The average hides exactly the users who are suffering.
- **Error counts instead of error ratios.** Divide by total requests; a count means nothing without its denominator.
- **No per-endpoint breakdown.** `sum by (uri)` for rates and `sum by (le, uri)` for percentiles. A total hides the one slow endpoint.
- **Counting your own scrapes and health checks.** Filter `uri!~"/actuator.*"`.
- **Dashboards edited only in the UI.** Provision them from JSON in Git, or they're lost with the Grafana instance.

---

## Code examples

`CODE_EXAMPLES/05-grafana-dashboards/grafana-demo/`. `mvn -q test` runs the lint with no Docker. `./demo.sh` runs everything else (Docker running). The first run builds the app image and pulls `grafana/grafana:12.1.0` (about 905 MB on disk) and `prom/prometheus:v3.5.0` (about 420 MB). Read in this order:

1. `src/main/java/com/example/grafana/ShopController.java`: a fast endpoint that uses the connection pool and fails on `boom`, and a checkout that's slow every 20th call.
2. `dashboards/service-red.json`: the RED + USE dashboard.
3. `dashboards/service-broken.json`: the same service, badly.
4. `grafana/provisioning/datasources/prometheus.yaml` and `grafana/provisioning/dashboards/dashboards.yaml`: provisioning.
5. `prometheus/prometheus.yml` and `compose.yaml`: the three containers.
6. `src/test/java/com/example/grafana/DashboardLint.java` and `DashboardLintTest.java`: the lint rules and what they find.
7. `demo.sh`: traffic, then the Grafana API checks and the numbers the broken dashboard hides.

To look at the dashboards yourself: `docker compose up -d --build --wait`, send some requests (`curl localhost:17050/api/checkout`), and open `http://localhost:17005` (admin / admin). `docker compose down` when you're done.

---

## Try this yourself

1. Change the checkout so every 5th call is slow, and run `demo.sh` again. How much does the average move, and how much does the p99 move? At what share of slow requests would the average finally look alarming?
2. Add a panel for p99 latency **without** `by (uri)`, in a new dashboard file. Send traffic only to `/api/products/1` for a while, then only to `/api/checkout`. What happens to the one-line p99, and why would that confuse someone during an incident?
3. Add a lint rule that flags `irate(` in any panel (topic 04: too spiky for an overview). Write a panel that breaks it, and check that `DashboardLintTest` fails.

## Self-check

1. Name the three RED signals and write the PromQL for each for a Spring Boot service.
2. A service's average latency is 80 ms and its p99 is 1.7 s. Which number goes on the dashboard, and what does the gap between them tell you?
3. What problem does provisioning dashboards from JSON files solve, compared with building them in the Grafana UI?
