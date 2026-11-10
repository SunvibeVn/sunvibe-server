# Sunvibe Server
Rental real-estate brokerage API for sunvibe.vn. Spring Boot 4.1.1, Java 21 (Gradle toolchain), PostgreSQL, Flyway, JWT (jjwt 0.12.6).
Gradle 9.7.1 wrapper, Kotlin DSL, multi-module modular monolith.

## Commands
- Build: ./gradlew build
- Unit tests: ./gradlew test (one module: ./gradlew :modules:auth-service:test)
- Run local: start a local PostgreSQL, set the env vars from .env.example in your shell or IDE run config
  (Spring does not read .env files), then ./gradlew :app:bootRun --args='--spring.profiles.active=dev'
  Default port 8081 (SERVER_PORT).
  Outside the dev/test profiles these env vars are required (no default in application.yml): MAIL_USERNAME,
  MAIL_PASSWORD, JWT_SECRET (base64). A missing JWT_SECRET fails startup. A missing MAIL_USERNAME fails startup only when
  MAIL_FROM is unset too; with MAIL_FROM set it fails when mail is sent, like a missing MAIL_PASSWORD. The dev profile has a
  public dev-only JWT_SECRET and empty mail values; env vars win if set.
  Also required outside dev/test: ID_CARD_CRYPTO_KEY and ID_CARD_CRYPTO_INDEX_KEY (two different base64 32-byte keys),
  ACQUISITION_HASH_KEY and AUTH_OTP_HASH_KEY (base64, at least 32 bytes); each fails startup when missing. The dev profile
  has dev-only values for them. SMS_PROVIDER is `none` unless set (`log` exists for dev/test only).
  Auth: AUTH_PHONE_MODE is `off` unless set (`legacy-login` needs AUTH_PHONE_LEGACY_LOGIN_UNTIL, `on` needs an SMS provider;
  the guards stop the start-up otherwise), AUTH_FRONTEND_BASE_URL is the web app of the reset link, and MAIL_HOST, MAIL_PORT,
  MAIL_SMTP_AUTH, MAIL_STARTTLS, MAIL_FROM and AUTH_MAIL_MAX_PER_HOUR set the mail: .env.example lists them. Deployment
  templates for Brevo SMTP: scripts/ops/env/, explained in scripts/ops/README.md section 9.
- Run on the LAN (phone/tablet testing): set LAN_FE_ORIGIN=http://<LAN-IP>:3000, then
  ./gradlew :app:bootRun --args='--spring.profiles.active=dev,lan' (refresh cookie not Secure; never with prod).
  Steps for both repos: ../docs/setup/lan-device-testing.md
- E2E seed accounts (local DB only; DATABASE_URL is a libpq URL such as postgresql://sunvibe@localhost:5432/sunvibe,
  not the JDBC DB_URL): scripts/dev/seed-e2e-accounts.sql, run with
  psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -v target=local -v customer_password="$E2E_CUSTOMER_PASSWORD"
  -v sale_password="$E2E_SALE_PASSWORD" -v leader_password="$E2E_LEADER_PASSWORD"
  -v admin_password="$E2E_ADMIN_PASSWORD" -f scripts/dev/seed-e2e-accounts.sql (re-run resets passwords and roles;
  creates the CUSTOMER, SALE, SALE_LEADER 0900000103 and ADMIN 0900000104 accounts with verified e2e.*@sunvibe.test emails,
  and the phone-only CUSTOMER 0900000105),
  then npm run e2e in sunvibe-ui with the same env vars. Never commit the passwords. Both seed scripts refuse a
  server that is not on this machine (psql host other than localhost, 127.0.0.1, ::1 or a socket), even with target=local.
- E2E lead fixtures (local DB only; run AFTER the accounts seed, which it needs): fifteen owner leads (L13, L14 and L15
  with a visited survey, ready for a checklist; L14 for the survey photo flows, L15 for the review flow), one territory, fixed ids: psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -v target=local -f scripts/dev/seed-e2e-owner-leads.sql
  (idempotent: deletes and re-inserts only its own fixture rows, their survey photos included). The survey photo E2E
  API run: scripts/dev/run-e2e-media-api.sh or .ps1 (fresh local store, stub scanner, dev processing container). `npm run e2e:workspace` in sunvibe-ui (the lead
  queue workspace) uses both seeds.
- The auth E2E API run: scripts/dev/run-e2e-auth-api.sh or .ps1 [off|legacy-login|on] (Mailpit on 127.0.0.1:8025, profile dev,
  that phone channel, the API log in build/e2e-auth-api.log for the codes of the `log` SMS sender).
- Export API contract: ./gradlew exportOpenApi -> api/openapi.json (commit it; CI fails when the export differs from
  the committed file). Swagger UI (local): http://localhost:8081/swagger-ui/index.html
- Migration history: bash scripts/ci/check-migration-history.sh origin/main --include-worktree (CI job
  migration-history fails a PR that changes a migration of its base branch).
- Tooling tests (Claude/Codex hooks, CI scripts; temporary Git repos only): node --test "scripts/tests/*.test.mjs"

## Modules
- app: Spring Boot entry point (vn.sunvibe.SunvibeServerApplication) and the single SecurityFilterChain (app/.../config/SecurityConfig.java).
- modules/shared-service: shared exceptions, ErrorResponse, crypto, ID generators, AppClock (package vn.sunvibe.shared).
- modules/auth-service: login by email or phone, refresh-token rotation, registration, email bind and replace, phone
  verification, password reset, audit, rate limit. A Java Flyway migration lives in src/main/java/db/migration.
- modules/property-service: properties (prices, images, amenities, rules, documents, availability), units and
  ownership, the province/ward catalog.
