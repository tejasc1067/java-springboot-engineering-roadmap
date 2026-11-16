#!/usr/bin/env bash
# Topic 09: a container's health status, and what happens to an in-flight request when it is stopped.
set -euo pipefail
cd "$(dirname "$0")"

PORT=14009
NAME=roadmap14-09-run
FAILED=0
pass() { echo "PASS: $1"; }
fail() { echo "FAIL: $1"; FAILED=1; }

cleanup() { docker rm -f "$NAME" >/dev/null 2>&1 || true; rm -f .request-result; }
trap cleanup EXIT
cleanup

now() { python3 -c 'import time; print(time.time())'; }
seconds_since() { python3 -c "print(round($(now) - $1, 1))"; }

wait_until_up() {
  for _ in $(seq 1 60); do
    curl -fs "http://localhost:$PORT/actuator/health" >/dev/null 2>&1 && return 0
    sleep 1
  done
  echo "app never came up"; exit 1
}

# Starts GET /slow (takes 3 s), stops the container 1 s into it, and reports what happened.
stop_during_request() {
  ( if curl -s -w ' HTTP %{http_code}' "http://localhost:$PORT/slow" > .request-result 2>/dev/null; then rc=0; else rc=$?; fi
    echo " (curl exit $rc)" >> .request-result ) &
  sleep 1
  local start; start=$(now)
  docker stop -t 10 "$NAME" >/dev/null       # SIGTERM, then SIGKILL after 10 s if still running
  STOP_SECONDS=$(seconds_since "$start")
  wait
  REQUEST=$(cat .request-result)
  EXIT_CODE=$(docker inspect -f '{{.State.ExitCode}}' "$NAME")
  LOGS=$(docker logs "$NAME" 2>&1)
  echo "   stop took ${STOP_SECONDS}s, exit code $EXIT_CODE, request: $REQUEST"
}

echo "== Building images (first run downloads Maven dependencies, ~1 min) =="
docker build -q -t roadmap14-09-app . >/dev/null
docker build -q -t roadmap14-09-shell -f Dockerfile.shell-form . >/dev/null

echo "== 1. HEALTHCHECK: 'running' is not the same as 'ready' =="
docker run -d --name "$NAME" -p "$PORT:8080" roadmap14-09-app >/dev/null
first=$(docker inspect -f '{{.State.Health.Status}}' "$NAME")
for _ in $(seq 1 60); do
  [[ "$(docker inspect -f '{{.State.Health.Status}}' "$NAME")" == "healthy" ]] && break
  sleep 1
done
last=$(docker inspect -f '{{.State.Health.Status}}' "$NAME")
[[ "$first" == "starting" && "$last" == "healthy" ]] \
  && pass "health went '$first' -> '$last' while the container was 'running' the whole time" \
  || fail "expected starting -> healthy, got $first -> $last"
probes=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$PORT/actuator/health/readiness")
[[ "$probes" == "200" ]] && pass "/actuator/health/readiness answers 200 (Kubernetes probes, topic 12)" \
  || fail "readiness probe endpoint returned $probes"

echo "== 2. Exec-form ENTRYPOINT + graceful shutdown (the default): stop during a 3 s request =="
stop_during_request
[[ "$REQUEST" == "finished HTTP 200"* && "$LOGS" == *"Commencing graceful shutdown"* && "$EXIT_CODE" == "143" ]] \
  && pass "in-flight request finished (200), then the app shut down on SIGTERM (exit 143) after ${STOP_SECONDS}s" \
  || fail "graceful shutdown did not behave as expected"
docker rm "$NAME" >/dev/null

echo "== 3. BROKEN: server.shutdown=immediate =="
docker run -d --name "$NAME" -p "$PORT:8080" -e SERVER_SHUTDOWN=immediate roadmap14-09-app >/dev/null
wait_until_up
stop_during_request
[[ "$REQUEST" != *"HTTP 200"* ]] \
  && pass "in-flight request was dropped - the caller got no response" \
  || fail "expected the request to be cut off"
docker rm "$NAME" >/dev/null

echo "== 4. BROKEN: shell-form ENTRYPOINT (java is not PID 1) =="
docker run -d --name "$NAME" -p "$PORT:8080" roadmap14-09-shell >/dev/null
wait_until_up
pid1=$(docker exec "$NAME" cat /proc/1/comm)
[[ "$pid1" == "sh" ]] && pass "PID 1 is '$pid1', not java" || fail "PID 1 is $pid1"
stop_during_request
[[ "$EXIT_CODE" == "137" && "$LOGS" != *"Commencing graceful shutdown"* ]] \
  && pass "SIGTERM never reached java: Docker waited the full timeout (${STOP_SECONDS}s) and SIGKILLed it (exit 137)" \
  || fail "expected a SIGKILL after the timeout"

exit $FAILED
