#!/usr/bin/env bash
# PreToolUse guard (Edit|Write|MultiEdit): block edits to versioned Flyway migrations (V*.sql, or V*.java under
# src/main/java/db/migration) that exist in HEAD.
# New migrations, untracked or only staged, stay editable. This is a convenience guard, not a security boundary:
# shell commands, renames and deletions are not covered (CI check: scripts/ci/check-migration-history.sh), and the
# edit is let through when the input or the repository cannot be read.
# Tests: node --test "scripts/tests/*.test.mjs"
input=$(cat)
file=$(printf '%s' "$input" | node -e '
let d="";process.stdin.on("data",c=>d+=c).on("end",()=>{
  try{const j=JSON.parse(d);process.stdout.write(((j.tool_input||{}).file_path||"").replace(/\\/g,"/"))}catch(e){}
})')
[ -z "$file" ] && exit 0
# Any versioned migration at any depth under db/migration (Flyway scans subfolders; same scope as the CI check).
case "$file" in
  */src/main/resources/db/migration/*|src/main/resources/db/migration/*) versioned='V*.sql' ;;
  */src/main/java/db/migration/*|src/main/java/db/migration/*) versioned='V*.java' ;;
  # The logic of a Java migration: an edit changes what an environment not yet migrated would do
  */infrastructure/persistence/migration/*) versioned='*.java' ;;
  *) exit 0 ;;
esac
# shellcheck disable=SC2254
case "${file##*/}" in
  $versioned) ;;
  *) exit 0 ;;
esac

project="${CLAUDE_PROJECT_DIR:-$PWD}"
case "$file" in
  /*|[A-Za-z]:/*) ;;
  *) file="$project/$file" ;;
esac
# The repository that holds the file (a hub session may edit server files), else the session's repository.
root=$(git -C "$(dirname "$file")" rev-parse --show-toplevel 2>/dev/null) ||
  root=$(git -C "$project" rev-parse --show-toplevel 2>/dev/null) || exit 0
git -C "$root" rev-parse --verify -q HEAD >/dev/null || exit 0
# git resolves an absolute pathspec against the working tree; a path outside it matches nothing.
committed=$(git -C "$root" ls-tree -r --name-only --full-name HEAD -- "$file" 2>/dev/null)
if [ -n "$committed" ]; then
  echo "Blocked: $committed is a committed Flyway migration (it exists in HEAD). Add a new V<yyyyMMddHHmm>__* migration instead; only the owner may approve changing a committed one." >&2
  exit 2
fi
exit 0
