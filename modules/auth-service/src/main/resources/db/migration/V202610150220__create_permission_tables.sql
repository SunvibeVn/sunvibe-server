-- Baseline for permission and role_permission (PermissionEntity, @JoinTable ROLE_PERMISSION in
-- UserRoleEntity). Until now only Hibernate ddl-auto created them; this reproduces that DDL (ADR-002, wave W3).
-- IF NOT EXISTS: a database already built by Hibernate keeps its tables unchanged.
-- uk2ojme20jpga3r4r79tdso17gi is the name Hibernate generates for the unique name column; keeping it stops
-- ddl-auto update (still on until W4) from adding a second unique constraint next to a renamed one.
CREATE TABLE IF NOT EXISTS permission (
    id UUID NOT NULL,
    description VARCHAR(255),
    name VARCHAR(255) NOT NULL,
    CONSTRAINT permission_pkey PRIMARY KEY (id),
    CONSTRAINT uk2ojme20jpga3r4r79tdso17gi UNIQUE (name)
);

-- The primary key (role_id, permission_id) also serves as the index for the role_id foreign key.
CREATE TABLE IF NOT EXISTS role_permission (
    role_id UUID NOT NULL,
    permission_id UUID NOT NULL,
    CONSTRAINT role_permission_pkey PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permission_role_id FOREIGN KEY (role_id) REFERENCES roles (id),
    CONSTRAINT fk_role_permission_permission_id FOREIGN KEY (permission_id) REFERENCES permission (id)
);

CREATE INDEX IF NOT EXISTS idx_role_permission_permission_id ON role_permission (permission_id);
