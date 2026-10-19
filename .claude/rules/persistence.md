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
- Queries on growing tables: paginate (Pageable or keyset `id > :last`), never findAll; prevent N+1 on list paths
  (fetch join, @EntityGraph or a projection/DTO query); read-only services may use @Transactional(readOnly = true).
- Index the columns a query filters and sorts by (composite: equality columns first, then the range column), in a
  Flyway migration; check a slow query with EXPLAIN (ANALYZE) on PostgreSQL, not on H2.
- Authorization stays in the use cases: no Supabase-style row-level security or auth.uid() patterns.
- Some items adapted from ECC skills/jpa-patterns and postgres-patterns (commit 76aaa77, MIT, Copyright (c) 2026
  Affaan Mustafa). See ../docs/setup/AI_FLOW.md section 12.
