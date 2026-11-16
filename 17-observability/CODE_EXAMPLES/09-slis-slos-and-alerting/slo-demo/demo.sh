#!/usr/bin/env bash
# Checks the SLO alerting rules with promtool (shipped inside the Prometheus image), then shows that the
# naive "any 5xx" alert pages on a blip the burn-rate alerts correctly ignore.
set -euo pipefail
cd "$(dirname "$0")"

IMAGE=prom/prometheus:v3.5.0
FAILED=0
pass() { echo "PASS: $*"; }
fail() { echo "FAIL: $*"; FAILED=1; }

cleanup() {
  docker rm -f roadmap17-09-promtool >/dev/null 2>&1 || true
}
trap cleanup EXIT

promtool() {   # promtool <args>: run promtool in a throwaway container with ./rules mounted
  docker run --rm --name roadmap17-09-promtool -v "$PWD/rules:/rules:ro" -w /rules \
    --entrypoint promtool "$IMAGE" "$@" 2>&1
}

echo "== 1. The rule files are valid"
OUT=$(promtool check rules slo-rules.yml naive-rules.yml) || true
echo "$OUT" | sed 's/^/   /'
if echo "$OUT" | grep -q "SUCCESS: 9 rules found" && echo "$OUT" | grep -q "SUCCESS: 1 rules found"; then
  pass "slo-rules.yml (6 recording + 3 alerting rules) and naive-rules.yml parse"
else
  fail "promtool check rules reported a problem"
fi

echo
echo "== 2. The burn-rate alerts behave as designed (6 scenarios in slo-rules.test.yml)"
OUT=$(promtool test rules slo-rules.test.yml) || true
echo "$OUT" | sed 's/^/   /'
if echo "$OUT" | grep -q "SUCCESS" && ! echo "$OUT" | grep -q "FAILED"; then
  pass "fast burn (5% errors) pages within the hour"
  pass "slow burn (0.8% errors) pages on the 6h window, fast burn stays quiet"
  pass "a 2-minute blip pages nobody"
  pass "healthy traffic (0.05% errors) pages nobody"
  pass "after the fix, fast burn clears within minutes; slow burn clears when its 30m window is clean"
  pass "20% of requests over 300 ms pages on the latency SLO"
else
  fail "a burn-rate scenario did not behave as expected (see promtool output above)"
fi

echo
echo "== 3. The naive alert: sum(rate(5xx[1m])) > 0"
OUT=$(promtool test rules naive-rules.test.yml) || true
echo "$OUT" | sed 's/^/   /'
if echo "$OUT" | grep -q "SUCCESS" && ! echo "$OUT" | grep -q "FAILED"; then
  pass "the naive alert pages on the same 2-minute blip, then resolves itself 3 minutes later (noise)"
else
  fail "expected the naive alert to fire on the blip"
fi

echo
[ "$FAILED" -eq 0 ] && echo "All checks passed." || { echo "Some checks FAILED."; exit 1; }