- modules/acquisition-service: owner intake (public form), owner leads, ward territories, the staff lead queue
  and the requirement change of a lead.
- modules/survey-service: survey appointments of owner leads (booking, change, confirmation, cancellation, visit,
  re-survey, resume) and the staff survey list; it moves leads only through the use case acquisition publishes.
- modules/listing-service, modules/notification-service: empty placeholders (target names, ADR-003). A new module must be added to settings.gradle.kts and app/build.gradle.kts.

## Architecture
- DDD + Hexagonal + CQRS. Package root per module: vn.sunvibe.<module>.core.{domain,application,infrastructure,presentation,shared}.
- Flow: Controller -> UseCase interface -> Service (@Transactional) -> domain repository interface
  -> infrastructure adapter -> JPA repository. Controllers inject use cases, never repositories.
- Domain is pure Java: no Spring/JPA annotations. Aggregates use create()/load() factories; never reflection.
- Commands and DTOs are Java records. Request DTO -> Command via presentation converters.
- DTOs at the API edge; never return JPA entities or domain objects.
- Packages and class names: .claude/rules/naming-layout.md, one standard for every module.
- Cross-module calls (../docs/decisions/ADR-009-cross-module-ports.md): the consumer declares a port in its
  core/application/shared/external/<topic>/, the provider publishes a use case, and the adapter lives in app
  (vn.sunvibe.integration.<Provider><Topic>Adapter, with a unit test). No Gradle dependency between feature modules:
  only feature -> shared-service and app -> any module. ./gradlew checkModuleBoundaries (part of check/build) fails otherwise.
- Full guide: docs/architecture/backend-architecture.md (overview, package tree, dependency rules)
  and docs/architecture/layer-guide.md (per-layer rules with examples). Cross-repo: ../docs/architecture.md

## Comments (../docs/rebuild/implementation/2026-10-14-g2-target-decisions.md D-42)
- No wave tags (W6, W8, W9a ...) in main code, config or new migrations; history is in git and the as-built docs.
- A rule id always names its feature (`property-units BR-12`, `auth-roles BR-8`, `rebuild BR-001`), never a bare
  `BR-8`, and the comment states the rule in words. Tests keep feature-prefixed AC/BR ids and spec ACs (`W9-7`).

## Hard rules
- NEVER edit a committed versioned Flyway migration. Add a new one (see .claude/rules/flyway.md).
- NEVER read or print .env, secrets, or JWT signing keys.
- NEVER commit a secret: CI gitleaks-scans new commits (.github/workflows/secret-scan.yml). A false positive goes
  into .gitleaksignore as a fingerprint (never the value), only after owner review.
- Every new endpoint: OpenAPI annotations (springdoc, io.swagger.v3.oas.annotations) + explicit authorization rule + test.
- Errors use the shared format: vn.sunvibe.shared.dto.response.error.ErrorResponse (code, message, errors,
  correlationId; ../docs/decisions/ADR-010-error-envelope-and-correlation-id.md). New errors are built with
  ErrorResponse.of(code, message, errors) and carry a stable code. They come from
  vn.sunvibe.config.GlobalServerExceptionHandler (app) or a module *ExceptionHandler. Never leak stack traces.
- New endpoints go under /api/v1/<resource>. The legacy auth paths (/api/auth, /api/users/command, /api/user/query)
  were removed in W10c (auth-browser-session); never re-add them.
- CORS: explicit origin allow-list from config (app.cors.allowed-origins), allowCredentials=true. Never "*".
  See ../docs/decisions/ADR-001-direct-browser-api.md
- Schema changes go through Flyway only (modules/<module>/src/main/resources/db/migration). Do not rely on ddl-auto.

## Before saying "done"
Each step is its own evidence; a passing compile (the Claude Stop hook) is not a passing build.
1. ./gradlew build passes: unit and API tests, the PostgreSQL tests on Testcontainers (Docker must run), ArchUnit
   and checkModuleBoundaries.
2. API changed: ./gradlew exportOpenApi and commit api/openapi.json; a second export leaves it unchanged.
3. No migration of the base branch changed: bash scripts/ci/check-migration-history.sh origin/main --include-worktree
   (it compares with the fork point; a migration committed earlier on this branch is protected only by review).
4. Independent review without open Blocker or Major findings, against ../docs/setup/checklists/be-review.md and
   .claude/rules/review-policy.md (Claude: code-reviewer, plus security-reviewer / migration-specialist when their
   triggers apply).
5. Docs: run /update-docs <module>/<feature> in this session when the feature or fix is finished (it edits ../docs;
   the hub is loaded via --add-dir), unless a flow run (hub scripts/flow, or Autoflow) does it in its docs stage. A Stop hook
   reminds you once per session on feature/*, fix/* and rebuild/* branches. Then remind the owner to commit the hub docs.
CI runs the build, the contract freshness check, migration-history and the tooling tests; whether a red check blocks
a merge depends on GitHub branch protection, which is not assumed here.

## Context
- Feature docs: ../docs/features/INDEX.md, then ../docs/features/<module>/<feature>/ (brief = business truth, read
  only; as-built = current behavior; impl/ = spec, plan, handoff; latest spec and plan only). Shared module rules:
  ../docs/features/<module>/brief/ if present. Read only the feature you work on.
- Past bugs: ../docs/known-issues.md
- Layer, review and security rules: .claude/rules/ (index and upkeep: .claude/rules/README.md). Claude Code loads them
  by file path; other tools read them directly.
- Business terms: ../docs/product/glossary.md
- Auth flow details: ../docs/features/auth/ (original Vietnamese docs: assets/dev_docs/); DB schema notes: docs/dev_docs/database/
