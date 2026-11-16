# 17 — Observability

Your service is in production, behind a load balancer, in containers, talking to a database, a cache, and a broker. A customer says "checkout was slow at 14:05". Can you find out why, without logging into a server or adding a `println` and redeploying? **Observability** is the set of signals a service emits so you can answer questions like that from the outside: logs, metrics, and traces, plus the health endpoints and alerts built on them.

You've met pieces of this already: logging in module 08 topic 10, Actuator and a first look at Prometheus in 08-12, correlation ids and Micrometer Tracing in module 13 topic 17, and probes in module 14 topic 12. This module puts them together into what a backend engineer actually uses during an incident, and shows the common ways each signal lies or goes missing: a metric with a tag that creates ten thousand time series, an average that hides slow requests, a trace that breaks at the first thread pool, a liveness probe that restarts every pod when the database blips, an alert that wakes someone up for nothing.

## Who this is for

- Anyone who has finished modules 08–14 and has only ever debugged by reading log files.
- Developers who have Actuator and Prometheus "set up" but couldn't write the query for "95th percentile latency of checkout over the last 5 minutes".
- Anyone who has been paged at night by an alert that turned out not to matter.

## What you'll be able to do at the end

- [ ] Say which signal (logs, metrics, or traces) answers a given question, and what each costs.
- [ ] Produce structured JSON logs with request context in every line, and keep that context from leaking between requests or getting lost on other threads.
- [ ] Record counters, gauges, and timers with Micrometer, with tags that don't explode into millions of time series.
- [ ] Read Prometheus's exposition format and write PromQL for request rate, error ratio, and latency percentiles.
- [ ] Build a service dashboard with the RED and USE methods, and spot dashboards that hide problems.
- [ ] Trace a request across HTTP calls and threads with OpenTelemetry, and explain sampling.
- [ ] Jump from an alert to the trace to the log lines of one failing request.
- [ ] Configure liveness, readiness, and startup checks so a dependency blip doesn't restart the whole fleet.
- [ ] Define SLIs and SLOs, compute an error budget, and write burn-rate alerts that fire on real problems and stay quiet on blips.

If you can do all nine, the module worked.

## Prerequisites

- **Module 08 topics 10 and 12** (logging and Actuator). This module starts where they stop.
- **Module 13 topic 17** (correlation and tracing) for topics 06–07.
- **Module 14 topics 09 and 12** (healthchecks and Kubernetes probes) for topic 08.
- **Docker** for topics 04, 05, and 09 only. Topic 04's tests start Prometheus with Testcontainers; topic 05's `demo.sh` runs an app, Prometheus, and Grafana with Compose; topic 09's `demo.sh` runs Prometheus's `promtool` to test alert rules. The images are `prom/prometheus:v3.5.0` (about 420 MB) and `grafana/grafana:12.1.0` (about 900 MB). Every other topic is Docker-free.

## How this module is organized

Topic 01 is **the map**: the three signals and which question each answers.

Topics 02–05 are **logs and metrics**: structured logging and MDC (02), Micrometer (03), Prometheus and PromQL (04), and dashboards (05).

Topics 06–07 are **traces**: OpenTelemetry tracing and context propagation (06), and connecting traces to logs and metrics (07).

Topics 08–09 are **acting on the signals**: health checks the platform uses to restart and route (08), and SLOs and alerts that tell humans when to act (09).

```text
01-logs-metrics-traces                <- one slow request, diagnosed three ways
02-structured-logging                 <- JSON logs, MDC; context leaking between requests and lost on other threads
03-micrometer-metrics                 <- counter, gauge, timer, tags; the high-cardinality tag
04-prometheus-and-promql              <- scraping, exposition format, rate, histogram_quantile; averaging percentiles
05-grafana-dashboards                 <- RED and USE; a provisioned dashboard; dashboards that hide problems
06-opentelemetry-tracing              <- spans, traceparent, propagation across HTTP and threads, sampling
07-correlating-logs-and-traces        <- traceId in every log line, exemplars, an id the customer can quote
08-liveness-and-readiness             <- availability states; the database in liveness restarts every pod
09-slis-slos-and-alerting             <- error budgets, burn-rate alerts tested with promtool
```

