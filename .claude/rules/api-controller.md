---
paths:
  - "modules/*/src/main/java/**/presentation/controller/**"
  - "modules/*/src/main/java/**/*Controller.java"
---
# Controller rules
- Thin controllers: validate the request DTO (@Valid record), convert it to a Command with a
  presentation converter, call one UseCase, map the result to a response DTO.
- Inject UseCase interfaces only; never repositories, adapters or JPA entities.
- Every endpoint: OpenAPI annotations (@Operation summary, @ApiResponse per error code)
  and an explicit auth rule (public paths are listed in app/.../config/SecurityConfig.java).
- After changing any endpoint or DTO: ./gradlew exportOpenApi and commit api/openapi.json.
- New paths: /api/v1/<resource>, plural nouns, kebab-case. Do not copy the legacy auth paths.
- Errors: throw application/domain exceptions; the shared handlers return
  vn.sunvibe.shared.dto.response.error.ErrorResponse. Never leak exception messages or stack traces.
