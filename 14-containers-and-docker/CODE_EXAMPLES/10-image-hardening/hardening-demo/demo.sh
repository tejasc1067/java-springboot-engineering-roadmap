#!/usr/bin/env bash
# Builds the same app four ways and checks who it runs as, what it can write, and how big each image is.
set -euo pipefail
cd "$(dirname "$0")"

FAILED=0
pass() { echo "PASS: $*"; }
fail() { echo "FAIL: $*"; FAILED=1; }

cleanup() {
  docker rm -f roadmap14-10-root-c roadmap14-10-nonroot-c roadmap14-10-distroless-c \
    roadmap14-10-readonly-c roadmap14-10-readonly-tmp-c >/dev/null 2>&1 || true
}
trap cleanup EXIT

wait_for() {   # wait_for <port>: poll /hello until the app answers (max ~30 s)
  for _ in $(seq 1 30); do
    curl -fs "http://localhost:$1/hello" && return 0
    sleep 1
  done
  return 1
}

echo "== Building app.jar and four images"
mvn -q package -DskipTests
for variant in jdk root nonroot distroless; do
  docker build -q -f "Dockerfile.$variant" -t "roadmap14-10-$variant" . >/dev/null
done

echo
echo "== 1. Image size"
size_mb() { echo $(( $(docker image inspect -f '{{.Size}}' "$1") / 1000000 )); }
JDK=$(size_mb roadmap14-10-jdk); JRE=$(size_mb roadmap14-10-root); DISTROLESS=$(size_mb roadmap14-10-distroless)
echo "   JDK base: ${JDK} MB   JRE base: ${JRE} MB   distroless: ${DISTROLESS} MB"
[ "$JRE" -lt "$JDK" ] && pass "JRE image is smaller than JDK image" || fail "JRE image not smaller"
[ "$DISTROLESS" -lt "$JRE" ] && pass "distroless image is smaller than JRE image" || fail "distroless not smaller"

echo
echo "== 2. Default image: runs as root"
docker run -d --name roadmap14-10-root-c -p 14010:8080 roadmap14-10-root >/dev/null
echo "   $(wait_for 14010)"
[ "$(docker exec roadmap14-10-root-c id -u)" = "0" ] && pass "app runs as uid 0 (root)" || fail "expected root"
if docker exec roadmap14-10-root-c sh -c 'echo tampered >> /app/app.jar' 2>/dev/null; then
  pass "as root, the app's own jar can be overwritten (an attacker inside the app could replace its code)"
else
  fail "expected root to be able to write /app/app.jar"
fi

echo
echo "== 3. USER app: runs as an unprivileged user"
docker run -d --name roadmap14-10-nonroot-c -p 14101:8080 roadmap14-10-nonroot >/dev/null
echo "   $(wait_for 14101)"
UID_NONROOT=$(docker exec roadmap14-10-nonroot-c id -u)
[ "$UID_NONROOT" != "0" ] && pass "app runs as uid $UID_NONROOT, not root" || fail "still root"
if docker exec roadmap14-10-nonroot-c sh -c 'echo tampered >> /app/app.jar' 2>/dev/null; then
  fail "non-root user could overwrite the jar"
else
  pass "overwriting /app/app.jar is refused: Permission denied"
fi

echo
echo "== 4. Distroless :nonroot: no shell, uid 65532"
docker run -d --name roadmap14-10-distroless-c -p 14102:8080 roadmap14-10-distroless >/dev/null
echo "   $(wait_for 14102)"
DISTROLESS_UID=$(docker top roadmap14-10-distroless-c | awk 'NR==2 {print $1}')
[ "$DISTROLESS_UID" = "65532" ] && pass "app runs as uid 65532" || fail "expected uid 65532, got $DISTROLESS_UID"
if docker exec roadmap14-10-distroless-c sh -c true >/dev/null 2>&1; then
  fail "distroless image has a shell"
else
  pass "no shell in the image: 'docker exec ... sh' fails (nothing for an attacker to run either)"
fi

echo
echo "== 5. Read-only root filesystem"
docker run -d --name roadmap14-10-readonly-c --read-only roadmap14-10-nonroot >/dev/null
sleep 8
READONLY_LOGS=$(docker logs roadmap14-10-readonly-c 2>&1)
if [ "$(docker inspect -f '{{.State.Status}}' roadmap14-10-readonly-c)" = "exited" ] \
   && [[ "$READONLY_LOGS" == *"Unable to start web server"* ]]; then
  pass "--read-only alone: app crashes ('Unable to start web server' - Tomcat needs a writable /tmp)"
else
  fail "expected --read-only without /tmp to crash"
fi
docker run -d --name roadmap14-10-readonly-tmp-c --read-only --tmpfs /tmp -p 14103:8080 roadmap14-10-nonroot >/dev/null
if wait_for 14103 >/dev/null; then
  pass "--read-only --tmpfs /tmp: app runs, and nothing outside /tmp is writable"
else
  fail "read-only container with tmpfs did not start"
fi

echo
[ "$FAILED" -eq 0 ] && echo "All checks passed." || { echo "Some checks failed."; exit 1; }
