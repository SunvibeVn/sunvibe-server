# Code review policy

Applies to every review, including agent-generated code. Supplements the backend checklist and the hub
code-reviewer instructions; it does not change owner gates or specialist-review triggers.

## Applicability and evidence

- **Mandatory:** read the requested diff and enough callers, contracts and implementations to trace its behavior.
  Before calling a check missing, look where it may already be enforced: caller, filter, exception handler, database
  constraint, ArchUnit test, configuration. Record the base/head or working-tree scope, feature spec/plan and
  verification supplied; mark missing inputs.
- **Mandatory:** use the configured baseline: Java 21 in `gradle/conventions/java-library-convention.gradle.kts`
  and Spring Boot 4.1.1 BOM in `build.gradle.kts`. Re-check these files when versions change; do not require newer
  APIs, preview features, framework upgrades or a different module layout as a review preference. For a
  version-specific claim use the resolved dependency, not a guide's version:
  `./gradlew :app:dependencyInsight --dependency hibernate-core --configuration runtimeClasspath`.
- **Mandatory:** apply `AGENTS.md`, the relevant Accepted ADRs in `../docs/decisions/`, approved feature decisions
  and the matching layer rules. Report unresolved source conflicts through the existing authority process;
  examples, generated code and generic online advice cannot override project decisions.
- **Mandatory:** explicitly read the applicable files in `.claude/rules/`: `java-core.md`,
  `performance-concurrency.md`, the touched layer rules, and the security, testing and Flyway rules when relevant.
  A path-scoped rule loads by itself only when a matching file is opened, so reading `git diff` output does not load
  it, and other tools do not load these files at all. Do not duplicate their checklists here.
- **Mandatory:** a finding states the triggering input/interleaving, the violated contract or rule, the failure
  path and impact, a precise changed `file:line` (or the nearest relevant line), and a concrete minimal remedy.
  Deterministic code reasoning is evidence; use a reproducer/test/log when needed. Do not invent test results.
- **Mandatory:** separate confirmed defects from questions or unverified risks. State assumptions and what would
  confirm them; do not present missing context, a hypothetical future scale or a library preference as a proven bug.
- **Mandatory:** focus on introduced defects and existing defects made reachable/worse by the diff. Label unrelated
  pre-existing issues separately; reuse an applicable SV- ID from `../docs/product/issues.md`. A read-only reviewer
  returns candidate findings to the coordinating writer and does not change files or the issue register.

## Requirement level and severity

- **Mandatory** means required when its stated condition applies. **Advisory** means a context-dependent suggestion.
  An unlabeled bullet in a rule file is a project convention: treat it as Mandatory unless its own wording says `may`
  or `prefer`. These labels describe rules; findings still use **Blocker / Major / Minor / Nit**, with severity
  justified by impact.
- **Blocker:** demonstrated critical exposure, destructive data loss/corruption or a failure that prevents safe use
  of the reviewed slice. State the affected scope and why it blocks progress.
- **Major:** a material correctness, security, transaction, compatibility or architecture failure in an applicable
  path, including a credible lost-update race or broken hard project constraint. Explain the concrete consequence.
- **Minor:** a localized defect with limited impact. **Nit:** optional clarity/style improvement without a defect.
  Avoid noisy nits; a style preference with no written rule is at most a Nit. Advisory advice alone is non-blocking;
  an independently demonstrated defect gets its own severity.
- **Mandatory:** do not automatically label every Mandatory violation Blocker, or downgrade a real defect because
  it appeared under an Advisory topic. Existing findings retain their source severity unless explicitly re-triaged
  with evidence under the issue-register process.
- **Mandatory:** follow `../docs/setup/AI_FLOW.md` section 8: fix every Blocker/Major and obtain re-review, or escalate
  to the owner. This policy grants no waiver, deferral, business decision or gate approval.

## Report

Findings in descending severity, each with evidence and fix; optional suggestions clearly labeled; then **Not
checked** with missing inputs, checks not run and reasons. Name the commands/results actually observed and their
scope. A compile, fake/H2 test, full PostgreSQL build, CI run and deployment each prove different things; follow
the existing `testing.md` and `AGENTS.md` verification requirements without claiming one proves another.
