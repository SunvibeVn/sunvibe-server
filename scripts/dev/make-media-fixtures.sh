#!/usr/bin/env bash
# Regenerates the synthetic survey-media corpus (survey-media spec SM-1, SM-2; plan PD-6) into
# app/src/test/resources/media/fixtures/ with the builder image docker/media-fixtures/ (its tools never enter the
# decoder image). Needs Docker. Commit the result; ProcessorCorpusTest reads it in ./gradlew build.
set -euo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
out="$root/app/src/test/resources/media/fixtures"
mkdir -p "$out"
docker build -q -t sunvibe/media-fixtures:local "$root/docker/media-fixtures" >/dev/null
mount="$out"
if command -v cygpath >/dev/null 2>&1; then
  # Git Bash on Windows: hand Docker a Windows path and stop MSYS from rewriting the mount argument
  mount=$(cygpath -w "$out")
  export MSYS_NO_PATHCONV=1
fi
docker run --rm -v "$mount:/out" sunvibe/media-fixtures:local
