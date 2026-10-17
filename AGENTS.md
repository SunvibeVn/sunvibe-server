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
  MAIL_PASSWORD, JWT_SECRET (base64). A missing JWT_SECRET or MAIL_USERNAME fails startup; a missing MAIL_PASSWORD
  fails only when mail is sent. The dev profile has a public dev-only JWT_SECRET and empty mail values; env vars win if set.
- Export API contract: ./gradlew exportOpenApi -> api/openapi.json (commit it). Swagger UI (local): http://localhost:8081/swagger-ui/index.html

## Modules
- app: Spring Boot entry point (vn.sunvibe.SunvibeServerApplication) and the single SecurityFilterChain (app/.../config/SecurityConfig.java).
- modules/shared-service: shared exceptions, ErrorResponse, crypto, ID generators, AppClock (package vn.sunvibe.shared).
- modules/auth-service: login, refresh-token rotation, registration, contact verification, password reset, audit, rate limit.
- modules/property-service: properties (prices, images, amenities, rules, documents, availability).
- modules/listing-service, modules/notification-service: empty placeholders (target names, ADR-003). A new module must be added to settings.gradle.kts and app/build.gradle.kts.

## Architecture
- DDD + Hexagonal + CQRS. Package root per module: vn.sunvibe.<module>.core.{domain,application,infrastructure,presentation,shared}.
- Flow: Controller -> UseCase interface -> Service (@Transactional) -> domain repository interface
  -> infrastructure adapter -> JPA repository. Controllers inject use cases, never repositories.
- Domain is pure Java: no Spring/JPA annotations. Aggregates use create()/load() factories; never reflection.
- Commands and DTOs are Java records. Request DTO -> Command via presentation converters.
- DTOs at the API edge; never return JPA entities or domain objects.
- New modules follow the property-service layout. Inside auth-service keep its existing variant (usecase/<area>/impl, persistence/converter).
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
- NEVER edit a committed Flyway migration. Add a new one (see .claude/rules/flyway.md).
- NEVER read or print .env, secrets, or JWT signing keys.
- NEVER commit a secret: CI gitleaks-scans new commits (.github/workflows/secret-scan.yml). A false positive goes
  into .gitleaksignore as a fingerprint (never the value), only after owner review.
- Every new endpoint: OpenAPI annotations (springdoc, io.swagger.v3.oas.annotations) + explicit authorization rule + test.
- Errors use the shared format: vn.sunvibe.shared.dto.response.error.ErrorResponse(message, errors),
  produced by vn.sunvibe.config.GlobalServerExceptionHandler (app) or a module *ExceptionHandler. Never leak stack traces.
- New endpoints go under /api/v1/<resource>. Existing auth paths (/api/auth, /api/users/command, /api/user/query) are legacy.
- CORS: explicit origin allow-list from config (app.cors.allowed-origins), allowCredentials=true. Never "*".
  See ../docs/decisions/ADR-001-direct-browser-api.md
- Schema changes go through Flyway only (modules/<module>/src/main/resources/db/migration). Do not rely on ddl-auto.

## Before saying "done"
1. ./gradlew test passes
2. api/openapi.json regenerated if the API changed
3. Run /update-docs <feature> in this session when the feature or fix is finished (it edits ../docs;
   the hub is loaded via --add-dir). A Stop hook reminds you once per session on feature/* and fix/* branches.
   Then remind the owner to commit the hub docs.

## Context
- Feature docs: ../docs/features/INDEX.md, then ../docs/features/<module>/<feature>/ (brief = business truth, read
  only; as-built = current behavior; impl/ = spec, plan, handoff; latest spec and plan only). Shared module rules:
  ../docs/features/<module>/brief/ if present. Read only the feature you work on.
- Past bugs: ../docs/known-issues.md
- Business terms: ../docs/product/glossary.md
- Auth flow details: ../docs/features/auth/ (original Vietnamese docs: assets/dev_docs/); DB schema notes: docs/dev_docs/database/
