#!/usr/bin/env bash
# Topic 14-02: images are read-only layers; containers add a throwaway writable layer on top.
set -euo pipefail
cd "$(dirname "$0")"

SLOW=roadmap14-02-slow-order
FAST=roadmap14-02-fast-order
WORK=$(mktemp -d)
FAILED=0
pass() { echo "PASS: $*"; }
fail() { echo "FAIL: $*"; FAILED=1; }
cleanup() {
    docker rm -f roadmap14-02-c1 roadmap14-02-c2 roadmap14-02-c3 >/dev/null 2>&1 || true
    docker image rm -f "$SLOW" "$FAST" >/dev/null 2>&1 || true
    rm -rf "$WORK"
}
trap cleanup EXIT

# Build from a temporary copy, because step 4 edits a file and we don't want to change the repo.
cp -R context/. "$WORK"/
build() { docker build --progress=plain -f "$WORK/$1" -t "$2" "$WORK" 2>&1; }

echo "== 1. Build an image. Each instruction in the Dockerfile becomes a layer."
build Dockerfile.fast-order "$FAST" >/dev/null
docker history --format 'table {{.CreatedBy}}\t{{.Size}}' "$FAST" | head -5 | cut -c1-90
docker history --format '{{.CreatedBy}}' "$FAST" | grep -q "COPY greeting.txt" \
    && pass "docker history shows one layer per instruction, newest on top" || fail "layers not found"

echo "== 2. Containers from the same image each get their own writable layer"
docker run -d --name roadmap14-02-c1 "$FAST" sleep infinity >/dev/null
docker run -d --name roadmap14-02-c2 "$FAST" sleep infinity >/dev/null
docker exec roadmap14-02-c1 sh -c 'echo "written in c1" > /app/note.txt'
docker diff roadmap14-02-c1 | sed 's/^/   docker diff c1: /'
docker diff roadmap14-02-c1 | grep -q "A /app/note.txt" && pass "c1's change is recorded in c1's own layer" || fail "docker diff did not show note.txt"
docker exec roadmap14-02-c2 test -f /app/note.txt && fail "c2 can see c1's file" || pass "c2 cannot see the file c1 wrote"

echo "== 3. Removing a container throws its writable layer away; the image never changed"
docker rm -f roadmap14-02-c1 >/dev/null
docker run -d --name roadmap14-02-c3 "$FAST" sleep infinity >/dev/null
docker exec roadmap14-02-c3 test -f /app/note.txt && fail "the file survived" || pass "a new container from the same image has no note.txt (data in a container is temporary - topic 07)"

echo "== 4. Two images FROM the same base share the base layers on disk"
build Dockerfile.slow-order "$SLOW" >/dev/null
base=$(docker image inspect --format '{{len .RootFS.Layers}}' eclipse-temurin:21-jre)
slow_base=$(docker image inspect --format '{{json .RootFS.Layers}}' "$SLOW" | tr -d '[]"' | cut -d, -f1-"$base")
fast_base=$(docker image inspect --format '{{json .RootFS.Layers}}' "$FAST" | tr -d '[]"' | cut -d, -f1-"$base")
[ "$slow_base" = "$fast_base" ] && pass "both images start with the same $base base layers, stored once" || fail "base layers differ"

echo "== 5. Build cache: change one small file and rebuild both images"
# The build cache is keyed on file CONTENT, so the edit must be new each run (a timestamp) to miss the cache.
EDITED="Hello again - edited at $(date +%s)"
echo "$EDITED" > "$WORK/greeting.txt"
t0=$SECONDS; slow_log=$(build Dockerfile.slow-order "$SLOW"); slow_time=$((SECONDS - t0))
t0=$SECONDS; fast_log=$(build Dockerfile.fast-order "$FAST"); fast_time=$((SECONDS - t0))
echo "   slow-order rebuild: ${slow_time}s   fast-order rebuild: ${fast_time}s"
[ "$slow_time" -ge 5 ] && [ "$fast_time" -lt 5 ] && pass "the rebuild times match: only slow-order paid for the 5s step again" || fail "unexpected rebuild times"
echo "$slow_log" | grep -E "RUN sleep 5" -A1 | grep -q CACHED \
    && fail "slow-order reused the slow step" \
    || pass "slow-order (COPY . . first): editing greeting.txt re-ran the slow dependency step"
echo "$fast_log" | grep -E "RUN sleep 5" -A1 | grep -q CACHED \
    && pass "fast-order (dependency list first): the slow step came from cache" \
    || fail "fast-order did not use the cache"
[ "$(docker run --rm "$FAST")" = "$EDITED" ] && pass "and the rebuilt image has the new file" || fail "new content missing"

exit $FAILED
