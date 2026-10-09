# Architecture Guidelines

## Overview

This project follows a **Modular Monolith** architecture with **DDD + Hexagonal Architecture + CQRS** patterns.

- **Root module**: `app` (main application module)
- **Sub-modules**: Located in `modules/` directory (e.g., `property-service`, `user-service`, etc.)

Each module should follow the same architectural principles described in this document.

---

## Architecture Patterns

### 1. Domain-Driven Design (DDD)

**Key Concepts:**
- **Aggregate Root**: The main entity that controls access to related entities
- **Value Objects**: Immutable objects identified by their attributes, not by ID
- **Domain Repository Interfaces**: Abstract persistence contracts in the domain layer
- **Rich Domain Model**: Business logic resides in domain objects

### 2. Hexagonal Architecture (Ports & Adapters)

**Core Principle:** Dependency inversion - the domain layer is independent of infrastructure.

**Layers (from inner to outer):**
1. **Domain Layer** (Core) - Business logic, domain models, repository interfaces
2. **Application Layer** - Use cases, commands, application services
3. **Infrastructure Layer** - Repository implementations, external services, persistence
4. **Presentation Layer** - Controllers, DTOs, API endpoints

**Dependency Rule:** Outer layers depend on inner layers, never the reverse.

### 3. CQRS (Command Query Responsibility Segregation)

**Separation of concerns:**
- **Commands**: Write operations (create, update, delete)
- **Queries**: Read operations (find, search, list)
- **Use Cases**: Interfaces representing business operations
- **Services**: Implementations of use cases

---

## Module Structure

Each service module should follow this package structure:

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

---

## Layer Responsibilities

### Domain Layer (`domain/`)

**Purpose:** Contains core business logic and domain models. Independent of any external dependencies.

**Components:**

#### Aggregates (`domain/model/aggregate/`)
- **Location**: `domain/model/aggregate/{AggregateName}.java`
- **Purpose**: Aggregate root that controls access to related entities
- **Rules**:
  - Contains business logic (methods like `applyRule()`, `changeStatus()`)
  - Has factory methods: `create()` for new entities, `load()` for reconstruction
  - Validates invariants in constructor
  - No framework annotations (no `@Entity`, `@Service`, etc.)

**Example:**
```java
public class Property {
    private PropertyId id;
    private PropertyCode propertyCode;
    private PropertyStatus status;
    
    // Factory method for creation
    public static Property create(PropertyCode code, String title, ...) {
        PropertyId id = PropertyId.generate();
        return new Property(id, code, title, ...);
    }
    
    // Factory method for reconstruction from database
    public static Property load(PropertyId id, PropertyCode code, String title, 
                                PropertyStatus status, LocalDateTime createdAt, ...) {
        Property property = new Property(id, code, title, ...);
        property.status = status;
        property.createdAt = createdAt;
        return property;
    }
    
    // Business logic
    public void changeStatus(PropertyStatus newStatus, String updatedBy) {
        // validation and logic
    }
}
```

#### Value Objects (`domain/model/vo/`)
- **Location**: `domain/model/vo/{ValueObjectName}.java`
- **Purpose**: Immutable objects identified by their attributes
- **Rules**:
  - Extend `ValueObject<T>` base class
  - Private constructors, public static factory methods (`of()`, `generate()`)
  - Implement `equalsCore()` and `hashCodeCore()` from base class
  - Self-validating (throw exceptions in constructor if invalid)

**Example:**
```java
public class PropertyId extends ValueObject<PropertyId> {
    private String value;
    
    private PropertyId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("PropertyId cannot be null or empty");
        }
        this.value = value;
    }
    
    public static PropertyId generate() {
        return new PropertyId(UUID.randomUUID().toString());
    }
    
    public static PropertyId of(String value) {
        return new PropertyId(value);
    }
    
    @Override
    protected boolean equalsCore(PropertyId other) {
        return Objects.equals(value, other.value);
    }
    
    @Override
    protected int hashCodeCore() {
        return Objects.hash(value);
    }
}
```

#### Domain Repository Interfaces (`domain/repository/`)
- **Location**: `domain/repository/{Entity}DomainRepository.java`
- **Purpose**: Abstract persistence contracts
- **Rules**:
  - Interface only, no implementation
  - Use domain types (e.g., `PropertyId`, not `String`)
  - No Spring Data annotations
  - Implementation lives in infrastructure layer

