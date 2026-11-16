#!/usr/bin/env bash
# Topic 14-01: "works on my machine" - and how an image pins the runtime.
set -euo pipefail
cd "$(dirname "$0")"

IMAGE=roadmap14-01-hello
FAILED=0
pass() { echo "PASS: $*"; }
fail() { echo "FAIL: $*"; FAILED=1; }
cleanup() { rm -rf out; docker image rm -f "$IMAGE" >/dev/null 2>&1 || true; }
trap cleanup EXIT

echo "== 1. Compile Hello.java with JDK 21 (inside a container, so you don't need JDK 21 installed)"
docker run --rm -v "$PWD":/src -w /src eclipse-temurin:21-jdk javac -d out Hello.java
echo "   host machine: $(uname -s) $(uname -m)"

echo "== 2. Run the compiled class on a 'server' that has Java 17"
if output=$(docker run --rm -v "$PWD/out":/app eclipse-temurin:17-jre java -cp /app Hello 2>&1); then
    fail "expected Java 17 to refuse a class compiled for Java 21"
else
    echo "$output" | grep -m1 "UnsupportedClassVersionError" | cut -c1-160
    echo "$output" | grep -q "UnsupportedClassVersionError" \
        && pass "Java 17 refuses it: UnsupportedClassVersionError (works on my machine, not on the server)" \
        || fail "unexpected error: $output"
fi

echo "== 3. Package program + Java 21 runtime into one image, then run the image"
docker build -q -t "$IMAGE" . >/dev/null
output=$(docker run --rm "$IMAGE")
echo "$output" | sed 's/^/   /'
echo "$output" | grep -q "Hello from Java 21" && pass "the image runs it: the runtime travels with the program" || fail "image did not run: $output"
echo "$output" | grep -q "on Linux" && pass "inside the container the OS is Linux, whatever the host is" || fail "expected Linux inside the container"

echo "== 4. A container starts in about a second (no OS to boot)"
start=$(date +%s)
docker run --rm "$IMAGE" >/dev/null
elapsed=$(( $(date +%s) - start ))
[ "$elapsed" -lt 10 ] && pass "container started, ran, and exited in under $((elapsed + 1))s" || fail "container took ${elapsed}s"

exit $FAILED
