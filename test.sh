#!/usr/bin/env bash
# test.sh — runs unit tests + smoke tests and writes a Markdown report
# Usage:
#   ./test.sh               # run everything, print report to stdout
#   ./test.sh --no-unit     # skip mvnw (useful when running against a live API only)
#   ./test.sh --out FILE    # also write report to FILE (default: test-report.md)
set -euo pipefail

BASE_URL="https://api.pocketnutrition.org"
OUT_FILE="test-report.md"
RUN_UNIT=true

for arg in "$@"; do
  case $arg in
    --no-unit) RUN_UNIT=false ;;
    --out)     shift; OUT_FILE="${1:-test-report.md}" ;;
  esac
done

PASS=0
FAIL=0
LINES=()

# ── helpers ─────────────────────────────────────────────────────────────────

ts() { date -u +"%Y-%m-%dT%H:%M:%SZ"; }

line() { LINES+=("$1"); }

# assert_http <label> <expected_status> <curl_args...>
# Checks HTTP status code; captures body for display.
assert_http() {
  local label="$1" expected="$2"; shift 2
  local resp http_code body
  resp=$(curl -s -w "\n__STATUS__%{http_code}" "$@") || true
  http_code=$(echo "$resp" | tail -1 | sed 's/__STATUS__//')
  body=$(echo "$resp" | sed '$d')
  local pretty
  pretty=$(echo "$body" | jq . 2>/dev/null || echo "$body")

  if [[ "$http_code" == "$expected" ]]; then
    PASS=$((PASS + 1))
    line "### ✅ $label"
    line "- Expected status: \`$expected\` — got \`$http_code\`"
  else
    FAIL=$((FAIL + 1))
    line "### ❌ $label"
    line "- Expected status: \`$expected\` — got \`$http_code\`"
  fi
  line ""
  line '```json'
  line "$pretty"
  line '```'
  line ""
}

# assert_field <label> <expected_status> <jq_filter> <expected_value> <curl_args...>
# Checks HTTP status AND a jq expression on the response body.
assert_field() {
  local label="$1" expected_status="$2" jq_filter="$3" expected_val="$4"; shift 4
  local resp http_code body actual
  resp=$(curl -s -w "\n__STATUS__%{http_code}" "$@") || true
  http_code=$(echo "$resp" | tail -1 | sed 's/__STATUS__//')
  body=$(echo "$resp" | sed '$d')
  local pretty
  pretty=$(echo "$body" | jq . 2>/dev/null || echo "$body")
  actual=$(echo "$body" | jq -r "$jq_filter" 2>/dev/null || echo "parse_error")

  local status_ok=true field_ok=true
  [[ "$http_code" != "$expected_status" ]] && status_ok=false
  [[ "$actual" != "$expected_val" ]]       && field_ok=false

  if $status_ok && $field_ok; then
    PASS=$((PASS + 1))
    line "### ✅ $label"
  else
    FAIL=$((FAIL + 1))
    line "### ❌ $label"
  fi
  line "- Status: expected \`$expected_status\` → got \`$http_code\`"
  line "- \`$jq_filter\`: expected \`$expected_val\` → got \`$actual\`"
  line ""
  line '```json'
  line "$pretty"
  line '```'
  line ""
}

# ── report header ────────────────────────────────────────────────────────────

line "# Test report — pocket-nutrition-api"
line ""
line "> Generated: $(ts)"
line "> Base URL: \`$BASE_URL\`"
line ""

# ── 1. Unit tests ─────────────────────────────────────────────────────────────

line "## 1. Unit tests"
line ""

if $RUN_UNIT; then
  line '```'
  mvn_out=$(./mvnw test 2>&1) && mvn_exit=0 || mvn_exit=$?
  summary=$(echo "$mvn_out" | grep -E "Tests run:|BUILD" | tail -5)
  line "$summary"
  line '```'
  line ""
  if [[ $mvn_exit -eq 0 ]]; then
    PASS=$((PASS + 1))
    line "### ✅ Unit tests passed"
  else
    FAIL=$((FAIL + 1))
    line "### ❌ Unit tests failed"
    line ""
    line '```'
    line "$(echo "$mvn_out" | grep -A 5 "FAILED\|ERROR" | head -40)"
    line '```'
  fi