**Example:**
```java
public interface PropertyDomainRepository {
    void save(Property property);
    Optional<Property> findById(PropertyId id);
    Optional<Property> findByCode(PropertyCode code);
    boolean existsByCode(PropertyCode code);
}
```

---

### Application Layer (`application/`)

**Purpose:** Orchestrates domain objects to fulfill use cases. Contains application-specific logic.

#### Commands (`application/command/`)
- **Location**: `application/command/{Operation}Command.java`
- **Purpose**: CQRS command objects for write operations
- **Rules**:
  - Use Java `record` for immutability
  - Contain validation logic in compact constructor
  - No business logic (only validation)

**Example:**
```java
public record PropertyCreateCommand(
    String propertyCode,
    String title,
    PropertyType propertyType,
    Integer bedrooms,
    // ... other fields
) {
    public PropertyCreateCommand {
        // Validation in compact constructor
        if (propertyCode == null || propertyCode.isBlank()) {
            throw new IllegalArgumentException("PropertyCode is required");
        }
    }
    
    public boolean isValid() {
        return propertyCode != null && !propertyCode.isBlank()
            && title != null && !title.isBlank();
    }
}
```

#### Use Cases (`application/usecase/`)
- **Location**: `application/usecase/{Operation}UseCase.java`
- **Purpose**: Interfaces representing business operations
- **Rules**:
  - Interface only
  - Single method `execute()` or similar
  - Takes Command as parameter, returns domain type or VO

**Example:**
```java
public interface PropertyCreateUseCase {
    PropertyId execute(PropertyCreateCommand command);
}
```

#### Services (`application/service/`)
- **Location**: `application/service/{Operation}Service.java`
- **Purpose**: Implementations of use cases
- **Rules**:
  - Annotated with `@Service`
  - Implements use case interface
  - Uses domain repositories
  - Orchestrates domain objects
  - Contains transaction management (`@Transactional`)

**Example:**
```java
@Service
@Transactional
@RequiredArgsConstructor
public class PropertyCreateService implements PropertyCreateUseCase {
    
    private final PropertyDomainRepository propertyDomainRepository;
    
    @Override
    public PropertyId execute(PropertyCreateCommand command) {
        // Validate command
        if (!command.isValid()) {
            throw new PropertyServiceException("Invalid command");
        }
        
        // Create domain object
        Property property = Property.create(
            PropertyCode.of(command.propertyCode()),
            command.title(),
            // ...
        );
        
        // Save via repository
        propertyDomainRepository.save(property);
        
        return property.getId();
    }
}
```

---

### Infrastructure Layer (`infrastructure/`)

**Purpose:** Implements technical details - persistence, external APIs, caching, etc.

#### Repository Adapters (`infrastructure/persistence/adapter/`)
- **Location**: `infrastructure/persistence/adapter/{Entity}RepositoryAdapter.java`
- **Purpose**: Implement domain repository interfaces
- **Rules**:
  - Annotated with `@Service`
  - Implements domain repository interface
  - Uses JPA repository and mapper
  - Converts between domain models and JPA entities

**Example:**
```java
@Service
@RequiredArgsConstructor
public class PropertyRepositoryAdapter implements PropertyDomainRepository {
    
    private final PropertyJpaRepository jpaRepository;
    private final PropertyEntityMapper mapper;
    
    @Override
    public void save(Property property) {
        var entity = mapper.toEntity(property);
        jpaRepository.save(entity);
    }
    
    @Override
    public Optional<Property> findById(PropertyId id) {
        return jpaRepository.findById(id.getValue())
            .map(mapper::toDomain);
    }
}
```

#### JPA Repositories (`infrastructure/persistence/repository/`)
- **Location**: `infrastructure/persistence/repository/{Entity}JpaRepository.java`
- **Purpose**: Low-level Spring Data JPA repositories
- **Rules**:
  - Extends `JpaRepository<Entity, ID>`
  - Annotated with `@Repository`
  - Works with JPA entities only
  - Contains custom queries if needed

**Example:**
```java
@Repository
public interface PropertyJpaRepository extends JpaRepository<PropertyEntity, String> {
    Optional<PropertyEntity> findByPropertyCode(String propertyCode);
    List<PropertyEntity> findByOwnerId(String ownerId);
}
```

