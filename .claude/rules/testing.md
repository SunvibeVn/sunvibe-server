---
paths:
  - "**/src/test/**"
---
# Testing rules
- Test first: a new behavior or a bug fix starts with a test you watched fail for the right reason.
- JUnit 5. Name tests method_condition_expectedResult (existing style), e.g. execute_wrongPassword_countsTheFailure.
- Service logic: unit tests with the in-memory fakes in <module>/src/test/.../testsupport/
  (InMemory*Repository, Recording*). Prefer fakes over Mockito; Spring Boot 4 has no @MockBean.
- Persistence adapters: @DataJpaTest-style slice tests on the H2 test profile for the mapping.
- PostgreSQL behavior (constraints, native SQL, Flyway): test it on PostgreSQL with Testcontainers through
  vn.sunvibe.PostgresContainerConfig (@Import it with spring.flyway.enabled=true and ddl-auto=validate, like
  FlywayPostgresMigrationTest). Docker must run locally and in CI; these tests are never skipped without it.
- Endpoints: API tests over HTTP (SpringBootTest RANDOM_PORT) through the real filter chain: the happy path, 401
  without a token, 403 for a wrong role, the ownership case (another owner's resource answers as the spec says)
  and the spec's error codes (assert the ErrorResponse `code`, not only the status).
- Use AppClock (or a fixed clock) instead of LocalDateTime.now() so time-based rules are testable.
- One module: ./gradlew :modules:<module>:test. Before "done": ./gradlew build.
- Some items adapted from ECC skills/springboot-tdd (commit 76aaa77, MIT, Copyright (c) 2026 Affaan Mustafa);
  its Maven, JaCoCo and @MockBean advice does not apply. See ../docs/setup/AI_FLOW.md section 12.
