#!/usr/bin/env bash
# Bulk security hold of survey photos (survey-media spec A-23, A-29; runbook: scripts/ops/README.md).
#
# Usage: scripts/ops/hold-media.sh <ids-file> <reason> <api-base-url>
#   ids-file      one "checklistId mediaId" per line; blank lines and lines starting with # are skipped
#   reason        SECURITY_INCIDENT | CREDENTIAL_EXPOSURE
#   api-base-url  https://... (e.g. https://api.sunvibe.vn, no trailing /api); http:// only for this machine
#
# One POST /api/v1/staff/survey-media/{mediaId}/hold per photo id (an id listed twice is held once) and no other
# request. A hold of a photo that is already held is a 200 no-op on the server that keeps the first reason. Each id is
# reported as "held" (it is held now), "already-held <REASON>" (an earlier hold with another reason, which stays),
# "not-found" or "refused <status>"; the exit status is 1 when any id was not found or refused, 2 on a usage error.
#
# The ADMIN access token is read from the terminal without echo (read -rs), or from SUNVIBE_ADMIN_TOKEN when that is set
# (code review M5, security review M-3: never an export line in the shell history). It is never written, echoed or
# stored, reaches curl through a config read from stdin, so it is on no command line, and is sent only over https
# (or to this machine).
set -uo pipefail

usage() {
  echo "usage: $0 <ids-file> <SECURITY_INCIDENT|CREDENTIAL_EXPOSURE> <api-base-url>" >&2
  exit 2
}

[ $# -eq 3 ] || usage
ids_file=$1
reason=$2
base=${3%/}
case "$reason" in
  SECURITY_INCIDENT | CREDENTIAL_EXPOSURE) ;;
  *) echo "reason must be SECURITY_INCIDENT or CREDENTIAL_EXPOSURE" >&2; exit 2 ;;
esac
# A host and an optional port only (no credentials part: curl would send the token to the host after an @).
if [[ "$base" =~ ^https://[A-Za-z0-9.-]+(:[0-9]{1,5})?(/[A-Za-z0-9._~/-]*)?$ ]]; then
  protocol='=https'
elif [[ "$base" =~ ^http://(127\.0\.0\.1|localhost|\[::1\])(:[0-9]{1,5})?$ ]]; then
  protocol='=http'
else
  echo "the api-base-url must be https://host[:port] (http:// only for this machine): the token must not travel in clear" >&2
  exit 2
fi
if [ ! -r "$ids_file" ]; then
  echo "cannot read the ids file: $ids_file" >&2
  exit 2
fi
token=${SUNVIBE_ADMIN_TOKEN:-}
if [ -z "$token" ]; then
  if ! { exec 3</dev/tty; } 2>/dev/null; then
    echo "no terminal to read the ADMIN token from: run this in a terminal, or set SUNVIBE_ADMIN_TOKEN" >&2
    exit 2
  fi
  printf 'ADMIN access token (not shown): ' >/dev/tty
  IFS= read -rs token <&3
  exec 3<&-
  printf '\n' >/dev/tty
  if [ -z "$token" ]; then
    echo "no token given (SUNVIBE_ADMIN_TOKEN is not set either)" >&2
    exit 2
  fi
fi
unset SUNVIBE_ADMIN_TOKEN
# an access token is a JWT: anything else (a quote, a newline) would also change the curl config it is written into
if [[ ! "$token" =~ ^[A-Za-z0-9._~+/=-]+$ ]]; then
  echo "the token has characters an access token never has" >&2
  exit 2
fi

STATUS=""
BODY=""

# call <method> <url> [json-body]: sets STATUS (000 when the API did not answer) and BODY.
call() {
  local method=$1 url=$2 data=${3:-} out
  local args=(-sS --config - -X "$method" -w '\n%{http_code}' --max-time 30 --proto "$protocol" --proto-redir =https)
  if [ -n "$data" ]; then
    args+=(-H 'Content-Type: application/json' --data "$data")
  fi
  out=$(printf 'header = "Authorization: Bearer %s"\n' "$token" | curl "${args[@]}" "$url" 2>/dev/null)
  if [ -z "$out" ]; then
    STATUS=000
    BODY=""
    return
  fi
  STATUS=${out##*$'\n'}
  BODY=${out%$'\n'*}
}

# The reason of the photo's hold in a hold answer ({"media": {..., "held": {..., "reason": "X"}}, ...}); empty if none.
held_reason() {
  printf '%s' "$1" | tr -d '\n' | sed -n 's/.*"held"[[:space:]]*:[[:space:]]*{\([^}]*\)}.*/\1/p' \
    | sed -n 's/.*"reason"[[:space:]]*:[[:space:]]*"\([A-Z_]*\)".*/\1/p' | head -n 1
}

held=0
already=0
missing=0
refused=0
seen=$'\n'

while IFS= read -r line || [ -n "$line" ]; do
  line=${line%$'\r'}
  case "$line" in '' | \#*) continue ;; esac
  read -r checklist_id media_id rest <<<"$line"
  if [[ ! "${checklist_id:-}" =~ ^[0-9A-Za-z]{26}$ || ! "${media_id:-}" =~ ^[0-9A-Za-z]{26}$ || -n "${rest:-}" ]]; then
    echo "malformed line (want: checklistId mediaId): skipped"
    refused=$((refused + 1))
    continue
  fi
  case "$seen" in *$'\n'"$media_id"$'\n'*) continue ;; esac
  seen+="$media_id"$'\n'

  call POST "$base/api/v1/staff/survey-media/$media_id/hold" "{\"reason\":\"$reason\"}"
  case "$STATUS" in
    200)
      kept=$(held_reason "$BODY")
      if [ -n "$kept" ] && [ "$kept" != "$reason" ]; then
        echo "$media_id already-held $kept"
        already=$((already + 1))
      else
        echo "$media_id held"
        held=$((held + 1))
      fi
      ;;
    404)
      echo "$media_id not-found"
      missing=$((missing + 1))
      ;;
    *)
      echo "$media_id refused $STATUS"
      refused=$((refused + 1))
      ;;
  esac
done <"$ids_file"

echo "held $held, already held with another reason $already, not found $missing, refused $refused"
if [ $((missing + refused)) -gt 0 ]; then
  exit 1
fi
