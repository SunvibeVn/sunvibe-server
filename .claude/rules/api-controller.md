---
paths:
  - "modules/*/src/main/java/**/presentation/controller/**"
  - "modules/*/src/main/java/**/presentation/dto/**"
  - "modules/*/src/main/java/**/presentation/converter/**"
  - "modules/*/src/main/java/**/presentation/exception/**"
  - "**/src/main/java/**/*Controller.java"
  - "app/src/main/java/**/*ExceptionHandler.java"
  - "api/openapi.json"
---
# Controller rules
- Thin controllers: validate the request DTO (@Valid record), convert it to a Command with a
  presentation converter, call one UseCase, map the result to a response DTO.
- Inject UseCase interfaces only; never repositories, adapters or JPA entities.
- Every endpoint: OpenAPI annotations (@Operation summary, @ApiResponse per error code)
  and an explicit auth rule (public paths are listed in app/.../config/SecurityConfig.java).
- After changing any endpoint or DTO: ./gradlew exportOpenApi and commit api/openapi.json.
- New paths: /api/v1/<resource>, plural nouns, kebab-case. Do not copy the legacy auth paths.
  A state change is usually a noun sub-resource (.../cancellation, .../decision, .../status-changes).
  Exception (ADR-008): the auth actions under /api/v1/auth/** and /api/v1/me/** are verb paths
  (login, session/refresh, password-reset/complete, password/change); do not add verb paths elsewhere unless
  ../docs/rebuild/13-api-contracts.md or an approved spec names one (.../assign and .../hold exist).
- Errors: throw application/domain exceptions; the shared handlers return
  vn.sunvibe.shared.dto.response.error.ErrorResponse. Never leak exception messages or stack traces.

## Contract and error review additions

Use `review-policy.md` for evidence/severity; access control is in `security.md`.

- **Mandatory:** the contract states what the server really sends. A response field that can be null is declared
  nullable (SV-020); a string limited to a set lists every value the server can send, through
  `@Schema(allowableValues)` (SV-085); a numeric field is not exported as a string (SV-021). Never edit
  api/openapi.json by hand.
- **Mandatory:** a new domain or application exception gets the status and stable `code` the spec defines (usually a
  4xx; a temporary outage may be 503), in the module's exception handler, with a test on both. Unmapped, it falls to a
  catch-all: 500, or 404 for a BaseBusinessException under the global handler. A catch-all handler keeps the
  framework's own 4xx statuses and headers (405 `Allow`, 415 `Accept`). See `../docs/known-issues.md`, 2026-10-15 and
  2026-10-21.
