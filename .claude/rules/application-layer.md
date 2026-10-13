---
paths:
  - "modules/*/src/main/java/**/core/application/**"
---
# Application layer rules (details: docs/architecture/layer-guide.md#application-layer-application)
- Commands (application/command): Java records, validation in the compact constructor, no business logic.
- Queries (application/query) for read models; keep reads and writes in separate use cases.
- Use cases (application/usecase): one interface per operation with a single execute(...) method.
- Implementations: @Service + @Transactional, implement the use case, orchestrate domain objects
  through domain repository interfaces. Location follows the module:
  new modules and property-service -> application/service/; existing auth-service -> application/usecase/<area>/impl/.
- External systems (email, SMS, audit, token) are ports (interfaces) here; adapters live in infrastructure.
- Never import infrastructure.* or presentation.*.
