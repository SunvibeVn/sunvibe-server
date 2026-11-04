---
paths:
  - "modules/*/src/main/java/**/core/infrastructure/persistence/**"
---
# Persistence rules (details: docs/architecture/layer-guide.md#infrastructure-layer-infrastructure)
- entity/: JPA entities (@Entity, @Table), no business logic, separate from domain models.
- repository/: <Entity>JpaRepository extends JpaRepository; works with entities only.
- adapter/: <Entity>RepositoryAdapter implements the domain repository interface, uses the JPA
  repository plus a mapper.
- Mappers: mapper/manual/ in every module except auth-service, which keeps converter/{auto (MapStruct), manual}/.
  Rebuild aggregates with load(...); never set private fields by reflection.
- Every new table or column needs a Flyway migration (see flyway.md).
- No @Transactional here unless there is a specific technical reason; transactions belong to services.
- Queries on growing tables: paginate (Pageable or keyset), never findAll; prevent N+1 on list paths
  (fetch join, @EntityGraph or a projection/DTO query); read-only services may use @Transactional(readOnly = true).
- Index the columns a query filters and sorts by (composite: equality columns first, then the range column), in a
  Flyway migration; check a slow query with EXPLAIN (ANALYZE) on PostgreSQL, not on H2.
- Authorization stays in the use cases, not in the database: no row-level security or auth.uid() patterns.
- Some items adapted from ECC skills/jpa-patterns and postgres-patterns (commit 76aaa77, MIT, Copyright (c) 2026
  Affaan Mustafa). See ../docs/setup/AI_FLOW.md section 12.

## Persistence correctness review additions

Use `review-policy.md` for evidence/severity. The pagination/N+1/index rules above remain the starting point.

- **Mandatory:** paged results need a deterministic total order (unique tie-breaker); a keyset cursor must match
  the filter and full sort tuple/direction, not always just `id > :last`. Check page-size bounds and count/query
  consistency. Do not combine pagination with a to-many fetch that causes in-memory limiting, wrong pages or row
  explosion; use a projection, ID-page then fetch, or another verified strategy. To-one fetches are not all forbidden.
- **Mandatory:** initialize needed lazy data/map results inside the intended persistence boundary. Do not read
  `open-in-view: false` in application.yml as "off": OpenInViewExceptMediaContentConfig keeps it on for every endpoint
  except the two media content streams, and scheduled jobs and workers never have it, so a lazy read that passes an API
  test can fail there. Under it the persistence context outlives each transaction, and a pooled connection stays
  bound until the request ends once acquired. Do not use it to hide per-row queries or extend media transactions.
  Choose fetches per use case; changing every association to EAGER is not an N+1 fix. Do not leak JPA entities through
  the API (existing architecture rule).
- **Mandatory:** for domain-to-entity reconstruction/update paths, preserve identity, version and unchanged fields;
  check new-entity detection for assigned IDs. Spring Data save may persist or merge; when merge is used its returned
  instance is managed, the supplied instance need not be. Do not assume mutations on a detached object are flushed.
  Map every child collection both ways: a mapper that leaves one out makes merge clear it, and orphanRemoval then
  deletes the rows (`../docs/known-issues.md`, 2026-10-21).
- **Mandatory:** where concurrent requests can invalidate a prior check, enforce the planned constraint/lock/version
  or guarded SQL update in the same transaction; check affected-row counts/conflicts. A Java existence check alone
  does not enforce uniqueness, and uniqueness of a normalized value (email case, phone format) needs the constraint on
  the canonical form (SV-091, SV-092). Lock acquisition alone does not prove that an earlier read was fresh: validate
  state under the lock/intended isolation, especially when mixing JDBC/native SQL with an existing persistence context.
- **Mandatory:** a conflict enforced by a constraint must surface where it is translated: `saveAndFlush` in the
  adapter, match the constraint name (ConstraintViolations in survey-service), rethrow a domain exception. A plain
  save fails later, at flush or commit, and answers 500. Do not continue in that transaction: PostgreSQL refuses further
  statements until a rollback or a savepoint rollback, and Spring has already marked it rollback-only.
- **Mandatory:** PostgreSQL runs READ COMMITTED unless the transaction asks otherwise. At REPEATABLE READ an UPDATE,
  DELETE, MERGE or locking read of a row changed since the snapshot fails with SQLSTATE 40001; a plain INSERT or read
  does not. SERIALIZABLE adds dependency failures, and its reads are valid only once it commits, unless it is READ ONLY
  DEFERRABLE. Handle 40001 as the spec says: this repo answers a conflict (ConcurrencyFailureException in the survey
  and acquisition handlers, survey-checklist submission spec 5.4) and has no server retry; a retry, if a spec adds one,
  reruns the whole transaction. Under READ COMMITTED a guarded UPDATE re-checks its WHERE on the latest row version, which a prior
  SELECT does not.
- **Mandatory:** bulk JPQL/native updates can bypass managed-state synchronization and optimistic-version checks.
  Check deliberate flush ordering and refresh/clear/reload strategy before later reads/writes (`@Modifying`
  flushAutomatically and clearAutomatically); clearing must not silently discard pending changes. With @Version
  preserve the loaded version and handle conflicts rather than resetting/retrying stale state blindly. For several
  locks, verify consistent order and the defined timeout/conflict behavior. Do not prescribe @Version for every table
  when another approved mechanism protects the invariant.
- **Mandatory:** when changing cascade/orphanRemoval, prove which owned rows can be deleted and retain both sides
  of bidirectional relationships consistently. For entity equals/hashCode/toString, check transient versus persisted
  identity, proxies and lazy traversal; avoid generated all-field methods that change hash membership or recurse.
- **Advisory:** when adding batch writes, verify actual JDBC batching/flush behavior and persistence-context size;
  saveAll does not by itself prove one SQL statement or bounded memory (`hibernate.jdbc.batch_size` is not set here).
  Retain the existing PostgreSQL verification and migration-review requirements in `testing.md` and `flyway.md`.

References: [Spring Data entity state/save](https://docs.spring.io/spring-data/jpa/reference/jpa/entity-persistence.html);
[Jakarta Persistence](https://jakarta.ee/specifications/persistence/3.2/jakarta-persistence-spec-3.2.html) (entity lifecycle,
versions, bulk updates in 4.11 and relationships); [Hibernate ORM guide](https://docs.hibernate.org/orm/7.4/userguide/html_single/)
(fetching and persistence context); PostgreSQL 17 [transaction isolation](https://www.postgresql.org/docs/17/transaction-iso.html)
and [savepoints](https://www.postgresql.org/docs/17/tutorial-transactions.html). ORM guide examples are background: verify
provider-version-specific behavior against the dependency actually resolved from this repo's Boot BOM before
recommending an API or setting.
