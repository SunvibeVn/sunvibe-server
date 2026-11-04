---
paths:
  - "**/src/test/**"
---
# Testing rules
- Test first: a new behavior or a bug fix starts with a test you watched fail for the right reason.
- JUnit Jupiter (6.x through the Boot BOM; the imports stay org.junit.jupiter.api). Name tests
  method_condition_expectedResult (existing style), e.g. execute_wrongPassword_countsTheFailure.
- Service logic: unit tests with the in-memory fakes in <module>/src/test/.../testsupport/
  (InMemory*Repository, Recording*). Prefer fakes over Mockito; Spring Boot 4 has no @MockBean.
- Persistence adapters: @DataJpaTest-style slice tests on the H2 test profile for the mapping.
- PostgreSQL behavior (constraints, native SQL, Flyway): test it on PostgreSQL with Testcontainers through
  vn.sunvibe.PostgresContainerConfig (@Import it with spring.flyway.enabled=true and ddl-auto=validate, like
  FlywayPostgresMigrationTest). Docker must run locally and in CI; these tests are never skipped without it.
- Endpoints: API tests over HTTP (SpringBootTest RANDOM_PORT) through the real filter chain: the happy path, 401
  without a token, 403 for a wrong role, the ownership case (another owner's resource answers as the spec says)
  and the spec's error codes (assert the ErrorResponse `code`, not only the status).
- Time: AppClock is static wall time, not a test seam. To test a time rule, pass `Instant now` into it (SurveyTimeRules)
  or inject a port such as MediaClock. The rule for main code is in `java-core.md`.
- One module: ./gradlew :modules:<module>:test. Before "done": ./gradlew build.
- Some items adapted from ECC skills/springboot-tdd (commit 76aaa77, MIT, Copyright (c) 2026 Affaan Mustafa);
  its Maven, JaCoCo and @MockBean advice does not apply. See ../docs/setup/AI_FLOW.md section 12.

## Test review additions

Use `review-policy.md` for evidence/severity. Each item below comes from a bug this project shipped or nearly shipped.

- **Mandatory:** a test must be able to fail: it goes red when the behavior breaks, and it asserts the effect (row,
  status, `code`), not only that nothing was thrown.
- **Mandatory:** a race regression forces the interleaving with latches or barriers on the store that owns the race,
  like SurveyConcurrencyPostgresTest, ChecklistConcurrencyPostgresTest and OwnerLeadConcurrencyPostgresTest; a sleep
  proves no order. A concurrency or timing fix needs a Linux run: two such tests passed on Windows and failed on Linux
  CI every time (SV-068). Review comes before the PR, so it records that run as pending, and the issue closes after it.
- **Mandatory:** do not assert a tight wall-clock deadline or time a server deadline from a slow client on loopback;
  use an injected clock or a generous bound (SV-068).
- **Mandatory:** a fixture writes a time the app compares with its clock from the JVM, not with SQL now(): the Docker
  PostgreSQL clock can run ahead of the JVM (about 0.7 s measured on Windows), which fails only in a warmed full build.
- **Mandatory:** an error Tomcat raises itself (short body, timeout, the /error dispatch) needs a test over a real
  HTTP stack, not only a controller exception (SV-069).
- **Mandatory:** the save of a loaded aggregate is tested on PostgreSQL with its child rows seeded first
  (`../docs/known-issues.md`, 2026-10-21).
