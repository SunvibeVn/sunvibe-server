---
paths:
  - "modules/*/src/main/java/**/core/domain/**"
---
# Domain layer rules (details: docs/architecture/layer-guide.md#domain-layer-domain)
- Pure Java. No Spring, JPA or Jackson annotations; no imports from application, infrastructure
  or presentation. Lombok remains only in auth-service's domain; do not add it elsewhere. @Setter and
  @NoArgsConstructor bypass the invariants (AppUser is legacy: do not copy it).
- Aggregates (domain/model/aggregate for Property, domain/model/user/aggregate for AppUser, otherwise a concept folder
  such as domain/model/unit or domain/model/lead): private constructor that checks invariants, create(...) for new
  objects, load(...) for rebuilding from the database, business methods such as changeStatus(...). No public setters.
- Value objects: immutable and self-validating, in one of two forms. auth-service, property-service and
  acquisition-service extend their module's ValueObject<T>: private constructor, of()/generate() factories,
  equalsCore()/hashCodeCore(). Records, the usual form in survey-service, validate in the compact constructor.
- Repository interfaces (domain/repository): <Entity>DomainRepository (a few ports end in Repository alone, e.g.
  MediaCountersRepository), domain types only (PropertyId, not String), no Spring Data types.
- Business rule violations throw exceptions from domain/exception.
- **Advisory:** a time rule takes `Instant now` as a parameter, as SurveyTimeRules does, so a test can fix the time;
  an aggregate that reads AppClock itself (auth-service, property-service, acquisition-service) cannot be tested that way.
