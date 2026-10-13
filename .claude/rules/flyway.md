---
paths:
  - "modules/*/src/main/resources/db/migration/**"
---
# Flyway rules
- Folder: modules/<module>/src/main/resources/db/migration/ (today only auth-service has migrations).
- File name: V<yyyyMMddHHmm>__<snake_case_desc>.sql, e.g. V202610121100__drop_generated_enum_check_constraints.sql.
  The timestamp must be later than the latest migration in ANY module (versions are global).
- Never edit a committed migration. Fix forward with a new file.
- One concern per migration. Must stay compatible with the currently running app version.
- Add an index for every new foreign key.
- Never drop a column in the same release that stops using it.
- Every table needs a migration. Do not rely on spring.jpa.hibernate.ddl-auto to create tables.
