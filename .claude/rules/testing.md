---
paths:
  - "**/src/test/**"
---
# Testing rules
- JUnit 5. Name tests method_condition_expectedResult (existing style),
  e.g. execute_wrongPassword_countsTheFailure.
- Service logic: unit tests with the in-memory fakes in <module>/src/test/.../testsupport/
  (InMemory*Repository, Recording*). Prefer fakes over Mockito.
- Persistence adapters: @DataJpaTest-style slice tests. Endpoints: tests that include the auth rule.
- Bug fix: add a failing test that reproduces the bug first.
- Tests run on H2 today. Prefer real PostgreSQL (Testcontainers) once it is added.
- Use AppClock (or a fixed clock) instead of LocalDateTime.now() so time-based rules are testable.
