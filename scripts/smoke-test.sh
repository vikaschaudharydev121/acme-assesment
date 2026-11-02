#!/usr/bin/env bash
# End-to-end smoke test of a running instance, written as the HR Manager's real journey plus the
# security guarantees an attacker would probe. Uses only curl, like a browser would (cookie jar +
# XSRF header). Exits non-zero on the first failed check.
#
#   BASE_URL=http://localhost:8080 MANAGER_PASSWORD=... VIEWER_PASSWORD=... scripts/smoke-test.sh
set -uo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
MANAGER_USER="${MANAGER_USER:-hr.manager}"
MANAGER_PASSWORD="${MANAGER_PASSWORD:-Manager@12345}"
VIEWER_USER="${VIEWER_USER:-hr.viewer}"
VIEWER_PASSWORD="${VIEWER_PASSWORD:-Viewer@12345}"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
PASS=0

fail() { echo "  FAIL: $*" >&2; exit 1; }
ok()   { PASS=$((PASS + 1)); echo "  ok   $*"; }

# request <jar> <method> <path> [json-body] [--no-csrf]  → sets $STATUS and $BODY
request() {
  local jar="$1" method="$2" path="$3" body="${4:-}" csrf="${5:-}"
  local args=(-s -o "$WORK/body" -w '%{http_code}' -X "$method" -b "$jar" -c "$jar" "$BASE_URL$path")
  if [[ -n "$body" ]]; then args+=(-H 'Content-Type: application/json' --data "$body"); fi
  if [[ "$csrf" != "--no-csrf" ]]; then
    local token
    token="$(awk '$6 == "XSRF-TOKEN" {print $7}' "$jar" 2>/dev/null | tail -1)"
    [[ -n "$token" ]] && args+=(-H "X-XSRF-TOKEN: $token")
  fi
  STATUS="$(curl "${args[@]}")"
  BODY="$(cat "$WORK/body")"
}

expect() { [[ "$STATUS" == "$1" ]] || fail "$2: expected HTTP $1, got $STATUS — $BODY"; ok "$2 ($STATUS)"; }
contains() { [[ "$BODY" == *"$1"* ]] || fail "$2: response lacks '$1' — $BODY"; ok "$2"; }
json() { python3 -c "import json,sys; d=json.load(sys.stdin); print($1)" <<<"$BODY"; }

sign_in() { # jar user password
  request "$1" GET /api/v1/auth/me            # primes the XSRF-TOKEN cookie
  request "$1" POST /api/v1/auth/login "{\"username\":\"$2\",\"password\":\"$3\"}"
}

M="$WORK/manager.jar"; V="$WORK/viewer.jar"; A="$WORK/anon.jar"
RUN_ID="$(date +%s)$RANDOM"
EMAIL="smoke.$RUN_ID@acme.example"

echo "== Platform"
request "$A" GET /actuator/health;                     expect 200 "health endpoint"; contains '"UP"' "status UP"
request "$A" GET /;                                    expect 200 "UI served"
request "$A" GET /employees;                           expect 200 "SPA deep link served"
HEADERS="$(curl -sI "$BASE_URL/")"
[[ "$HEADERS" == *"Content-Security-Policy"* && "$HEADERS" == *"X-Frame-Options: DENY"* ]] \
  || fail "security headers missing"; ok "CSP and frame headers present"

echo "== Access control"
request "$A" GET /api/v1/employees;                    expect 401 "anonymous cannot list employees"
request "$A" GET /actuator/env;                        expect 401 "actuator internals hidden"
sign_in "$A" "$MANAGER_USER" "wrong-password-$RUN_ID"; expect 401 "wrong password rejected"
contains "Invalid username or password." "generic credential error (no enumeration)"

echo "== HR Manager journey"
sign_in "$M" "$MANAGER_USER" "$MANAGER_PASSWORD";      expect 200 "manager signs in"; contains HR_MANAGER "manager role"
grep -q '#HttpOnly_.*ACCESS_TOKEN' "$M" || fail "session cookie is not HttpOnly"; ok "session cookie is HttpOnly"

