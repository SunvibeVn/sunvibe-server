#!/usr/bin/env bash
# The local API for the auth E2E runs (auth-email-access plan Task A16, PD-9). DEV ONLY.
#
# Starts Mailpit (SMTP on 127.0.0.1:1025, its API and inbox on 127.0.0.1:8025) and the API on profile dev with the phone
# channel of the argument. In legacy-login the end date is set one day ahead. Everything the API logs also goes to
# build/e2e-auth-api.log, where the phone suites read the codes of the `log` SMS sender. It prints the mode and the paths,
# never a secret.
#
# Needs: Docker, a local PostgreSQL and the env vars of .env.example in this shell (as for the plain dev run), the E2E
# seeds run against that database.
# Usage: scripts/dev/run-e2e-auth-api.sh [off|legacy-login|on]   (default off; Ctrl+C stops it and cleans up)
set -euo pipefail

cd "$(dirname "$0")/../.."
MODE=${1:-off}
MAILPIT=sunvibe-e2e-mailpit
MAILPIT_IMAGE=axllent/mailpit:v1.22.3
LOG=build/e2e-auth-api.log

case "$MODE" in
  off|legacy-login|on) ;;
  *) echo "usage: $0 [off|legacy-login|on]" >&2; exit 2 ;;
esac

cleanup() {
  docker rm -f "$MAILPIT" >/dev/null 2>&1 || true
  echo "auth e2e: stopped; Mailpit removed"
}
trap cleanup EXIT INT TERM

export AUTH_PHONE_MODE=$MODE
export SMS_PROVIDER=log
export MAIL_HOST=localhost MAIL_PORT=1025 MAIL_SMTP_AUTH=false MAIL_STARTTLS=false MAIL_FROM=e2e@sunvibe.test
if [ "$MODE" = "legacy-login" ]; then
  AUTH_PHONE_LEGACY_LOGIN_UNTIL=$(date -u -d '+1 day' +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -v+1d +%Y-%m-%dT%H:%M:%SZ)
  export AUTH_PHONE_LEGACY_LOGIN_UNTIL
fi

docker rm -f "$MAILPIT" >/dev/null 2>&1 || true
docker run -d --rm --name "$MAILPIT" -p 127.0.0.1:1025:1025 -p 127.0.0.1:8025:8025 "$MAILPIT_IMAGE" >/dev/null

mkdir -p build
echo "auth e2e: profile dev, phone channel $MODE, Mailpit on 127.0.0.1:8025, log $LOG"
./gradlew :app:bootRun --args="--spring.profiles.active=dev" 2>&1 | tee "$LOG"
