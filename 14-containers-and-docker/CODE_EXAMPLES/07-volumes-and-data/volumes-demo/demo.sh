#!/usr/bin/env bash
# Topic 07: what happens to a database's data when its container is removed,
# with no volume, with a named volume, and with a bind mount.
set -euo pipefail
cd "$(dirname "$0")"

VOLUME=roadmap14-07-data
FAILED=0
pass() { echo "PASS: $1"; }
fail() { echo "FAIL: $1"; FAILED=1; }

cleanup() {
  docker rm -f -v roadmap14-07-layer roadmap14-07-layer2 roadmap14-07-pg-a roadmap14-07-pg-b roadmap14-07-pg-c roadmap14-07-pg-d >/dev/null 2>&1 || true
  docker volume rm "$VOLUME" >/dev/null 2>&1 || true
  rm -rf ./host-folder
}
trap cleanup EXIT
cleanup

# Waits until Postgres accepts TCP connections. (During first start-up the image runs a temporary
# server on a Unix socket only, so checking over TCP avoids talking to that temporary server.)
wait_for_pg() {
  for _ in $(seq 1 60); do
    docker exec "$1" pg_isready -q -h 127.0.0.1 -U postgres && return 0
    sleep 1
  done
  echo "Postgres in $1 never became ready"; exit 1
}

start_pg() {   # start_pg <container-name> [extra docker run args...]
  local name=$1; shift
  docker run -d --name "$name" -e POSTGRES_PASSWORD=secret "$@" postgres:17 >/dev/null
  wait_for_pg "$name"
}

sql() { docker exec "$1" psql -U postgres -tAc "$2"; }

echo "== 0. A file in the container's writable layer =="
docker run --name roadmap14-07-layer postgres:17 bash -c 'echo "order #1" > /tmp/orders.txt' >/dev/null
docker start -a roadmap14-07-layer >/dev/null      # stop + start: SAME container, same writable layer
docker cp roadmap14-07-layer:/tmp/orders.txt - >/dev/null 2>&1 \
  && pass "stop/start keeps the file: it's the same container" \
  || fail "file missing after restart"
docker rm roadmap14-07-layer >/dev/null            # remove: the writable layer is deleted with it
docker create --name roadmap14-07-layer2 postgres:17 >/dev/null
docker cp roadmap14-07-layer2:/tmp/orders.txt - >/dev/null 2>&1 \
  && fail "file survived container removal (unexpected)" \
  || pass "remove + new container: the file is gone"
docker rm roadmap14-07-layer2 >/dev/null

echo "== 1. No volume given =="
start_pg roadmap14-07-pg-a
sql roadmap14-07-pg-a "create table customer (name text); insert into customer values ('Ann');" >/dev/null
echo "Rows before removal: $(sql roadmap14-07-pg-a 'select count(*) from customer')"
# postgres:17 declares VOLUME /var/lib/postgresql/data, so Docker silently created an ANONYMOUS volume.
ANON=$(docker inspect -f '{{range .Mounts}}{{.Name}}{{end}}' roadmap14-07-pg-a)
echo "Anonymous volume Docker created: ${ANON:0:12}..."
docker rm -f roadmap14-07-pg-a >/dev/null           # what a redeploy does: old container gone
start_pg roadmap14-07-pg-b                          # same image, new container -> a NEW anonymous volume
if sql roadmap14-07-pg-b "select count(*) from customer" >/dev/null 2>&1; then
  fail "table survived without a volume (unexpected)"
else
  pass "no volume: new container has no 'customer' table"
fi
if docker volume inspect "$ANON" >/dev/null 2>&1; then
  pass "the old data still exists in orphaned volume ${ANON:0:12}... - nothing will ever mount it again"
  docker volume rm "$ANON" >/dev/null
else
  fail "expected the anonymous volume to outlive its container"
fi
docker rm -f -v roadmap14-07-pg-b >/dev/null

echo "== 2. Named volume: the data lives outside the container =="
start_pg roadmap14-07-pg-c -v "$VOLUME:/var/lib/postgresql/data"
sql roadmap14-07-pg-c "create table customer (name text); insert into customer values ('Ann');" >/dev/null
docker rm -f roadmap14-07-pg-c >/dev/null
start_pg roadmap14-07-pg-d -v "$VOLUME:/var/lib/postgresql/data"
rows=$(sql roadmap14-07-pg-d "select count(*) from customer")
[[ "$rows" == "1" ]] && pass "named volume: new container still sees 1 row" || fail "named volume: expected 1 row, got $rows"
echo "Where Docker keeps it: $(docker volume inspect -f '{{.Mountpoint}}' "$VOLUME") (inside Docker's VM on Mac/Windows)"
docker rm -f roadmap14-07-pg-d >/dev/null

echo "== 3. Bind mount: a host folder appears inside the container =="
mkdir -p ./host-folder
docker run --rm -v "$PWD/host-folder:/export" postgres:17 \
  bash -c 'echo "written inside the container" > /export/report.txt'
[[ -f ./host-folder/report.txt ]] \
  && pass "bind mount: file written in the container is on the host: $(cat ./host-folder/report.txt)" \
  || fail "bind mount: file not found on host"

exit $FAILED
