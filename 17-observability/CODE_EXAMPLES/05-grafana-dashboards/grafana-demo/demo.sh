#!/usr/bin/env bash
# Topic 05: app + Prometheus + Grafana with provisioned dashboards. Checks that every panel of the
# RED/USE dashboard returns data, and shows numerically what the broken dashboard's panels hide.
set -euo pipefail
cd "$(dirname "$0")"

FAILED=0
pass() { echo "PASS: $1"; }
fail() { echo "FAIL: $1"; FAILED=1; }

APP=http://localhost:17050
PROM=http://localhost:17051
GRAFANA=http://localhost:17005
TRAFFIC_PID=""

cleanup() {
  [[ -n "$TRAFFIC_PID" ]] && kill "$TRAFFIC_PID" 2>/dev/null || true
  docker compose down -v >/dev/null 2>&1 || true
}
trap cleanup EXIT
cleanup

# Instant PromQL query against Prometheus; prints the first result's value (or "none").
promql() {
  local out
  out=$(curl -fs -G "$PROM/api/v1/query" --data-urlencode "query=$1")
  [[ "$out" =~ \"value\":\[[0-9.]+,\"([^\"]+)\"\] ]] && echo "${BASH_REMATCH[1]}" || echo "none"
}

echo "== Starting app, Prometheus and Grafana (first run builds the app image, ~1 min) =="
docker compose up -d --build --wait >/dev/null 2>&1

echo "== Traffic: 100 product requests (5 fail with 500) and 40 checkouts (every 20th takes 1.5 s) =="
# The status="500" series doesn't exist until the first error. If all 5 errors happened before Prometheus
# first scraped it, the series would appear already at 5, and increase()/rate() (which need two samples)
# would show 0 errors. So: one error, wait for it to be scraped, then the rest.
curl -s -o /dev/null "$APP/api/products/boom"
for _ in $(seq 1 30); do
  [[ "$(promql 'sum(http_server_requests_seconds_count{status="500"})')" == "1" ]] && break
  sleep 1
done
for i in $(seq 1 99); do
  if (( i % 20 == 0 )); then curl -s -o /dev/null "$APP/api/products/boom"; else curl -s -o /dev/null "$APP/api/products/$i"; fi
done
for _ in $(seq 1 40); do curl -s -o /dev/null "$APP/api/checkout"; done

# Wait until Prometheus has scraped all of it.
for _ in $(seq 1 30); do
  [[ "$(promql 'sum(http_server_requests_seconds_count{uri="/api/checkout"})')" == "40" ]] && break
  sleep 1
done

# Light background traffic, so the panels' short rate() windows have something to show.
( while true; do curl -s -o /dev/null "$APP/api/products/1"; curl -s -o /dev/null "$APP/api/checkout"; sleep 0.2; done ) &
TRAFFIC_PID=$!
disown "$TRAFFIC_PID"
sleep 5

echo "== Grafana provisioned both dashboards from files =="
search=$(curl -fs -u admin:admin "$GRAFANA/api/search?query=Shop")
[[ "$search" == *'"uid":"service-red"'* && "$search" == *'"uid":"service-broken"'* ]] \
  && pass "/api/search lists service-red and service-broken" \
  || fail "dashboards not provisioned: $search"

echo "== Every panel of the RED/USE dashboard returns data through Grafana =="
dashboard=$(curl -fs -u admin:admin "$GRAFANA/api/dashboards/uid/service-red")
exprs=$(grep -oE '"expr":"(\\.|[^"\\])*"' <<< "$dashboard" || true)
count=0
while IFS= read -r expr; do
  [[ -z "$expr" ]] && continue
  count=$((count + 1))
  body="{\"queries\":[{\"refId\":\"A\",\"datasource\":{\"uid\":\"prometheus\"},$expr,\"instant\":true,\"range\":false}],\"from\":\"now-5m\",\"to\":\"now\"}"
  result=$(curl -fs -u admin:admin -H 'Content-Type: application/json' -d "$body" "$GRAFANA/api/ds/query")
  shown=${expr#\"expr\":\"}; shown=${shown%\"}
  if [[ "$result" =~ \"values\":\[\[[0-9]+\],\[[0-9] ]]; then
    pass "panel query $count returns a number: ${shown:0:70}..."
  else
    fail "panel query $count returned no data: $expr"
  fi
done <<< "$exprs"
(( count == 9 )) && pass "9 panel queries checked" || fail "expected 9 panel queries, found $count"

echo "== What the broken dashboard hides (numbers from Prometheus, last 5 minutes of /api/checkout) =="
avg=$(promql 'sum(increase(http_server_requests_seconds_sum{uri="/api/checkout"}[5m])) / sum(increase(http_server_requests_seconds_count{uri="/api/checkout"}[5m]))')
p99=$(promql 'histogram_quantile(0.99, sum by (le) (rate(http_server_requests_seconds_bucket{uri="/api/checkout"}[5m])))')
if awk -v a="$avg" -v p="$p99" 'BEGIN { exit !(a < 0.25 && p > 1.0) }'; then
  pass "average latency ${avg}s looks fine; p99 is ${p99}s: 1 user in 100 waits over a second"
else
  fail "expected a low average and a high p99 (avg=$avg p99=$p99)"
fi

errors=$(promql 'sum(http_server_requests_seconds_count{status="500"})')
ratio=$(promql 'sum(increase(http_server_requests_seconds_count{uri!~"/actuator.*",status=~"5.."}[5m])) / sum(increase(http_server_requests_seconds_count{uri!~"/actuator.*"}[5m]))')
if [[ "$errors" == "5" ]] && awk -v r="$ratio" 'BEGIN { exit !(r > 0 && r < 0.1) }'; then
  pass "the 'Errors' panel says 5 (of how many?); the error ratio says $ratio"
else
  fail "unexpected error numbers (count=$errors ratio=$ratio)"
fi

with=$(promql 'sum(rate(http_server_requests_seconds_count[1m]))')
without=$(promql 'sum(rate(http_server_requests_seconds_count{uri!~"/actuator.*"}[1m]))')
if awk -v w="$with" -v o="$without" 'BEGIN { exit !(w > o) }'; then
  pass "without uri!~\"/actuator.*\" the request rate counts Prometheus's own scrapes: $with vs $without req/s"
else
  fail "expected actuator traffic in the unfiltered rate ($with vs $without)"
fi

echo
echo "Open $GRAFANA (admin / admin) to look at both dashboards: run 'docker compose up -d' yourself, this script cleans up."
exit $FAILED
