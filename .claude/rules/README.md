---
paths:
  - ".claude/rules/**"
---
# Backend rules: index and upkeep

Loads only when a rule file is opened; read it before changing a rule. Each rule serves two readers: the session
writing the code and the reviewer judging it (hub code-reviewer, security-reviewer, migration-specialist, or another
tool through `AGENTS.md`).

## Index

| File | Loads on | Owns |
|---|---|---|
| `review-policy.md` | every session (no `paths`, on purpose) | evidence, requirement levels, severity, report format |
| `java-core.md` | any Java file | collections, equality, strings, exceptions, money, time |
| `naming-layout.md` | main Java, the architecture tests | packages, class names, what each word means |
| `performance-concurrency.md` | main Java, application yml, Gradle files | limits, shared state, async work, scheduled jobs, blocking I/O |
| `domain-layer.md` | core/domain | aggregates, value objects, domain repositories |
| `application-layer.md` | core/application, the integration adapters in app | use cases, ports, transaction boundaries |
| `persistence.md` | core/infrastructure/persistence | mapping, queries, locks, conflicts, isolation |
| `api-controller.md` | controllers, DTOs, converters, resolvers, exception handlers, api/openapi.json | endpoint shape, contract truth, error mapping |
| `security.md` | config, security, auth, presentation, core/application, core/infrastructure, application yml | authentication, authorization, input, logs, rate limits |
| `flyway.md` | db/migration | naming, compatibility, locks on populated tables |
| `testing.md` | test sources | test levels, time, races, real HTTP errors |

The checklists in `../docs/setup/checklists/` say what to check; these files hold the detail behind each item. Accepted
ADRs, approved specs and briefs outrank a rule, and a rule never decides business policy.

## Writing a rule

- Verify first. A rule that names a class, config key, folder or library behavior is written after reading it in this
  repo, or in the official docs of the resolved version. Cite the source: an SV- ID, an ADR, a
  `../docs/known-issues.md` date or a doc link.
- State the condition, the failure and the remedy, so a reviewer can tell a defect from a preference. A new review
  check carries **Mandatory** or **Advisory** (`review-policy.md` defines them).
- Describe what a module does as observed; only the owner turns a variant into a decision. No business rule here.
- One topic lives in one file and the others point to it: when two rules disagree, the reader may follow either.
- A new file gets `paths`: without them it loads in every session. Keep each file short enough to read whole, well
  under 200 lines.
- Keep the ECC attribution lines (MIT) in `persistence.md`, `security.md` and `testing.md`.

## When to update

| Change | Update |
|---|---|
| Java or Spring Boot baseline | the baseline in `review-policy.md`; version notes and links: Java 21 in `java-core.md` and `performance-concurrency.md`, Hibernate guide in `persistence.md`, JUnit line in `testing.md` |
| PostgreSQL server or test image | the version 17 notes and links in `flyway.md`, `persistence.md` and `java-core.md` |
| New module or moved folder | `application-layer.md`, `domain-layer.md`, `persistence.md`, `flyway.md`, the `paths` lists, and "Variants in use" in `docs/architecture/backend-architecture.md` |
| A stated fact changes (open-in-view, actuator, JDBC batching, ShedLock, the rate limiter, forward headers, Hikari or Tomcat sizes, logServerErrorDetail) | the sentence that states it, in the same PR |
| A bug worth a check lands in `../docs/known-issues.md` or `../docs/product/issues.md` | add the check to the owning file and cite it; the citation stays after the issue closes, while the check guards it |

## Checks after a change

- `node ../scripts/ai-config/sunvibe-ai-config.mjs check`: frontmatter and every referenced path.
- `node --test "scripts/tests/*.test.mjs"` when a hook or CI script changed with the rule.
- A new or changed `paths` glob: confirm it matches a real file.
- Independent review as for any PR (`../docs/setup/AI_FLOW.md` section 8).
