#!/usr/bin/env bash
# Lesson: a container has its own network. Publish ports to reach it from the host;
# use a user-defined network and container names to reach one container from another.
set -euo pipefail
cd "$(dirname "$0")"

IMAGE=roadmap14-06-app
NET=roadmap14-06-net
FAILED=0
pass() { echo "PASS: $*"; }
fail() { echo "FAIL: $*"; FAILED=1; }

cleanup() {
    docker rm -f $(docker ps -aq --filter name=roadmap14-06-) >/dev/null 2>&1 || true
    docker network rm $NET >/dev/null 2>&1 || true
    docker rmi -f $IMAGE >/dev/null 2>&1 || true
}
trap cleanup EXIT

start() { local name=$1; shift; docker run -d --name "$name" "$@" $IMAGE >/dev/null; }
# Wait until the app answers INSIDE its own container (curl is in the eclipse-temurin image).
wait_ready() {
    for _ in $(seq 1 40); do
        docker exec "$1" curl -s -o /dev/null localhost:8080/hello 2>/dev/null && return 0
        sleep 0.5
    done
    echo "  $1 never started:"; docker logs --tail 5 "$1"; return 1
}
# Call from the HOST. Prints the body, or curl's error.
from_host() { curl -sS -m 3 "localhost:$1/$2" 2>&1 || true; }

mvn -q -DskipTests package
docker build -q -t $IMAGE . >/dev/null

echo "== From the host into a container"

start roadmap14-06-unpublished
wait_ready roadmap14-06-unpublished
r=$(from_host 14006 hello); echo "  no -p:                         $r"
[[ "$r" == *"hello from"* ]] && fail "reachable without -p" || pass "without -p the app runs but the host can't reach it"
docker rm -f roadmap14-06-unpublished >/dev/null

start roadmap14-06-published -p 14006:8080
wait_ready roadmap14-06-published
r=$(from_host 14006 hello); echo "  -p 14006:8080:                 $r"
[ "$r" = "hello from app" ] && pass "-p 14006:8080 forwards host port 14006 to container port 8080" || fail "published port didn't answer"

err=$(docker run -d --name roadmap14-06-clash -p 14006:8080 $IMAGE 2>&1 >/dev/null || true)
echo "  second container on 14006:     $(echo "$err" | head -1 | sed 's/.*: //')"
[[ "$err" == *"already allocated"* ]] && pass "two containers can't publish the same host port" || fail "expected a port clash, got: $err"
docker rm -f roadmap14-06-clash >/dev/null 2>&1 || true

start roadmap14-06-loopback -p 14061:8080 -e SERVER_ADDRESS=127.0.0.1
wait_ready roadmap14-06-loopback
inside=$(docker exec roadmap14-06-loopback curl -s localhost:8080/hello)
r=$(from_host 14061 hello)
echo "  SERVER_ADDRESS=127.0.0.1:      inside the container: '$inside' | from the host: $r"
[ "$inside" = "hello from app" ] && [[ "$r" != *"hello from"* ]] \
    && pass "an app bound to 127.0.0.1 answers inside its container but not through the published port" \
    || fail "loopback binding behaved unexpectedly"

echo
echo "== From one container to another"

start roadmap14-06-inventory -p 14062:8080 -e APP_NAME=inventory
wait_ready roadmap14-06-inventory
r=$(from_host 14062 hello); echo "  host -> inventory:             $r"

start roadmap14-06-orders -e APP_NAME=orders -e OTHER_URL=http://localhost:14062
wait_ready roadmap14-06-orders
r=$(docker exec roadmap14-06-orders curl -s localhost:8080/call-other); echo "  orders -> localhost:14062:     $r"
[[ "$r" == FAILED* ]] && pass "inside a container, localhost is that container - not your laptop" || fail "localhost call should fail"
docker rm -f roadmap14-06-orders >/dev/null

start roadmap14-06-orders -e APP_NAME=orders -e OTHER_URL=http://roadmap14-06-inventory:8080
wait_ready roadmap14-06-orders
r=$(docker exec roadmap14-06-orders curl -s localhost:8080/call-other); echo "  default network, by name:      $r"
[[ "$r" == *UnresolvedAddress* ]] && pass "on the default bridge network, container names don't resolve" || fail "expected the name not to resolve"
docker rm -f roadmap14-06-orders roadmap14-06-inventory >/dev/null

docker network create $NET >/dev/null
# No -p on inventory: other containers on the network reach it directly. -p is only for the host.
start roadmap14-06-inventory --network $NET -e APP_NAME=inventory
start roadmap14-06-orders --network $NET -p 14063:8080 -e APP_NAME=orders -e OTHER_URL=http://roadmap14-06-inventory:8080
wait_ready roadmap14-06-inventory
wait_ready roadmap14-06-orders
r=$(from_host 14063 call-other); echo "  user network, by name:         $r"
[ "$r" = "hello from inventory" ] && pass "on a user-defined network, orders reaches inventory by container name" || fail "name call failed"
[ -z "$(docker port roadmap14-06-inventory)" ] && pass "inventory published no ports and was still reachable from orders" || fail "inventory has published ports"

exit $FAILED
