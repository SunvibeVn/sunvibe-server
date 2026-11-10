#!/usr/bin/env bash
# Nightly dump of the test database (W35a plan Task S3, D-2; scripts/ops/README.md "Test environment on one VM").
#
#   backup-db.sh -f <compose-file> --env-file <env-file> [--dir <folder>]
#
# Runs pg_dump -Fc in the postgres service through `docker compose exec -T`, as the database container's own user (no
# password on any command line), into <folder>/sunvibe-test-<UTC time>.dump (default folder /var/backups/sunvibe-test).
# The dump is written under a temporary name and renamed only when pg_dump succeeds; then the 7 newest dumps are kept.
# A failed dump exits 1 and leaves every earlier dump in place.
set -euo pipefail
umask 077

readonly KEEP=7

usage() {
  sed -n '4,4p' "$0" | sed 's/^# \{0,1\}//' >&2
  exit 2
}

compose_file=""
env_file=""
dir="/var/backups/sunvibe-test"
while [ $# -gt 0 ]; do
  case "$1" in
    -f) [ $# -ge 2 ] || usage; compose_file="$2"; shift 2 ;;
    --env-file) [ $# -ge 2 ] || usage; env_file="$2"; shift 2 ;;
    --dir) [ $# -ge 2 ] || usage; dir="$2"; shift 2 ;;
    *) usage ;;
  esac
done
[ -n "$compose_file" ] && [ -n "$env_file" ] || usage

mkdir -p "$dir"
name="sunvibe-test-$(date -u +%Y%m%dT%H%M%SZ).dump"
partial="$dir/.$name.partial"
trap 'rm -f "$partial"' EXIT

# The variables are the container's own, expanded by its shell
# shellcheck disable=SC2016
if ! docker compose -f "$compose_file" --env-file "$env_file" exec -T postgres \
    sh -c 'pg_dump -Fc -U "$POSTGRES_USER" -d "$POSTGRES_DB"' > "$partial" || [ ! -s "$partial" ]; then
  echo "backup-db.sh: dump failed; the earlier dumps in $dir are kept" >&2
  exit 1
fi
mv -f "$partial" "$dir/$name"
echo "wrote $dir/$name"

# The names sort by time; remove all but the newest $KEEP
mapfile -t dumps < <(find "$dir" -maxdepth 1 -type f -name 'sunvibe-test-*.dump' -printf '%f\n' | sort)
if [ "${#dumps[@]}" -gt "$KEEP" ]; then
  for old in "${dumps[@]:0:${#dumps[@]}-KEEP}"; do
    rm -f "$dir/$old"
    echo "removed $dir/$old"
  done
fi