```text
COMMON_MISTAKES.md       <- real observability bugs with code and config
INTERVIEW_QNA.md         <- interview-grade questions on signals, cardinality, percentiles, tracing, probes, SLOs
```

## The toolkit

| Tool / library | What it does | First used in |
|---|---|---|
| Spring Boot structured logging (`logging.structured.format.console`) | JSON log lines (ECS, Logstash, GELF) with no extra dependency | topic 02 |
| Micrometer (`micrometer-core`, via Actuator) | counters, gauges, timers, distribution summaries | topic 03 |
| `micrometer-registry-prometheus` | the `/actuator/prometheus` endpoint | topic 04 |
| Prometheus 3.5 (`prom/prometheus:v3.5.0`) | scrapes and stores metrics, runs PromQL, evaluates alert rules; `promtool` tests them | topic 04 |
| Grafana 12.1 (`grafana/grafana:12.1.0`) | dashboards over Prometheus | topic 05 |
| Micrometer Tracing + OpenTelemetry bridge (`micrometer-tracing-bridge-otel`) | creates and propagates spans | topic 06 |
| OpenTelemetry SDK testing (`InMemorySpanExporter`) | collects finished spans in tests, no collector needed | topic 01 |

The demos stay on Spring Boot 3.5.14 and Java 21, like modules 08–16.

## How to run the examples

Each topic has its own project under `CODE_EXAMPLES/NN-topic-name/`. Most are Maven projects where the tests are the lesson:

```bash
cd 17-observability/CODE_EXAMPLES/03-micrometer-metrics/<demo-dir>
mvn -q test
```

Topics 05 and 09 also have a `demo.sh` in the module 14 style: it starts what it needs, prints a `PASS`/`FAIL` line per check, removes its containers when it exits, and exits non-zero on failure. Their containers are named `roadmap17-*`, and topic 05 publishes Grafana and Prometheus on host ports `170NN`, so you can open the dashboard while the script waits.

## Checkpoint

You're done with this module when you can:

1. Given "checkout is slow for some users since 14:00", say which signal you'd look at first, second, and third, and why.
2. Explain how a `requestId` from one request can appear in another request's log lines, and fix it.
3. Explain why tagging a timer with `userId` can take down your monitoring, and what to tag with instead.
4. Write PromQL for the request rate, the 5xx error ratio, and the p95 latency of one endpoint, and explain why averaging the p95 of each instance is wrong.
5. Name the panels of a RED dashboard and a USE panel for the connection pool, and say what an average-latency panel hides.
6. Read a `traceparent` header, and explain why a trace stops at a `new RestTemplate()` or a plain thread pool.
7. Explain why only some requests have traces by default, and how to get from an alert to the log lines of one failing request.
8. Explain why the database must not be part of the liveness check, and what a startup probe is for.
9. Turn a 99.9% availability SLO over 30 days into an error budget in minutes, and explain why a burn-rate alert beats "error rate > 0".

If those nine feel comfortable, move to module 18, the last one, where you'll reason about the whole system at scale.

## A note on what's *not* here

- **No hosted observability vendors.** Datadog, New Relic, Grafana Cloud, CloudWatch, and the like all consume the same signals taught here; their setup belongs in a cloud or vendor guide.
- **No log aggregation stack.** Elasticsearch/OpenSearch, Loki, and Fluent Bit are where JSON logs go. Running them is platform work; topic 02 makes your logs ready for any of them.
- **No running tracing backend.** Jaeger, Tempo, and Zipkin store and display traces. Topics 06–07 show what a service sends (OTLP) and test it with an in-memory exporter.
- **No profiling or APM agents.** Continuous profilers and bytecode agents are useful, but they're tools to adopt, not concepts to learn first. JVM-level diagnosis is in module 03d.
- **No on-call process.** Incident roles, runbooks, and postmortems matter, but they're team practice, not backend engineering.
