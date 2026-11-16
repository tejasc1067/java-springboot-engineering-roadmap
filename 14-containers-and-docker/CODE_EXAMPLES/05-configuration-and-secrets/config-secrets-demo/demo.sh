#!/usr/bin/env bash
# Lesson: anything put into an image can be read back out of it. Configure containers when they start.
set -euo pipefail
cd "$(dirname "$0")"

FAILED=0
pass() { echo "PASS: $*"; }
fail() { echo "FAIL: $*"; FAILED=1; }
WORK=$(mktemp -d)

cleanup() {
    docker rm -f roadmap14-05-app >/dev/null 2>&1 || true
    docker rmi -f roadmap14-05-baked roadmap14-05-clean roadmap14-05-buildsecret >/dev/null 2>&1 || true
    rm -rf "$WORK"
}
trap cleanup EXIT

# Does any layer of the image contain this exact file? (Exports the image and lists every layer tarball.)
file_in_layers() {
    docker save "$1" -o "$WORK/img.tar"
    rm -rf "$WORK/x" && mkdir "$WORK/x" && tar -xf "$WORK/img.tar" -C "$WORK/x"
    for blob in "$WORK"/x/blobs/sha256/*; do
        if tar -tf "$blob" 2>/dev/null | grep -qx "$2"; then
            tar -xOf "$blob" "$2"
            return 0
        fi
    done
    return 1
}

mvn -q -DskipTests package

echo "== BROKEN: secrets baked into the image"
docker build -q -f Dockerfile.baked --build-arg NPM_TOKEN=npm_t0ken-leaked -t roadmap14-05-baked . >/dev/null

env_leak=$(docker image inspect roadmap14-05-baked --format '{{range .Config.Env}}{{println .}}{{end}}' | grep PASSWORD || true)
echo "  docker image inspect:  $env_leak"
[ -n "$env_leak" ] && pass "ENV secret is readable with docker image inspect" || fail "ENV secret not found"

arg_leak=$(docker history --no-trunc --format '{{.CreatedBy}}' roadmap14-05-baked | grep -m1 -o 'NPM_TOKEN=[^ ]*' || true)
echo "  docker history:        $arg_leak"
[ -n "$arg_leak" ] && pass "build ARG value is recorded in the image history" || fail "ARG value not found"

if docker run --rm --entrypoint ls roadmap14-05-baked /app/application-secrets.yml >/dev/null 2>&1; then
    fail "deleted file is still visible in the container"
else
    pass "the 'deleted' secrets file is not visible inside a running container..."
fi
file_leak=$(file_in_layers roadmap14-05-baked app/application-secrets.yml | grep password || true)
echo "  docker save + tar:     $file_leak"
[ -n "$file_leak" ] && pass "...but it is still inside an earlier layer of the image" || fail "file not found in layers"

echo
echo "== FIXED (build time): a secret mount instead of an ARG"
printf 'npm_t0ken-leaked' > "$WORK/npm_token"
docker build -q -f Dockerfile.buildsecret --secret id=npm_token,src="$WORK/npm_token" -t roadmap14-05-buildsecret . >/dev/null
( docker image inspect roadmap14-05-buildsecret; docker history --no-trunc roadmap14-05-buildsecret ) | grep -q 'npm_t0ken' \
    && fail "build secret leaked into the image" || pass "build used the token, but it is in no layer, history, or config"

echo
echo "== FIXED (run time): one clean image, configured at docker run"
docker build -q -t roadmap14-05-clean . >/dev/null

clean_leaks=$( (docker image inspect roadmap14-05-clean; docker history --no-trunc roadmap14-05-clean) | grep -ciE 's3cret|npm_t0ken' || true)
[ "$clean_leaks" = 0 ] && pass "clean image: no secret in inspect or history" || fail "clean image leaks ($clean_leaks matches)"
file_in_layers roadmap14-05-clean app/application-secrets.yml >/dev/null && fail "clean image contains the secrets file" \
                                                                          || pass "clean image: no secrets file in any layer"

# Non-secret config: an env file. Secret: a read-only mounted file, loaded with Spring's configtree.
docker run -d --name roadmap14-05-app -p 14005:8080 \
    --env-file runtime.env \
    -e SPRING_CONFIG_IMPORT=configtree:/run/secrets/ \
    -v "$PWD/runtime-secrets:/run/secrets:ro" \
    roadmap14-05-clean >/dev/null

for _ in $(seq 1 30); do
    config=$(curl -s localhost:14005/config || true)
    [ -n "$config" ] && break
    sleep 1
done
echo "  GET /config: $config"
echo "$config" | grep -q '"profiles":"\[prod\]"'                       && pass "profile came from SPRING_PROFILES_ACTIVE" || fail "profile not set"
echo "$config" | grep -q '"mode":"production"'                         && pass "application-prod.yml in the jar was applied" || fail "prod profile file not applied"
echo "$config" | grep -q '"greeting":"hello from the environment"'      && pass "APP_GREETING env var overrode app.greeting" || fail "env override missing"
echo "$config" | grep -q '"dbPasswordSet":true'                        && pass "password loaded from the mounted file" || fail "password not loaded"

# Environment variables are visible to anyone who can run `docker inspect` on this host. The file isn't.
docker inspect roadmap14-05-app --format '{{range .Config.Env}}{{println .}}{{end}}' | grep -q APP_GREETING \
    && pass "env vars show up in docker inspect (fine for non-secrets)" || fail "env var not visible"
docker inspect roadmap14-05-app | grep -q 'Sup3rS3cret-mounted' \
    && fail "mounted secret visible in docker inspect" || pass "mounted secret value does not appear in docker inspect"

exit $FAILED
