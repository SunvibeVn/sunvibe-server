@AGENTS.md
Hub workflow and cross-repo rules below; their paths (docs/...) are relative to the hub, i.e. ../docs/... from here.
@../AGENTS.md

## Claude Code only
- Start sessions with `claude --add-dir ..` (or the `claude` helper) so hub skills, subagents and docs are available.
  This repo's hooks and settings load only in a session started here.
- Feature work: this main session implements the approved plan itself, from ../docs/features/<module>/<feature>/
  (brief/, the latest impl/specs/ and impl/plans/, and the BE prompt). Use superpowers:executing-plans ("Native")
  with superpowers:test-driven-development for every task, on the run's branch (rebuild/sunvibe-rebuild during the
  rebuild), without a worktree unless the owner asks. feature-implementer is optional (legacy): only for one
  self-contained task the owner asks to delegate, with the task, paths, decisions and checks in the prompt.
- Never edit a brief (../docs/features/**/brief/); a missing or unclear one goes to the owner.
- Stop and report the exact conflict (files and lines) when brief, spec, API contract, plan, ADR or code disagree in
  a way that changes behavior, or when a task needs a business decision; a plan "ruling" never decides a business
  rule. After two evidence-backed failed attempts on one task (failing test or log, a different fix each time),
  stop: raise the effort once (`/effort xhigh`) with that evidence, and if it still fails, ask the owner.
- In a flow run (`claude -p` started by the hub scripts/flow): after two evidence-backed failed attempts stop and
  report both instead of raising the effort; the runner's review stage replaces the final review below; skip
  superpowers:finishing-a-development-branch and end with the report (the runner verifies, the owner pushes).
  Outside a flow run, finishing-a-development-branch never merges into the base locally; push and PR follow the repo
  rules (rebuild: G2 D-27 to D-29).
- Bug: superpowers:systematic-debugging; a failing test reproduces it first.
- Flyway or backfill: the flyway-migration skill; migration-specialist reviews every db/migration change and every
  backfill (triggers: ../docs/setup/AI_FLOW.md section 8).
- Before "done": AGENTS.md "Before saying done" with superpowers:verification-before-completion (real commands,
  read their output). The Stop hook only compiles; it is not that verification.
- Independent review before the PR. Give each reviewer the diff range, the spec and plan paths, the decisions and
  invariants that apply and the checks you ran (they do not see this conversation):
  code-reviewer always (BE checklist ../docs/setup/checklists/be-review.md); security-reviewer for auth, users,
  ownership, roles, money or payments, uploads or personal data; migration-specialist for db/migration or a
  backfill (../docs/setup/AI_FLOW.md section 8). Fix Blocker and Major findings, re-run the affected checks.
- This review replaces the final review of superpowers:executing-plans: dispatch the hub code-reviewer (and the
  triggered reviewers) without a model parameter, so each keeps its frontmatter model; read its Critical as Blocker
  and Important as Major. Fix every Blocker and Major test-first, or take it to the owner; a Ruling never declines
  one. After the fixes, the same reviewers review again.
- When the BE work of a feature is done: /api-handoff (it pins the BE commit and the contract sha256).
- Model and effort are set when the session starts (owner settings; high-risk work: `claude --effort xhigh`). Never
  switch the model mid-session (it re-reads the conversation uncached). Policy:
  ../docs/setup/CLAUDE_MODEL_EFFORT_SETUP.md
- Architecture questions: read docs/architecture/backend-architecture.md first, then only the
  layer-guide.md section you need.
