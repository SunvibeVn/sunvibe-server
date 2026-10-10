-- V202610091200__create_roles_table.sql
--
-- First Flyway migration of the auth-service schema.
--
-- DEV DATABASE RESET REQUIRED: Flyway refuses to migrate a non-empty schema
-- that has no flyway_schema_history table. Any dev database previously
-- populated by Hibernate ddl-auto must be reset by dropping and recreating the
-- WHOLE schema before the first boot with these migrations, e.g.
--   DROP SCHEMA public CASCADE; CREATE SCHEMA public;
-- Dropping only users/roles/user_roles is not enough: other ddl-auto tables
-- exist in the same schema, and role_permission references roles.
CREATE TABLE roles (
    id UUID NOT NULL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(500),
    CONSTRAINT uk_roles_name UNIQUE (name)
);
