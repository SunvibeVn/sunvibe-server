---
paths:
  - "modules/*/src/main/resources/db/migration/**"
---
# Flyway rules
- Folder: modules/<module>/src/main/resources/db/migration/ of the owning module (auth-service, property-service,
  acquisition-service and survey-service have migrations today).
- File name: V<yyyyMMddHHmm>__<snake_case_desc>.sql, e.g. V202610121100__drop_generated_enum_check_constraints.sql.
  The timestamp must be later than the latest migration in ANY module (versions are global).
- Never edit a committed versioned migration. Fix forward with a new file. The exception is a repeatable
  R__<desc>.sql (master data only, e.g. R__property_master_data.sql): it is edited in place and runs after the versioned
  migrations whenever it changes, so every statement must be safe to repeat (ON CONFLICT ...).
- One concern per migration. Must stay compatible with the currently running app version. Rebuild migrations are
  expand-only; dropping a table or column, a rename or a type narrowing needs explicit owner approval.
- Add an index for every new foreign key. No foreign key across modules: a column that names another module's row is
  a plain id.
- Never drop a column in the same release that stops using it.
- Every table needs a migration. Do not rely on spring.jpa.hibernate.ddl-auto to create tables.
- Proof on PostgreSQL: FlywayPostgresMigrationTest (part of ./gradlew build, needs Docker) applies every migration
  and lets Hibernate validate the mapping. H2 tests with Flyway disabled prove nothing about a migration. That test
  runs on empty tables: it proves neither the run time nor the lock time on populated data (SV-090).
- CI job migration-history (scripts/ci/check-migration-history.sh) fails a PR that modifies, renames or deletes a
  versioned migration that exists on its base branch. Locally: bash scripts/ci/check-migration-history.sh origin/main
  --include-worktree.

## Locks and populated tables

Use `review-policy.md` for evidence/severity. Steps to create a migration: the flyway-migration skill. PostgreSQL
behavior below is from the version 17 docs (tests run postgres:17.6); recheck it if the server version changes.

- **Mandatory:** Flyway runs each file in one transaction, so every lock is held until the file ends. A migration on
  an existing table starts with `SET LOCAL lock_timeout = '5s'`, so a blocked lock fails the start-up (the runbook
  retries) instead of stalling the queries behind it. Keep a strong lock away from slow work in the same file.
- **Mandatory:** on a populated table, state the cost of each step:
  - `ADD COLUMN` with no default or a non-volatile one changes only metadata. A volatile default rewrites the table
    and its indexes.
  - A column type change rewrites them too, unless the old type is binary coercible to the new one; even then indexes
    may rebuild. Any type change drops the column's statistics (run ANALYZE), and a running old instance can fail with
    `cached plan must not change result type` (not reproduced), so the plan states how old instances are handled;
    stopping them was the owner's decision for the W18 deploy (SV-090), not a default.
  - `SET NOT NULL` scans the table unless a valid CHECK constraint proves that no NULL exists.
  - A new CHECK or FOREIGN KEY on existing rows: `ADD CONSTRAINT ... NOT VALID`, then `VALIDATE CONSTRAINT` in the next
    file (SHARE UPDATE EXCLUSIVE, so reads and writes go on). `ADD FOREIGN KEY` also locks the referenced table.
  - A plain `CREATE INDEX` blocks writes for the whole build: alone in its file with the table's size as the reason
    the build is short (V202611041534__index_survey_checklists_review.sql), or CONCURRENTLY. `IF NOT EXISTS` keeps
    any index of that name without checking that its definition matches.
- **Mandatory:** `CREATE INDEX CONCURRENTLY` (none yet) cannot run in a transaction: own file with a `<file>.sql.conf`
  holding `executeInTransaction=false`, no `IF NOT EXISTS`, `SET lock_timeout` and `RESET` instead of `SET LOCAL`, and a
  `pg_index.indisvalid` check. `spring.flyway.postgresql.transactional-lock: false` is server-wide: record it as such.
  A failed build leaves an INVALID index, so add its recovery to scripts/ops/README.md, which says none can occur today.

References: PostgreSQL 17 [ALTER TABLE](https://www.postgresql.org/docs/17/sql-altertable.html),
[CREATE INDEX](https://www.postgresql.org/docs/17/sql-createindex.html); Flyway
[migrations](https://documentation.red-gate.com/fd/migrations-271585107.html),
[transaction handling](https://documentation.red-gate.com/fd/migration-transaction-handling-273973399.html),
[script configuration](https://documentation.red-gate.com/fd/script-configuration-277578847.html),
[PostgreSQL transactional lock](https://documentation.red-gate.com/fd/flyway-postgresql-transactional-lock-setting-277579114.html).
