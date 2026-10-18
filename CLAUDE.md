@AGENTS.md
Hub workflow and cross-repo rules below; their paths (docs/...) are relative to the hub, i.e. ../docs/... from here.
@../AGENTS.md

## Claude Code only
- Start sessions with `claude --add-dir ..` so hub skills, subagents and docs are available.
- New feature: read the brief and follow the plan in ../docs/features/<module>/<feature>/ (brief/, impl/plans/);
  implement with Superpowers subagent-driven-development and TDD. Never edit a brief.
  Dispatch implementer tasks to the feature-implementer subagent; Flyway or backfill changes also go to
  migration-specialist (hub agents, loaded through --add-dir ..).
  Model and effort per task: feature-implementer defaults to Sonnet high (pass no `model`). A **hard** task is
  dispatched with `model: opus`, effort high. Hard = the plan marks it `Implementer: high`, or the Superpowers
  "Model Selection" judgment finds it hard (concurrency or locking, money or fee calculation, a state machine with
  several guarded transitions, layered authorization, a cross-module transaction, many files with integration or
  design judgment). Superpowers decides the difficulty; the only two combinations allowed are Sonnet high (default)
  and Opus high (hard); never Haiku, never below Sonnet high. If feature-implementer stops after two
  evidence-backed failures on a task, re-dispatch that task once with `model: opus`, effort xhigh (same prompt plus
  its report); if that also fails, stop and ask the owner. Never switch this session's own model mid-session (it
  re-reads the conversation uncached).
- Superpowers subagent-driven-development: its "Model Selection" section applies to feature-implementer only, within
  the two combinations above. Per-task reviews, re-reviews and the final review go to code-reviewer with no `model`
  parameter (it keeps its Opus high agent file). Where the skill escalates to "a more capable model" (fix rounds
  4-5, stuck implementer), re-dispatch feature-implementer with `model: opus`, effort xhigh instead.
- Bug: use Superpowers systematic-debugging; write a failing test first.
- Before a PR: run the code-reviewer and security-reviewer subagents; fix Blocker and Major findings.
- When BE work for a feature is done: run /api-handoff.
- Architecture questions: read docs/architecture/backend-architecture.md first, then only the
  layer-guide.md section you need.
