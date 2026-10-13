# Backend architecture (sunvibe-server)

Moved from the former `CLAUDE.md` (2026-10-13). Per-layer rules with code examples:
[layer-guide.md](layer-guide.md). Cross-repo overview: `sunvibe-hub/docs/architecture.md`.

## Overview

This project follows a **Modular Monolith** architecture with **DDD + Hexagonal Architecture + CQRS** patterns.

- **Root module**: `app` (Spring Boot application, `vn.sunvibe.SunvibeServerApplication`, global `config/SecurityConfig`)
- **Shared module**: `modules/common` (exceptions, error DTOs, crypto, ID generators, `AppClock`)
- **Domain modules**: `modules/<name>-service`. With code today: `auth-service`, `property-service`.
  Empty placeholders: listing, booking, review, customer, payment, notification, campaign, analyst, email, chat.

Each module should follow the same architectural principles described in this document.

## Architecture patterns

### 1. Domain-Driven Design (DDD)
- **Aggregate Root**: The main entity that controls access to related entities
- **Value Objects**: Immutable objects identified by their attributes, not by ID
- **Domain Repository Interfaces**: Abstract persistence contracts in the domain layer
- **Rich Domain Model**: Business logic resides in domain objects

### 2. Hexagonal Architecture (Ports & Adapters)
**Core Principle:** Dependency inversion - the domain layer is independent of infrastructure.

Layers (from inner to outer):
1. **Domain Layer** (Core) - Business logic, domain models, repository interfaces
2. **Application Layer** - Use cases, commands, application services
3. **Infrastructure Layer** - Repository implementations, external services, persistence
4. **Presentation Layer** - Controllers, DTOs, API endpoints

**Dependency Rule:** Outer layers depend on inner layers, never the reverse.

### 3. CQRS (Command Query Responsibility Segregation)
- **Commands**: Write operations (create, update, delete)
- **Queries**: Read operations (find, search, list)
- **Use Cases**: Interfaces representing business operations
- **Services**: Implementations of use cases

## Module structure (reference layout)

```
modules/{module-name}/src/main/java/vn/sunvibe/{module}/core/
├── application/          # Application Layer
│   ├── command/          # CQRS Commands (immutable records)
│   ├── query/            # CQRS Queries (if needed)
│   ├── usecase/          # Use Case interfaces
│   ├── service/          # Service implementations
│   ├── exception/        # Application-specific exceptions
│   └── shared/           # Shared application components
├── domain/               # Domain Layer (Core)
│   ├── model/
│   │   ├── aggregate/    # Aggregate Roots
│   │   ├── vo/           # Value Objects
│   │   ├── enums/        # Domain enums
│   │   └── {entity}/     # Other domain entities
│   ├── repository/       # Domain Repository Interfaces
│   ├── service/          # Domain Services (if needed)
│   ├── event/            # Domain Events (if needed)
│   └── exception/        # Domain exceptions
├── infrastructure/       # Infrastructure Layer
│   ├── persistence/
│   │   ├── adapter/      # Repository adapters (implement domain interfaces)
│   │   ├── repository/   # JPA repositories (low-level)
│   │   ├── entity/       # JPA entities
│   │   └── mapper/       # Domain ↔ Entity mappers
│   ├── config/           # Infrastructure configuration
│   ├── api/              # External API clients
│   ├── cache/            # Cache implementations
│   ├── email/            # Email services
│   └── audit/            # Audit logging
├── presentation/         # Presentation Layer
│   ├── controller/       # REST controllers
│   ├── dto/
│   │   ├── request/      # Request DTOs
│   │   └── response/     # Response DTOs
│   ├── converter/        # DTO ↔ Command/Domain mappers
│   ├── exception/        # Presentation exception handlers
│   ├── validation/       # Custom validators
│   ├── openapi/          # OpenAPI documentation
│   └── scheduler/        # Scheduled tasks
└── shared/               # Shared cross-cutting concerns
    ├── config/           # Shared configuration
    ├── constant/         # Constants
    ├── runner/           # Application runners
    ├── service/          # Shared services
    └── utils/            # Utility classes
```

### Variants in use (observed 2026-10-13)

| Concern | property-service (matches the reference) | auth-service |
|---|---|---|
| Use case implementations | `application/service/<Op>Service` | `application/usecase/<area>/impl/<Op>Service` |
| Ports to external systems | `application/shared/external/<system>/` | `application/usecase/external/<system>/` |
| Queries | — | `application/query/<area>/` |
| Domain ↔ entity mappers | `infrastructure/persistence/mapper/manual/` | `infrastructure/persistence/converter/{auto,manual}/` (auto = MapStruct) |
| Security | — | `infrastructure/security/{config,filter,provider,ratelimit,userdetails}` |

TODO(owner): pick one layout as the standard for new modules.

## Dependency rules

```
Presentation  ──►  Application  ──►  Domain  ◄──  Infrastructure
(controllers,      (use cases,       (aggregates,   (adapters, JPA,
 DTOs, converters)  commands,         VOs, repo      entities, mappers)
                    services)         interfaces)
```

(The former CLAUDE.md drew Domain → Infrastructure; the text rules below are the intent.)

- Domain layer **MUST NOT** depend on any other layer
- Application layer depends only on Domain layer
- Infrastructure layer implements Domain layer interfaces
- Presentation layer depends on Application layer

Forbidden:
- ❌ Domain importing `infrastructure.*`, `presentation.*`, `application.*` (except shared types if absolutely necessary)
- ❌ Application importing `infrastructure.*`, `presentation.*`
- ❌ Presentation importing `infrastructure.*` (use application layer instead) or the domain layer directly (use use cases)

## Example flow: create entity

1. Controller receives HTTP request
2. RequestMapper converts Request DTO → Command
3. Service (UseCase implementation) executes Command
4. Service creates Domain object via factory method: `Aggregate.create()`
5. Service calls `Repository.save(domain)`
6. RepositoryAdapter converts Domain → Entity via `mapper.toEntity()`
7. JpaRepository saves Entity to database
8. ResponseMapper converts result → Response DTO
9. Controller returns HTTP response

## Module creation checklist

1. **Package structure** follows the standard layout
2. **Domain layer** has no external dependencies
3. **Repository interfaces** are in domain layer
4. **Repository implementations** are in infrastructure layer
5. **Use case interfaces** are in application layer
6. **Service implementations** are in application layer
7. **Controllers** use use cases, not repositories directly
8. **DTOs** are in presentation layer
9. **Mappers** use factory methods, not reflection
10. **No circular dependencies** between layers
11. Register the module in `settings.gradle.kts` and as a dependency of `app/build.gradle.kts`

## Summary

- **Separation of concerns**: Each layer has a clear responsibility
- **Testability**: Domain logic can be tested without infrastructure
- **Maintainability**: Changes in infrastructure don't affect domain logic
- **Scalability**: Easy to extract modules to microservices if needed
- **Consistency**: All modules follow the same structure

When in doubt, remember: **Domain is king** - keep it pure, independent, and rich with business logic.
