#!/usr/bin/env bash
# When Claude stops, make sure the code still compiles if Java/Gradle/SQL files changed.
input=$(cat)
active=$(printf '%s' "$input" | node -e '
let d="";process.stdin.on("data",c=>d+=c).on("end",()=>{
  try{process.stdout.write(String(JSON.parse(d).stop_hook_active===true))}catch(e){process.stdout.write("false")}
})')
[ "$active" = "true" ] && exit 0
cd "$CLAUDE_PROJECT_DIR" 2>/dev/null || exit 0
changed=$(git status --porcelain -- '*.java' '*.gradle' '*.gradle.kts' '*.sql' | head -1)
[ -z "$changed" ] && exit 0
if ! out=$(./gradlew -q compileJava compileTestJava 2>&1); then
  echo "Build check failed. Fix compile errors before finishing:" >&2
  printf '%s\n' "$out" | tail -40 >&2
  exit 2
fi
exit 0
