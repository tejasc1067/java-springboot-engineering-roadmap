#!/usr/bin/env bash
# Topic 14-03: a naive single-stage Dockerfile vs a multi-stage one, for the same Spring Boot app.
set -euo pipefail
cd "$(dirname "$0")"

NAIVE=roadmap14-03-naive
MULTI=roadmap14-03-multistage
WORK=$(mktemp -d)
FAILED=0
pass() { echo "PASS: $*"; }
fail() { echo "FAIL: $*"; FAILED=1; }
cleanup() {
    docker rm -f roadmap14-03-naive roadmap14-03-multistage >/dev/null 2>&1 || true
    docker image rm -f "$NAIVE" "$MULTI" >/dev/null 2>&1 || true
    rm -rf "$WORK"
}
trap cleanup EXIT

# Build from a temporary copy, because step 5 edits a source file.
cp -R pom.xml src Dockerfile Dockerfile.naive .dockerignore "$WORK"/
build() { docker build --progress=plain -f "$WORK/$1" -t "$2" "$WORK" 2>&1; }
size_mb() { echo $(( $(docker image inspect --format '{{.Size}}' "$1") / 1000000 )); }
wait_for() {   # wait_for PORT: poll /hello until the app answers (Spring Boot takes a few seconds to start)
    for _ in $(seq 1 60); do curl -fs "http://localhost:$1/hello" && return 0; sleep 1; done; return 1
}

echo "== 1. Build both images (first run downloads Maven dependencies: a minute or two)"
build Dockerfile.naive "$NAIVE" >/dev/null
build Dockerfile "$MULTI" >/dev/null

echo "== 2. Both serve the same app"
docker run -d --name roadmap14-03-naive -p 14031:8080 "$NAIVE" >/dev/null
docker run -d --name roadmap14-03-multistage -p 14032:8080 "$MULTI" >/dev/null
[ "$(wait_for 14031)" = "Hello from a container" ] && pass "naive image answers on :14031" || fail "naive image did not answer"
[ "$(wait_for 14032)" = "Hello from a container" ] && pass "multi-stage image answers on :14032" || fail "multi-stage image did not answer"

echo "== 3. Image size"
naive_mb=$(size_mb "$NAIVE"); multi_mb=$(size_mb "$MULTI")
echo "   naive: ${naive_mb} MB   multi-stage: ${multi_mb} MB"
[ "$multi_mb" -lt "$naive_mb" ] && pass "multi-stage image is $((naive_mb - multi_mb)) MB smaller" || fail "multi-stage is not smaller"

echo "== 4. What each image ships to production"
in_image() { docker run --rm --entrypoint sh "$1" -c "$2" >/dev/null 2>&1; }
in_image "$NAIVE" 'test -f /app/src/main/java/com/example/dockerfile/DockerfileDemoApplication.java' \
    && pass "naive image contains your source code" || fail "expected source code in naive image"
in_image "$NAIVE" 'test -d /root/.m2/repository' \
    && pass "naive image contains the whole Maven repository ($(docker run --rm --entrypoint sh "$NAIVE" -c 'du -sm /root/.m2 | cut -f1') MB)" \
    || fail "expected ~/.m2 in naive image"
in_image "$NAIVE" 'command -v mvn && command -v javac' \
    && pass "naive image contains Maven and a full JDK (compiler included)" || fail "expected mvn and javac in naive image"
in_image "$MULTI" 'test -e /app/src || test -d /root/.m2 || command -v mvn || command -v javac' \
    && fail "multi-stage image contains build tools or source" \
    || pass "multi-stage image has no source, no ~/.m2, no Maven, no javac - just a JRE and app.jar"

echo "== 5. Change one line of Java and rebuild both"
# Unique text per run: the build cache is keyed on file content, so a repeated edit would hit old cache.
NEW_TEXT="Hello from a container rebuilt at $(date +%s)"
sed -i.bak "s/Hello from a container/$NEW_TEXT/" "$WORK/src/main/java/com/example/dockerfile/DockerfileDemoApplication.java"
rm "$WORK/src/main/java/com/example/dockerfile/DockerfileDemoApplication.java.bak"
t0=$SECONDS; build Dockerfile.naive "$NAIVE" >/dev/null; naive_time=$((SECONDS - t0))
t0=$SECONDS; build Dockerfile "$MULTI" >/dev/null; multi_time=$((SECONDS - t0))
echo "   naive rebuild: ${naive_time}s   multi-stage rebuild: ${multi_time}s"
[ "$multi_time" -lt "$naive_time" ] \
    && pass "naive re-downloaded every dependency; multi-stage reused them from its cache mount" \
    || fail "expected the multi-stage rebuild to be faster"
docker rm -f roadmap14-03-multistage >/dev/null
docker run -d --name roadmap14-03-multistage -p 14032:8080 "$MULTI" >/dev/null
[ "$(wait_for 14032)" = "$NEW_TEXT" ] && pass "the rebuilt multi-stage image serves the changed code" || fail "rebuilt image serves old code"

exit $FAILED
