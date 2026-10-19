#!/usr/bin/env bash
# Fails when this branch modifies, deletes or renames a versioned Flyway migration (V*.sql) that already exists at
# its fork point with <base>: such a migration may have been applied somewhere, and Flyway's checksum validation
# would break. Adding migrations, editing the branch's own new ones and repeatable migrations (R__) are fine.
# Usage: scripts/ci/check-migration-history.sh <base-ref-or-sha> [--include-worktree]
#   CI pull request: origin/<base branch> (needs actions/checkout with fetch-depth: 0)
#   CI push: the previous tip (github.event.before); an all-zero sha means a new branch: nothing to compare.
#   --include-worktree also checks uncommitted changes (local use).
# An exception needs the owner's explicit approval in the PR; this check then stays red as the record.
# Tests: node --test "scripts/tests/*.test.mjs"
set -u
base="${1:-}"
mode="${2:-}"
if [ -z "$base" ] || [ -z "${base//0/}" ]; then
  echo "check-migration-history: no base commit (new branch); nothing to compare."
  exit 0
fi
if ! git rev-parse --verify -q "$base^{commit}" >/dev/null; then
  echo "check-migration-history: base '$base' not found. In CI check out with fetch-depth: 0; a push whose previous tip was force-pushed away has no base either." >&2
  exit 1
fi
fork=$(git merge-base "$base" HEAD) || { echo "check-migration-history: no merge base with '$base'." >&2; exit 1; }

pathspec=':(glob)**/src/main/resources/db/migration/**/V*.sql'
if [ "$mode" = "--include-worktree" ]; then
  changes=$(git diff --name-status --find-renames "$fork" -- "$pathspec") || { echo "check-migration-history: git diff failed." >&2; exit 1; }
else
  changes=$(git diff --name-status --find-renames "$fork" HEAD -- "$pathspec") || { echo "check-migration-history: git diff failed." >&2; exit 1; }
fi

violations=$(printf '%s\n' "$changes" | awk -F '\t' '$1 ~ /^(M|D|T|R)/ { print "  " $1 "  " $2 }')
if [ -n "$violations" ]; then
  echo "Committed Flyway migrations were changed (status: M modified, D deleted, R renamed, T type changed):" >&2
  printf '%s\n' "$violations" >&2
  echo "Add a new V<yyyyMMddHHmm>__*.sql migration instead (see .claude/rules/flyway.md)." >&2
  exit 1
fi
echo "check-migration-history: no committed migration changed since $(git rev-parse --short "$fork")."
exit 0
