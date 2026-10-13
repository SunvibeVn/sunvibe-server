---
paths:
  - "modules/*/src/main/java/**/core/infrastructure/persistence/**"
---
# Persistence rules (details: docs/architecture/layer-guide.md#infrastructure-layer-infrastructure)
- entity/: JPA entities (@Entity, @Table), no business logic, separate from domain models.
- repository/: <Entity>JpaRepository extends JpaRepository; works with entities only.
- adapter/: <Entity>RepositoryAdapter implements the domain repository interface, uses the JPA
  repository plus a mapper.
- Mappers: new modules and property-service use mapper/manual/; existing auth-service uses converter/{auto (MapStruct), manual}/.
  Rebuild aggregates with load(...); never set private fields by reflection.
- Every new table or column needs a Flyway migration (see flyway.md).
- No @Transactional here unless there is a specific technical reason; transactions belong to services.
