#!/usr/bin/env bash
# Block edits to Flyway migrations that are already committed.
input=$(cat)
file=$(printf '%s' "$input" | node -e '
let d="";process.stdin.on("data",c=>d+=c).on("end",()=>{
  try{const j=JSON.parse(d);process.stdout.write(((j.tool_input||{}).file_path||"").replace(/\\/g,"/"))}catch(e){}
})')
[ -z "$file" ] && exit 0
case "$file" in
  */src/main/resources/db/migration/V*.sql|src/main/resources/db/migration/V*.sql)
    cd "$CLAUDE_PROJECT_DIR" 2>/dev/null || exit 0
    if git ls-files --error-unmatch "$file" >/dev/null 2>&1; then
      echo "Blocked: $file is a committed Flyway migration. Create a new V<yyyyMMddHHmm>__*.sql instead." >&2
      exit 2
    fi
    ;;
esac
exit 0
