-- V202610091200__create_roles_table.sql
CREATE TABLE roles (
    id UUID NOT NULL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(500),
    CONSTRAINT uk_roles_name UNIQUE (name)
);
