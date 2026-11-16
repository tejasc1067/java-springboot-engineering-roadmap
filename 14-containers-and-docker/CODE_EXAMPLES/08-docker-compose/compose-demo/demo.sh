#!/usr/bin/env bash
# Topic 08: app + Postgres with Docker Compose. Two broken setups, then the working one.
set -euo pipefail
cd "$(dirname "$0")"

FAILED=0
pass() { echo "PASS: $1"; }
fail() { echo "FAIL: $1"; FAILED=1; }

LOCALHOST=(-f compose.broken-localhost.yaml)
RACE=(-f compose.broken-no-healthcheck.yaml)
GOOD=(-f compose.yaml -f compose.slow-db.yaml)

cleanup() {
  docker compose "${LOCALHOST[@]}" down -v >/dev/null 2>&1 || true
  docker compose "${RACE[@]}" down -v >/dev/null 2>&1 || true
  docker compose "${GOOD[@]}" down -v >/dev/null 2>&1 || true
}
trap cleanup EXIT
cleanup

# Waits (up to 90 s) for the app container of a compose project to exit; prints its exit code.
wait_for_app_exit() {
  for _ in $(seq 1 90); do
    state=$(docker compose "$@" ps -a app --format '{{.State}} {{.ExitCode}}')
    [[ "$state" == exited* ]] && { echo "${state#exited }"; return; }
    sleep 1
  done
  echo "still-running"
}

echo "== Building the app image (first run downloads Maven dependencies, ~1 min) =="
docker compose -f compose.yaml build -q

echo "== Broken 1: JDBC URL says localhost =="
docker compose "${LOCALHOST[@]}" up -d >/dev/null 2>&1
code=$(wait_for_app_exit "${LOCALHOST[@]}")
logs=$(docker compose "${LOCALHOST[@]}" logs app 2>&1)
if [[ "$code" != "0" && "$code" != "still-running" && "$logs" == *"Connection to localhost:5432 refused"* ]]; then
  pass "app exited ($code): 'Connection to localhost:5432 refused' - localhost is the app's own container"
else
  fail "expected the app to fail connecting to localhost (got: $code)"
fi
docker compose "${LOCALHOST[@]}" down -v >/dev/null 2>&1

echo "== Broken 2: depends_on without a healthcheck, and a database that is slow to become ready =="
docker compose "${RACE[@]}" up -d >/dev/null 2>&1
code=$(wait_for_app_exit "${RACE[@]}")
logs=$(docker compose "${RACE[@]}" logs app 2>&1)
if [[ "$code" != "0" && "$code" != "still-running" && "$logs" == *"Connection to db:5432 refused"* ]]; then
  pass "app exited ($code): 'Connection to db:5432 refused' - it started before Postgres was ready"
else
  fail "expected the app to start too early and fail (got: $code)"
fi
docker compose "${RACE[@]}" down -v >/dev/null 2>&1

echo "== Fixed: service name as host + healthcheck + depends_on: service_healthy (same slow database) =="
start=$(date +%s)
docker compose "${GOOD[@]}" up -d --wait >/dev/null 2>&1
echo "Both services healthy after $(( $(date +%s) - start )) s"
curl -fs -X POST -H 'Content-Type: text/plain' -d 'first note' http://localhost:14008/notes >/dev/null
[[ "$(curl -fs http://localhost:14008/notes)" == *"first note"* ]] \
  && pass "app waited for the database, then saved and read a note through http://localhost:14008/notes" \
  || fail "note not saved"

echo "== Data survives 'docker compose down' (the named volume stays) =="
docker compose "${GOOD[@]}" down >/dev/null 2>&1
docker compose "${GOOD[@]}" up -d --wait >/dev/null 2>&1
[[ "$(curl -fs http://localhost:14008/notes)" == *"first note"* ]] \
  && pass "after down + up, the note is still there" \
  || fail "note lost after down + up"

echo "== 'docker compose down -v' deletes the volume too =="
docker compose "${GOOD[@]}" down -v >/dev/null 2>&1
docker compose "${GOOD[@]}" up -d --wait >/dev/null 2>&1
[[ "$(curl -fs http://localhost:14008/notes)" == "[]" ]] \
  && pass "after down -v + up, the database starts empty" \
  || fail "expected an empty database after down -v"

exit $FAILED
