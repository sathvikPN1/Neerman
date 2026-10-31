#!/usr/bin/env bash
# Quick HTTP smoke check for a running instance. Read-only except for one failed login attempt.
# Usage: bash tests/smoke/smoke.sh [base-url]     (default http://localhost:8080)
# Needs: curl. Uses the demo accounts, so run it only where DEMO_DATA=true.

BASE="${1:-http://localhost:8080}"
PASS="${DEMO_PASSWORD:-Demo@1234}"
JAR="$(mktemp)"
fails=0

check() { # name, expected, actual
  if [ "$2" = "$3" ]; then echo "PASS  $1"; else echo "FAIL  $1 (expected $2, got $3)"; fails=$((fails + 1)); fi
}
code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }

# 1. Public endpoints
check "health is UP"            200 "$(code "$BASE/actuator/health")"
check "login page"              200 "$(code "$BASE/login")"
check "logo is public"          200 "$(code "$BASE/img/nirmaan-logo.png")"
check "actuator/env hidden"     404 "$(code "$BASE/actuator/env")"

# 2. Anonymous users are redirected
for path in / /claims/1 /coo/approvals /staff/queue /finance/payments /admin/staff /audit /team; do
  check "anonymous $path -> login" 302 "$(code "$BASE$path")"
done

# 3. POST without CSRF token is refused
check "POST without CSRF"       403 "$(code -X POST "$BASE/claims")"

# 4. Log in as a user and return the HTTP status of a page
login() { # email -> 0 on success
  : > "$JAR"
  local page token
  page="$(curl -s -c "$JAR" "$BASE/login")"
  token="$(printf '%s' "$page" | sed -n 's/.*name="_csrf"[^>]*value="\([^"]*\)".*/\1/p; s/.*value="\([^"]*\)"[^>]*name="_csrf".*/\1/p' | head -n1)"
  curl -s -o /dev/null -b "$JAR" -c "$JAR" --data-urlencode "username=$1" --data-urlencode "password=$PASS" \
    --data-urlencode "_csrf=$token" "$BASE/login"
}
as() { code -b "$JAR" "$BASE$1"; } # path

login "coo@nirmaan.local"
check "COO /coo/approvals"      200 "$(as /coo/approvals)"
check "COO /admin/staff"        200 "$(as /admin/staff)"
check "COO /audit"              200 "$(as /audit)"
check "COO /team is team-only"  403 "$(as /team)"

login "staff@nirmaan.local"
check "staff /staff/queue"      200 "$(as /staff/queue)"
check "staff /admin/staff"      403 "$(as /admin/staff)"

login "finance@nirmaan.local"
check "finance /finance/payments" 200 "$(as /finance/payments)"
check "finance /admin/staff"    403 "$(as /admin/staff)"

login "priya@agrisense.local"
check "team /team"              200 "$(as /team)"
check "team /coo/approvals"     403 "$(as /coo/approvals)"
check "team /staff/queue"       403 "$(as /staff/queue)"
check "team /finance/payments"  403 "$(as /finance/payments)"
check "team /audit"             403 "$(as /audit)"

rm -f "$JAR"
echo
if [ "$fails" -eq 0 ]; then echo "Smoke test passed."; else echo "$fails check(s) failed."; exit 1; fi
