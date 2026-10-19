#!/usr/bin/env bash
# Stop hook: compile-only smoke check. When Java, Gradle or SQL files changed in the working tree, run
# ./gradlew compileJava compileTestJava and block the stop on a compile error. It runs no tests, no Flyway or
# PostgreSQL check and no API export: ./gradlew build (and exportOpenApi when the API changed) stays the real check
# before "done". Committed changes on a clean tree are not checked here; CI builds them.
# Tests: node --test "scripts/tests/*.test.mjs"
input=$(cat)
active=$(printf '%s' "$input" | node -e '
let d="";process.stdin.on("data",c=>d+=c).on("end",()=>{
  try{process.stdout.write(String(JSON.parse(d).stop_hook_active===true))}catch(e){process.stdout.write("false")}
})')
[ "$active" = "true" ] && exit 0
root=$(git -C "${CLAUDE_PROJECT_DIR:-$PWD}" rev-parse --show-toplevel 2>/dev/null) || exit 0
cd "$root" || exit 0
changed=$(git status --porcelain -- '*.java' '*.gradle' '*.gradle.kts' '*.sql' | head -1)
[ -z "$changed" ] && exit 0
if ! out=$(./gradlew -q compileJava compileTestJava 2>&1); then
  echo "Compile check failed (compileJava compileTestJava only; tests were not run). Fix it before finishing:" >&2
  printf '%s\n' "$out" | tail -40 >&2
  exit 2
fi
exit 0
