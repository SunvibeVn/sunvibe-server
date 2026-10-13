---
paths:
  - "modules/*/src/main/java/**/core/domain/**"
---
# Domain layer rules (details: docs/architecture/layer-guide.md#domain-layer-domain)
- Pure Java. No Spring, JPA or Jackson annotations; no imports from application, infrastructure
  or presentation.
- Aggregates (domain/model/.../aggregate): private constructor that checks invariants,
  create(...) for new objects, load(...) for rebuilding from the database, business methods
  such as changeStatus(...). No public setters.
- Value objects (domain/model/.../vo): extend ValueObject<T>, private constructor, of()/generate()
  factories, self-validating, implement equalsCore()/hashCodeCore().
- Repository interfaces (domain/repository): <Entity>DomainRepository, domain types only
  (PropertyId, not String), no Spring Data types.
- Business rule violations throw exceptions from domain/exception.
