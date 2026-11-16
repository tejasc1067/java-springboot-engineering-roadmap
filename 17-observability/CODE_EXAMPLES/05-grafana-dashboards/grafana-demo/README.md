# grafana-demo

A Spring Boot app, Prometheus, and Grafana in Compose, with the datasource and two dashboards provisioned
from files: `dashboards/service-red.json` (RED + USE, 9 panels) and `dashboards/service-broken.json`
(raw counters, an error count, average latency, no per-endpoint breakdown).

- `mvn -q test` (no Docker): `DashboardLintTest` checks both dashboard files. The good one passes; the
  broken one fails 10 specific rules.
- `./demo.sh` (Docker running): builds and starts the stack, sends traffic (5 of 100 product requests fail,
  every 20th checkout takes 1.5 s), then checks through Grafana's HTTP API that both dashboards are
  provisioned and every RED/USE panel query returns a number. It finishes by putting numbers on what the
  broken dashboard hides: average latency about 0.08 s against a p99 of about 1.7 s, and so on.
  Every container, network and volume is removed when it exits.

Ports: app `17050`, Prometheus `17051`, Grafana `17005`.

First run builds the app image (about a minute) and pulls `grafana/grafana:12.1.0` (about 905 MB on disk)
and `prom/prometheus:v3.5.0` (about 420 MB).

To explore the dashboards yourself:

```
docker compose up -d --build --wait
curl localhost:17050/api/checkout        # send some traffic
open http://localhost:17005              # admin / admin
docker compose down
```