#### JPA Entities (`infrastructure/persistence/entity/`)
- **Location**: `infrastructure/persistence/entity/{Entity}Entity.java`
- **Purpose**: Database mapping entities
- **Rules**:
  - Annotated with `@Entity`, `@Table`
  - Use JPA annotations (`@Column`, `@OneToMany`, etc.)
  - No business logic
  - Separate from domain models

**Example:**
```java
@Entity
@Table(name = "properties")
@Getter
@Setter
@NoArgsConstructor
public class PropertyEntity {
    @Id
    private String id;
    
    @Column(name = "property_code", unique = true)
    private String propertyCode;
    
    @Column(name = "title")
    private String title;
    
    @OneToMany(mappedBy = "property")
    private List<PropertyImageEntity> images;
}
```

#### Mappers (`infrastructure/persistence/mapper/`)
- **Location**: `infrastructure/persistence/mapper/manual/{Entity}EntityMapper.java`
- **Purpose**: Convert between domain models and JPA entities
- **Rules**:
  - Annotated with `@Component`
  - Two main methods: `toDomain()` and `toEntity()`
  - Use domain factory methods for reconstruction
  - **IMPORTANT**: Use `load()` factory method instead of reflection

**Example:**
```java
@Component
@RequiredArgsConstructor
public class PropertyEntityMapper {
    
    public Property toDomain(PropertyEntity entity) {
        // Use load() factory method for reconstruction
        return Property.load(
            PropertyId.of(entity.getId()),
            PropertyCode.of(entity.getPropertyCode()),
            entity.getTitle(),
            // ... other fields
            PropertyStatus.valueOf(entity.getStatus()),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getCreatedBy(),
            entity.getUpdatedBy()
        );
    }
    
    public PropertyEntity toEntity(Property domain) {
        return PropertyEntity.builder()
            .id(domain.getId().getValue())
            .propertyCode(domain.getPropertyCode().getValue())
            .title(domain.getTitle())
            // ... other fields
            .build();
    }
}
```

**⚠️ IMPORTANT:** Never use reflection to set private fields in mappers. Always use the `load()` factory method provided in the aggregate root.

---

### Presentation Layer (`presentation/`)

**Purpose:** Handles HTTP requests/responses, API contracts.

#### Controllers (`presentation/controller/`)
- **Location**: `presentation/controller/{Entity}Controller.java`
- **Purpose**: REST API endpoints
- **Rules**:
  - Annotated with `@RestController`, `@RequestMapping`
  - Injects use cases, not repositories or services directly
  - Uses converters to map DTOs to commands
  - Returns response DTOs

**Example:**
```java
@RestController
@RequestMapping("/api/v1/properties")
@RequiredArgsConstructor
public class PropertyController {
    
    private final PropertyCreateUseCase propertyCreateUseCase;
    private final PropertyCreateRequestMapper requestMapper;
    private final PropertyCreateResponseMapper responseMapper;
    
    @PostMapping
    public ResponseEntity<PropertyCreateResponse> createProperty(
            @Valid @RequestBody PropertyCreateRequest request) {
        
        // Convert request to command
        PropertyCreateCommand command = requestMapper.toCommand(request);
        
        // Execute use case
        PropertyId propertyId = propertyCreateUseCase.execute(command);
        
        // Build response
        PropertyCreateResponse response = responseMapper.toResponse(propertyId);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
```

#### DTOs (`presentation/dto/`)
- **Location**: `presentation/dto/request/{Operation}Request.java` and `presentation/dto/response/{Operation}Response.java`
- **Purpose**: Data transfer objects for API contracts
- **Rules**:
  - Use Java `record` for immutability
  - Use Jakarta validation annotations (`@Valid`, `@NotNull`, etc.)
  - Separate from domain models and commands

**Example:**
```java
public record PropertyCreateRequest(
    @NotBlank String propertyCode,
    @NotBlank String title,
    @NotNull PropertyType propertyType,
    @Positive Integer bedrooms,
    // ... other fields
) {}
```

#### Converters (`presentation/converter/`)
- **Location**: `presentation/converter/{Operation}RequestMapper.java` and `presentation/converter/{Operation}ResponseMapper.java`
- **Purpose**: Convert between DTOs and commands/domain objects
- **Rules**:
  - Annotated with `@Component`
  - Methods: `toCommand()`, `toResponse()`, etc.

---

## Dependency Rules

### Allowed Dependencies

