#!/usr/bin/env bash
# The local API for the survey-media E2E run (survey-media spec 6.4, plan PD-5). DEV ONLY.
#
# No committed profile turns photo uploads on; this script passes the overrides on the bootRun command line:
# profile dev, uploads and processing on, the local store under a fresh temporary directory (created before the start
# and deleted on exit), the stub scanner and the dev processing container of docker/media-processor/compose.dev.yaml
# (published on 127.0.0.1:7300 only). It prints the store root and the profile, never a secret.
# It serves `npm run e2e:workspace`, so the inventory intake is on and the direct create off, as after turn-on
# (property-create intake A-11): the inventory-intake project runs behind the photo projects.
#
# Needs: Docker, a local PostgreSQL and the env vars of .env.example in this shell (as for the plain dev run), the two
# E2E seeds run against that database. Usage: scripts/dev/run-e2e-media-api.sh   (Ctrl+C stops it and cleans up)
set -euo pipefail

cd "$(dirname "$0")/../.."
COMPOSE=docker/media-processor/compose.dev.yaml
PROCESSOR_VERSION=${SURVEY_MEDIA_PROCESSOR_VERSION:-dev-8.16.1}

root=$(mktemp -d "${TMPDIR:-/tmp}/sunvibe-e2e-media.XXXXXX")
mkdir -p "$root/store" "$root/spool"

cleanup() {
  docker compose -f "$COMPOSE" down >/dev/null 2>&1 || true
  rm -rf "$root"
  echo "survey-media e2e: stopped; store root $root deleted"
}
trap cleanup EXIT INT TERM

echo "survey-media e2e: profile dev, store root $root/store, processor $PROCESSOR_VERSION on 127.0.0.1:7300"
docker compose -f "$COMPOSE" up --build -d

./gradlew :app:bootRun --args="--spring.profiles.active=dev \
--app.property.intake.enabled=true \
--app.property.direct-create.enabled=false \
--app.survey.media.upload-enabled=true \
--app.survey.checklist.submission-enabled=true \
--app.survey.media.processing-enabled=true \
--app.survey.media.store=local \
--app.survey.media.local.root=$root/store \
--app.survey.media.spool-dir=$root/spool \
--app.survey.media.scanner=stub \
--app.survey.media.processor.kind=isolated \
--app.survey.media.processor.host=127.0.0.1 \
--app.survey.media.processor.port=7300 \
--app.survey.media.processor.version=$PROCESSOR_VERSION"
