@AGENTS.md
Hub workflow and cross-repo rules below; their paths (docs/...) are relative to the hub, i.e. ../docs/... from here.
@../AGENTS.md

## Claude Code only
- Start sessions with `claude --add-dir ..` so hub skills, subagents and docs are available.
- New feature: read the brief and follow the plan in ../docs/features/<feature>/ (brief/, impl/plans/);
  implement with Superpowers subagent-driven-development and TDD. Never edit the brief.
- Bug: use Superpowers systematic-debugging; write a failing test first.
- Before a PR: run the code-reviewer and security-reviewer subagents; fix Blocker and Major findings.
- When BE work for a feature is done: run /api-handoff.
- Architecture questions: read docs/architecture/backend-architecture.md first, then only the
  layer-guide.md section you need.