request "$M" GET "/api/v1/employees?size=1";           expect 200 "list employees"
TOTAL="$(json "d['totalElements']")"
[[ "$TOTAL" -ge 10000 ]] || fail "expected >= 10000 seeded employees, got $TOTAL"; ok "seeded data present ($TOTAL employees)"

request "$M" GET "/api/v1/employees?country=IN&department=Engineering&q=a&sort=annualSalary,desc&size=5"
expect 200 "search + filter + sort"
[[ "$(json "all(e['countryCode']=='IN' and e['department']=='Engineering' for e in d['content'])")" == True ]] \
  || fail "filter returned wrong rows"; ok "filters applied correctly"

NEW="{\"firstName\":\"Smoke\",\"lastName\":\"Test\",\"email\":\"$EMAIL\",\"jobTitle\":\"Software Engineer\",\"department\":\"Engineering\",\"countryCode\":\"IN\",\"annualSalary\":2400000,\"hireDate\":\"2024-01-15\"}"
request "$M" POST /api/v1/employees "$NEW";            expect 201 "create employee"
ID="$(json "d['id']")"; VERSION="$(json "d['version']")"
contains '"currency":"INR"' "currency derived from country"

request "$M" POST /api/v1/employees "$NEW";            expect 409 "duplicate email rejected"
request "$M" POST /api/v1/employees '{"firstName":""}'; expect 400 "invalid payload rejected"
contains '"errors"' "field-level validation errors returned"
request "$M" POST /api/v1/employees "${NEW%\}},\"employeeCode\":\"EMP-HACK\"}"; expect 400 "server-owned fields cannot be mass-assigned"

RAISE="${NEW%\}},\"annualSalary\":2700000,\"version\":$VERSION}"
request "$M" PUT "/api/v1/employees/$ID" "$RAISE";      expect 400 "salary change without reason rejected"
RAISE="${NEW%\}},\"annualSalary\":2700000,\"version\":$VERSION,\"salaryChangeReason\":\"Annual review\"}"
request "$M" PUT "/api/v1/employees/$ID" "$RAISE";      expect 200 "salary change with reason"
request "$M" PUT "/api/v1/employees/$ID" "$RAISE";      expect 409 "stale version (lost update) prevented"

request "$M" GET "/api/v1/employees/$ID/salary-history"; expect 200 "salary history"
[[ "$(json "len(d)")" == 2 && "$(json "d[0]['reason']")" == "Annual review" ]] || fail "history wrong: $BODY"
ok "history records initial salary and raise"

request "$M" POST /api/v1/employees "$NEW" --no-csrf;  expect 403 "write without CSRF token rejected"

echo "== Insights"
request "$M" GET /api/v1/insights/summary;            expect 200 "org summary"; contains '"median"' "median reported"
request "$M" GET /api/v1/insights/by-country;         expect 200 "pay by country"
request "$M" GET "/api/v1/insights/by-job-title?country=IN"; expect 200 "job titles in India"
contains '"currency":"INR"' "country breakdown stays in local currency"
request "$M" GET "/api/v1/insights/by-department?country=XX"; expect 400 "unknown country rejected"

echo "== Read-only viewer"
sign_in "$V" "$VIEWER_USER" "$VIEWER_PASSWORD";        expect 200 "viewer signs in"
request "$V" GET "/api/v1/employees/$ID";              expect 200 "viewer can read"
request "$V" DELETE "/api/v1/employees/$ID";           expect 403 "viewer cannot delete"
request "$V" PUT "/api/v1/employees/$ID" "$RAISE";     expect 403 "viewer cannot edit"

echo "== Clean-up and sign-out"
request "$M" DELETE "/api/v1/employees/$ID";           expect 204 "manager deletes test employee"
request "$M" GET "/api/v1/employees/$ID";              expect 404 "deleted employee is gone"
request "$M" POST /api/v1/auth/logout;                 expect 204 "sign out"
request "$M" GET /api/v1/auth/me;                      expect 401 "session ended after sign-out"

echo "PASSED: $PASS checks"