```
┌─────────────────────────────────────────┐
│         Presentation Layer              │
│  (Controllers, DTOs, Converters)        │
└──────────────────┬──────────────────────┘
                   │ depends on
┌──────────────────▼──────────────────────┐
│        Application Layer                │
│  (Use Cases, Commands, Services)        │
└──────────────────┬──────────────────────┘
                   │ depends on
┌──────────────────▼──────────────────────┐
│           Domain Layer                   │
│  (Aggregates, VOs, Repository Interfaces)│
└──────────────────────────────────────────┘
                   │ depends on
┌──────────────────▼──────────────────────┐
│       Infrastructure Layer               │
│  (Repository Adapters, JPA, Entities)   │
└──────────────────────────────────────────┘
```

**Key Rules:**
- Domain layer **MUST NOT** depend on any other layer
- Application layer depends only on Domain layer
- Infrastructure layer implements Domain layer interfaces
- Presentation layer depends on Application layer

### Forbidden Dependencies

❌ Domain layer importing from:
- `infrastructure.*`
- `presentation.*`
- `application.*` (except shared types if absolutely necessary)

❌ Application layer importing from:
- `infrastructure.*`
- `presentation.*`

❌ Presentation layer importing from:
- `infrastructure.*` (use application layer instead)
- Domain layer directly (use application layer/use cases)

---

## Best Practices

### 1. Always Use Factory Methods

**✅ Correct:**
```java
PropertyId id = PropertyId.generate();
PropertyId id = PropertyId.of("existing-id");
Property property = Property.create(code, title, ...);
Property property = Property.load(id, code, title, status, createdAt, ...);
```

**❌ Incorrect:**
```java
PropertyId id = new PropertyId("value");  // Constructor should be private
```

### 2. Never Use Reflection for Field Access

**✅ Correct:**
```java
// In mapper, use load() factory method
Property domain = Property.load(
    id, code, title, ...,
    status, createdAt, updatedAt, createdBy, updatedBy
);
```

**❌ Incorrect:**
```java
// Never do this
Field field = Property.class.getDeclaredField("status");
field.setAccessible(true);
field.set(domain, status);
```

### 3. Keep Domain Layer Pure

**✅ Correct:**
- Domain models contain only business logic
- No framework annotations in domain layer
- No dependencies on Spring, JPA, etc.

**❌ Incorrect:**
```java
@Entity  // ❌ Don't put this in domain layer
public class Property {
    @Autowired  // ❌ No Spring annotations
    private SomeService service;
}
```

### 4. Repository Pattern

**✅ Correct:**
```java
// Domain layer - interface only
public interface PropertyDomainRepository {
    Optional<Property> findById(PropertyId id);
}

// Infrastructure layer - implementation
@Service
public class PropertyRepositoryAdapter implements PropertyDomainRepository {
    // Implementation details
}
```

**❌ Incorrect:**
- Putting repository implementation in domain layer
- Domain repository extending `JpaRepository`

### 5. Transaction Management

**✅ Correct:**
- Place `@Transactional` on application service methods
- Domain layer has no transaction annotations

**❌ Incorrect:**
- `@Transactional` in domain layer
- `@Transactional` in infrastructure layer (unless for specific technical reasons)

### 6. Exception Handling

- **Domain exceptions**: `domain/exception/` - Business rule violations
- **Application exceptions**: `application/exception/` - Application-specific errors
- **Presentation exceptions**: `presentation/exception/` - HTTP error responses via `@ExceptionHandler`

---

## Module Creation Checklist

When creating a new module, ensure:

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

---

## Example Flow: Create Entity

```
1. Controller receives HTTP request
   ↓
2. RequestMapper converts Request DTO → Command
   ↓
3. Service (UseCase implementation) executes Command
   ↓
4. Service creates Domain object via factory method: Aggregate.create()
   ↓
5. Service calls Repository.save(domain)
   ↓
6. RepositoryAdapter converts Domain → Entity via mapper.toEntity()
   ↓
7. JpaRepository saves Entity to database
   ↓
8. ResponseMapper converts result → Response DTO
   ↓
   
9. Controller returns HTTP response
```

---

## Summary

This architecture ensures:

- **Separation of concerns**: Each layer has a clear responsibility
- **Testability**: Domain logic can be tested without infrastructure
- **Maintainability**: Changes in infrastructure don't affect domain logic
- **Scalability**: Easy to extract modules to microservices if needed
- **Consistency**: All modules follow the same structure

When in doubt, remember: **Domain is king** - keep it pure, independent, and rich with business logic.
