# Sunvibe Server
Rental real-estate brokerage API for sunvibe.vn. Spring Boot 4.1.1, Java 21 (Gradle toolchain), PostgreSQL, Flyway, JWT (jjwt 0.12.6).
Gradle 9.7.1 wrapper, Kotlin DSL, multi-module modular monolith.

## Commands
- Build: ./gradlew build
- Unit tests: ./gradlew test (one module: ./gradlew :modules:auth-service:test)
- Run local: start a local PostgreSQL, set the env vars from .env.example in your shell or IDE run config
  (Spring does not read .env files), then ./gradlew :app:bootRun --args='--spring.profiles.active=dev'
  Default port 8082 (SERVER_PORT).
- Export API contract: ./gradlew exportOpenApi -> api/openapi.json (commit it). Swagger UI (local): http://localhost:8082/swagger-ui/index.html

## Modules
- app: Spring Boot entry point (vn.sunvibe.SunvibeServerApplication) and the single SecurityFilterChain (app/.../config/SecurityConfig.java).
- modules/common: shared exceptions, ErrorResponse, crypto, ID generators, AppClock.
- modules/auth-service: login, refresh-token rotation, registration, contact verification, password reset, audit, rate limit.
- modules/property-service: properties (prices, images, amenities, rules, documents, availability).
- Other modules under modules/ are empty placeholders. A new module must be added to settings.gradle.kts and app/build.gradle.kts.

## Architecture
- DDD + Hexagonal + CQRS. Package root per module: vn.sunvibe.<module>.core.{domain,application,infrastructure,presentation,shared}.
- Flow: Controller -> UseCase interface -> Service (@Transactional) -> domain repository interface
  -> infrastructure adapter -> JPA repository. Controllers inject use cases, never repositories.
- Domain is pure Java: no Spring/JPA annotations. Aggregates use create()/load() factories; never reflection.
- Commands and DTOs are Java records. Request DTO -> Command via presentation converters.
- DTOs at the API edge; never return JPA entities or domain objects.
- New modules follow the property-service layout. Inside auth-service keep its existing variant (usecase/<area>/impl, persistence/converter).
- Full guide: docs/architecture/backend-architecture.md (overview, package tree, dependency rules)
  and docs/architecture/layer-guide.md (per-layer rules with examples). Cross-repo: ../docs/architecture.md

## Hard rules
- NEVER edit a committed Flyway migration. Add a new one (see .claude/rules/flyway.md).
- NEVER read or print .env, secrets, or JWT signing keys.
- Every new endpoint: OpenAPI annotations (springdoc, io.swagger.v3.oas.annotations) + explicit authorization rule + test.
- Errors use the shared format: vn.sunvibe.common.dto.response.error.ErrorResponse(message, errors),
  produced by common/exception/GlobalServerExceptionHandler or a module *ExceptionHandler. Never leak stack traces.
- New endpoints go under /api/v1/<resource>. Existing auth paths (/api/auth, /api/users/command, /api/user/query) are legacy.
- CORS: explicit origin allow-list from config (app.cors.allowed-origins), allowCredentials=true. Never "*".
  See ../docs/decisions/ADR-001-direct-browser-api.md
- Schema changes go through Flyway only (modules/<module>/src/main/resources/db/migration). Do not rely on ddl-auto.

## Before saying "done"
1. ./gradlew test passes
2. api/openapi.json regenerated if the API changed
3. Feature doc updated (run /update-docs from the hub)

## Context
- Feature docs: ../docs/features/INDEX.md (read only the feature you work on)
- Past bugs: ../docs/known-issues.md
- Business terms: ../docs/product/glossary.md
- Auth flow details: docs/dev_docs/auth/; DB schema notes: docs/dev_docs/database/
