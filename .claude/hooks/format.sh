#!/usr/bin/env bash
# Format the edited Java file with Spotless, if Spotless is configured.
input=$(cat)
file=$(printf '%s' "$input" | node -e '
let d="";process.stdin.on("data",c=>d+=c).on("end",()=>{
  try{const j=JSON.parse(d);process.stdout.write(((j.tool_input||{}).file_path||"").replace(/\\/g,"/"))}catch(e){}
})')
case "$file" in
  *.java)
    cd "$CLAUDE_PROJECT_DIR" 2>/dev/null || exit 0
    # Remove the next line once Spotless is configured (see report TODO).
    exit 0
    ./gradlew -q spotlessApply -PspotlessIdeHook="$file" >/dev/null 2>&1 || true
    ;;
esac
exit 0
