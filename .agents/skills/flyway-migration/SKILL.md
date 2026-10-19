---
name: flyway-migration
description: Create a new Flyway migration safely. Use whenever a database schema or data change is needed.
---
<!-- Generated from .claude/skills/flyway-migration/SKILL.md by scripts/ai-config (sync); edit the source, then run sync. -->
1. Find the latest migration across ALL modules (`modules/*/src/main/resources/db/migration/`;
   versions are global) and pick the next version with the pattern V<yyyyMMddHHmm>__<snake_case_desc>.sql,
   using the current time and a value later than the latest one.
2. Put the file in the owning module's db/migration folder. Write one concern per file.
   Never edit an existing committed migration.
3. Check backward compatibility with the running app: add columns as nullable or with defaults;
   no drop or rename in the same release that stops using the column.
4. Add indexes for new foreign keys and for columns used in search filters.
5. Run the app or the integration tests against PostgreSQL to apply it, and report the result
   (H2 tests with spring.flyway.enabled=false do NOT prove the migration works). ./gradlew build runs
   FlywayPostgresMigrationTest on Testcontainers (Docker must run).
6. Every migration and every backfill: ask the migration-specialist subagent (hub agent) to review it with its file
   paths, the plan task and the data assumptions (populated tables need the most care); fix Blocker and Major
   findings. It checks the design; this skill lists the steps.
7. Run bash scripts/ci/check-migration-history.sh origin/main --include-worktree (CI runs it on every PR).
8. Ask the owner to read the migration line by line before it leaves the machine: before committing by hand, or, when
   commits are needed earlier (a flow run verifies committed work), before the push, with the migration files listed
   in the report. A populated-data migration stops before the push (G2 D-27).
