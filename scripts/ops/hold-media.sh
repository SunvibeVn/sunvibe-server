#!/usr/bin/env bash
# Bulk security hold of survey photos (survey-media spec A-23, A-29; runbook: scripts/ops/README.md).
#
# Usage: SUNVIBE_ADMIN_TOKEN=<ADMIN access token> scripts/ops/hold-media.sh <ids-file> <reason> <api-base-url>
#   ids-file      one "checklistId mediaId" per line; blank lines and lines starting with # are skipped
#   reason        SECURITY_INCIDENT | CREDENTIAL_EXPOSURE
#   api-base-url  e.g. https://api.sunvibe.vn (no trailing /api)
#
# One POST /api/v1/staff/survey-media/{mediaId}/hold per photo id (an id listed twice is held once). A hold of a photo
# that is already held is a 200 no-op on the server; the script tells it apart by reading the checklist's media list
# first and comparing its setVersion with the one the hold answers. Each id is reported as "held", "already-held",
# "not-found" or "refused <status>"; the exit status is 1 when any id was not found or refused, 2 on a usage error.
#
# The token is read only from SUNVIBE_ADMIN_TOKEN. It is never written, echoed or stored: it reaches curl through a
# config read from stdin, so it is not on any command line either.
set -uo pipefail

usage() {
  echo "usage: SUNVIBE_ADMIN_TOKEN=... $0 <ids-file> <SECURITY_INCIDENT|CREDENTIAL_EXPOSURE> <api-base-url>" >&2
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
if [ -z "${SUNVIBE_ADMIN_TOKEN:-}" ]; then
  echo "SUNVIBE_ADMIN_TOKEN is not set: export the ADMIN access token in this shell first (it is never printed)" >&2
  exit 2
fi
if [ ! -r "$ids_file" ]; then
  echo "cannot read the ids file: $ids_file" >&2
  exit 2
fi

STATUS=""
BODY=""

# call <method> <url> [json-body]: sets STATUS (000 when the API did not answer) and BODY.
call() {
  local method=$1 url=$2 data=${3:-} out
  local args=(-sS --config - -X "$method" -w '\n%{http_code}' --max-time 30)
  if [ -n "$data" ]; then
    args+=(-H 'Content-Type: application/json' --data "$data")
  fi
  out=$(printf 'header = "Authorization: Bearer %s"\n' "$SUNVIBE_ADMIN_TOKEN" | curl "${args[@]}" "$url" 2>/dev/null)
  if [ -z "$out" ]; then
    STATUS=000
    BODY=""
    return
  fi
  STATUS=${out##*$'\n'}
  BODY=${out%$'\n'*}
}

set_version() {
  printf '%s' "$1" | sed -n 's/.*"setVersion"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p' | head -n 1
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

  call GET "$base/api/v1/staff/survey-checklists/$checklist_id/media"
  if [ "$STATUS" = 404 ]; then
    echo "$media_id not-found"
    missing=$((missing + 1))
    continue
  fi
  if [ "$STATUS" != 200 ]; then
    echo "$media_id refused $STATUS"
    refused=$((refused + 1))
    continue
  fi
  before=$(set_version "$BODY")

  call POST "$base/api/v1/staff/survey-media/$media_id/hold" "{\"reason\":\"$reason\"}"
  case "$STATUS" in
    200)
      after=$(set_version "$BODY")
      if [ -n "$before" ] && [ "$before" = "$after" ]; then
        echo "$media_id already-held"
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

echo "held $held, already held $already, not found $missing, refused $refused"
if [ $((missing + refused)) -gt 0 ]; then
  exit 1
fi
