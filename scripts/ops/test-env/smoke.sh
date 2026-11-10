#!/usr/bin/env bash
# Smoke checks of the one-VM test environment (W35a plan Task S2; scripts/ops/README.md "Test environment on one VM").
#
#   smoke.sh <base-url> [--cacert <file>] [--resolve <host:port:address>]
#   smoke.sh <base-url> --second-client [--cacert <file>] [--resolve <host:port:address>]
#
# <base-url> is https://<host>, no path and no port: check (b) asks http://<host>/ on port 80.
# The first form runs checks (a) to (f); its part of (d) fills the login bucket of this machine's address. Then run the
# second form at once (within the login rule's window of one minute) from another address: its one failed login must get
# 401, not 429. Both must pass. --cacert trusts a local CA (Caddy's internal CA on a local stack); --resolve is curl's.
#
# Prints PASS or FAIL per check and exits 1 when any check fails (2 on a usage error). It never prints a token, a cookie
# or a body: every answer goes to /dev/null, and headers are only matched, never echoed.
set -uo pipefail

readonly ALLOWED_ORIGIN="https://test.sunvibe.vn"
readonly REFUSED_ORIGIN="https://www.sunvibe.vn"
# The login rule of application.yml allows 10 a minute per client; a few more attempts leave room for a slow clock
readonly LOGIN_ATTEMPTS=15

usage() {
  sed -n '3,7p' "$0" | sed 's/^# \{0,1\}//' >&2
}

base=""
second_client=0
curl_extra=()
while [ $# -gt 0 ]; do
  case "$1" in
    --second-client) second_client=1; shift ;;
    --cacert)
      [ $# -ge 2 ] || { usage; exit 2; }
      # A local CA has no revocation service; Windows' Schannel curl refuses its certificates without this option, and
      # curl with another TLS library ignores it
      curl_extra+=(--cacert "$2" --ssl-no-revoke); shift 2 ;;
    --resolve)
      [ $# -ge 2 ] || { usage; exit 2; }
      curl_extra+=(--resolve "$2"); shift 2 ;;
    -h|--help) usage; exit 0 ;;
    -*) echo "unknown option: $1" >&2; usage; exit 2 ;;
    *) [ -z "$base" ] || { usage; exit 2; }; base="$1"; shift ;;
  esac
done
if ! [[ "$base" =~ ^https://[A-Za-z0-9.-]+$ ]]; then
  echo "base-url must be https://<host> with no path, port or trailing slash" >&2
  usage
  exit 2
fi
host="${base#https://}"

failures=0
pass() { echo "PASS $*"; }
fail() { echo "FAIL $*"; failures=$((failures + 1)); }

# Status code of one request; the body is discarded
status() {
  curl -sS -o /dev/null -w '%{http_code}' --max-time 20 ${curl_extra[@]+"${curl_extra[@]}"} "$@" 2>/dev/null
}

# Response headers of one request, for matching only; the body is discarded
headers() {
  curl -sS -o /dev/null -D - --max-time 20 ${curl_extra[@]+"${curl_extra[@]}"} "$@" 2>/dev/null | tr -d '\r'
}

has_header() {
  # $1 headers, $2 an extended regex for one whole header line, matched without regard to case
  printf '%s\n' "$1" | grep -Eiq "^$2\$"
}

login_status() {
  # A failed login for an address that has no account, with forged forwarding headers: they must not pick the bucket
  local forged="203.0.113.$((RANDOM % 250 + 1))"
  status -X POST "$base/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -H "X-Forwarded-For: $forged" -H "X-Real-IP: $forged" -H "Forwarded: for=$forged" \
    --data "{\"email\":\"smoke-$RANDOM$RANDOM@example.com\",\"password\":\"smoke-not-a-password\"}"
}

if [ "$second_client" -eq 1 ]; then
  code=$(login_status)
  if [ "$code" = "401" ]; then
    pass "d second client: a failed login answers 401, its own bucket"
  else
    fail "d second client: a failed login answered $code, expected 401 (429 means one bucket for every client)"
  fi
  [ "$failures" -eq 0 ] || exit 1
  exit 0
fi

# (a) the public catalog answers
code=$(status "$base/api/v1/public/provinces")
if [ "$code" = "200" ]; then pass "a GET /api/v1/public/provinces answers 200"; else fail "a GET /api/v1/public/provinces answered $code"; fi

# (b) plain http redirects to https
redirect=$(headers "http://$host/api/v1/public/provinces")
if has_header "$redirect" 'HTTP/[0-9.]+ (301|302|307|308).*' && has_header "$redirect" "location: https://$host/.*"; then
  pass "b http:// redirects to https://"
else
  fail "b http:// does not redirect to https://"
fi

# (c) CORS preflight: the test web is allowed, www is refused
preflight() {
  headers -X OPTIONS "$base/api/v1/auth/login" -H "Origin: $1" \
    -H 'Access-Control-Request-Method: POST' -H 'Access-Control-Request-Headers: content-type'
}
allowed=$(preflight "$ALLOWED_ORIGIN")
refused=$(preflight "$REFUSED_ORIGIN")
if has_header "$allowed" 'HTTP/[0-9.]+ 200.*' \
   && has_header "$allowed" "access-control-allow-origin: $ALLOWED_ORIGIN" \
   && has_header "$allowed" 'access-control-allow-credentials: true'; then
  pass "c preflight from $ALLOWED_ORIGIN is allowed"
else
  fail "c preflight from $ALLOWED_ORIGIN is not allowed"
fi
if has_header "$refused" 'HTTP/[0-9.]+ 403.*' && ! has_header "$refused" 'access-control-allow-origin:.*'; then
  pass "c preflight from $REFUSED_ORIGIN is refused"
else
  fail "c preflight from $REFUSED_ORIGIN is not refused"
fi

# (d) first client: failed logins until 429, each with a forged X-Forwarded-For
codes=""
limited=0
for _ in $(seq 1 "$LOGIN_ATTEMPTS"); do
  code=$(login_status)
  codes="$codes $code"
  if [ "$code" = "429" ]; then limited=1; break; fi
  if [ "$code" != "401" ]; then break; fi
done
if [ "$limited" -eq 1 ]; then
  pass "d first client: failed logins reach 429 (answers:$codes); now run --second-client from another address"
else
  fail "d first client: no 429 within $LOGIN_ATTEMPTS failed logins (answers:$codes); a forged header may pick the bucket"
fi

# (e) no API docs and no actuator
for path in /v3/api-docs /actuator/env; do
  code=$(status "$base$path")
  # 000 is no answer at all, which proves nothing about the path
  if [ "$code" != "200" ] && [ "$code" != "000" ]; then
    pass "e GET $path answers $code, not 200"
  else
    fail "e GET $path answered $code, expected an answer other than 200"
  fi
done

# (f) noindex on an answer and on an error
for path in /api/v1/public/provinces /no-such-path; do
  found=$(headers "$base$path")
  if has_header "$found" 'x-robots-tag: noindex'; then
    pass "f GET $path carries X-Robots-Tag: noindex"
  else
    fail "f GET $path has no X-Robots-Tag: noindex"
  fi
done

[ "$failures" -eq 0 ] || exit 1
exit 0