else
  line "_Skipped (--no-unit)_"
fi
line ""

# ── 2. Smoke tests ────────────────────────────────────────────────────────────

line "## 2. Smoke tests"
line ""

# 2.1 Search — basic
line "### 2.1 Search"
line ""
assert_field \
  "GET /ingredients/search?q=poulet&lang=fr → 200, servingG=100.0" \
  "200" ".[0].servingG" "100.0" \
  "$BASE_URL/ingredients/search?q=poulet&lang=fr"

# 2.2 Search with nutrition inline
assert_field \
  "GET /ingredients/search?q=poulet&lang=fr&quantity=150&cooking_method=grilled → nutrition populated" \
  "200" ".[0].nutrition.energyKcal > 0" "true" \
  "$BASE_URL/ingredients/search?q=poulet&lang=fr&quantity=150&cooking_method=grilled"

# 2.3 Barcode — known product (Nutella)
assert_field \
  "GET /ingredients/barcode/3017620422003 → 200, source=off_direct" \
  "200" ".source" "off_direct" \
  "$BASE_URL/ingredients/barcode/3017620422003"

# 2.4 Barcode — unknown
assert_http \
  "GET /ingredients/barcode/0000000000000 → 404" \
  "404" \
  "$BASE_URL/ingredients/barcode/0000000000000"

# 2.5 Unit ml — vin rouge
assert_field \
  "POST /nutrition unit=ml (vin rouge 150ml) → 200, available=true" \
  "200" ".[0].available" "true" \
  -X POST "$BASE_URL/nutrition" \
  -H "Content-Type: application/json" \
  -d '[{"name":"vin rouge","quantity":150,"unit":"ml","cookingMethod":"raw","measuredState":"raw"}]'

# 2.6 Unknown ingredient → confidence threshold → available=false
assert_field \
  "POST /nutrition unknown ingredient → available=false (confidence < 0.6)" \
  "200" ".[0].available" "false" \
  -X POST "$BASE_URL/nutrition" \
  -H "Content-Type: application/json" \
  -d '[{"name":"xyzxyz123","quantity":100,"unit":"g","cookingMethod":"raw","measuredState":"raw"}]'

# 2.7 Invalid unit → 400
assert_http \
  "POST /nutrition unit=oz → 400" \
  "400" \
  -X POST "$BASE_URL/nutrition" \
  -H "Content-Type: application/json" \
  -d '[{"name":"chicken","quantity":100,"unit":"oz","cookingMethod":"raw","measuredState":"raw"}]'

# 2.8 Empty list → 400
assert_http \
  "POST /nutrition empty list → 400" \
  "400" \
  -X POST "$BASE_URL/nutrition" \
  -H "Content-Type: application/json" \
  -d '[]'

# ── summary ──────────────────────────────────────────────────────────────────

TOTAL=$((PASS + FAIL))
line "---"
line ""
line "## Summary"
line ""
line "| | Count |"
line "|---|---|"
line "| ✅ Passed | $PASS |"
line "| ❌ Failed | $FAIL |"
line "| Total     | $TOTAL |"
line ""
if [[ $FAIL -eq 0 ]]; then
  line "> All $TOTAL tests passed."
else
  line "> **$FAIL test(s) failed** — see details above."
fi

# ── output ───────────────────────────────────────────────────────────────────

REPORT=$(printf '%s\n' "${LINES[@]}")

echo "$REPORT"
echo "$REPORT" > "$OUT_FILE"
echo ""
echo "Report written to: $OUT_FILE"

[[ $FAIL -eq 0 ]] && exit 0 || exit 1
