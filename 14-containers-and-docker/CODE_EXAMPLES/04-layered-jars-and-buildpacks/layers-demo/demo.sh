#!/usr/bin/env bash
# Lesson: a fat-jar image re-ships all 20 MB on every code change; a layered image re-ships ~KB.
set -euo pipefail
cd "$(dirname "$0")"

APP=src/main/java/com/example/layers/LayersApplication.java
FAILED=0
pass() { echo "PASS: $*"; }
fail() { echo "FAIL: $*"; FAILED=1; }

cleanup() {
    sed -i.bak 's/"hello v2"/"hello v1"/' "$APP" && rm -f "$APP.bak"
    docker rm -f roadmap14-04-run >/dev/null 2>&1 || true
    docker rmi -f roadmap14-04-fatjar:v1 roadmap14-04-fatjar:v2 \
                  roadmap14-04-layered:v1 roadmap14-04-layered:v2 >/dev/null 2>&1 || true
}
trap cleanup EXIT

# Layer ids (content digests) of an image, one per line, base image layers included.
layers() { docker image inspect --format '{{range .RootFS.Layers}}{{println .}}{{end}}' "$1" | sed '/^$/d'; }
# Size of each layer the Dockerfile added, newest first (docker history lists newest first).
sizes() { docker history --format '{{.Size}}\t{{.CreatedBy}}' "$1"; }

build_both() {
    mvn -q -DskipTests package
    docker build -q -f Dockerfile.fatjar  -t "roadmap14-04-fatjar:$1"  . >/dev/null
    docker build -q -f Dockerfile.layered -t "roadmap14-04-layered:$1" . >/dev/null
}

echo "== build v1"
build_both v1

echo "== change one line of code, build v2"
sed -i.bak 's/"hello v1"/"hello v2"/' "$APP" && rm -f "$APP.bak"
build_both v2

echo
echo "-- fat jar image, v2 layers (newest first):"
sizes roadmap14-04-fatjar:v2 | head -3
echo "-- layered image, v2 layers (newest first):"
sizes roadmap14-04-layered:v2 | head -6
echo

# Layers that exist in v2 but not in v1 = what a registry push / pull must transfer.
new_layers() { comm -13 <(layers "$1:v1" | sort) <(layers "$1:v2" | sort) | wc -l | tr -d ' '; }

fat_new=$(new_layers roadmap14-04-fatjar)
layered_new=$(new_layers roadmap14-04-layered)
echo "new layers after a one-line change: fat jar = $fat_new, layered = $layered_new"

[ "$fat_new" = 1 ] && pass "fat jar image: the whole jar layer changed" || fail "fat jar image changed $fat_new layers"
[ "$layered_new" = 1 ] && pass "layered image: only one layer changed" || fail "layered image changed $layered_new layers"

# Which one? The dependencies layer (4th from the end of the layer list) must be identical in v1 and v2.
deps_v1=$(layers roadmap14-04-layered:v1 | tail -4 | head -1)
deps_v2=$(layers roadmap14-04-layered:v2 | tail -4 | head -1)
[ "$deps_v1" = "$deps_v2" ] && pass "layered image: dependencies layer reused (${deps_v2:7:12})" \
                            || fail "dependencies layer was rebuilt"

# The newest COPY layer is the one that changed in each image.
changed_size=$(sizes roadmap14-04-layered:v2 | grep COPY | head -1 | cut -f1)
fat_size=$(sizes roadmap14-04-fatjar:v2 | grep COPY | head -1 | cut -f1)
echo "bytes to ship for the change: fat jar = $fat_size, layered = $changed_size"

# The layered image still runs the app.
docker run -d --name roadmap14-04-run -p 14004:8080 roadmap14-04-layered:v2 >/dev/null
for _ in $(seq 1 30); do
    body=$(curl -s localhost:14004/hello || true)
    [ -n "$body" ] && break
    sleep 1
done
[ "$body" = "hello v2" ] && pass "layered image starts and serves the new code: $body" || fail "layered app answered '$body'"

exit $FAILED
