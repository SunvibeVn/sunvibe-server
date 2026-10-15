-- Wave W6 (auth-roles brief BR-1, owner decision W6-1): every role lives in the roles table, under Flyway.
-- Until now RoleDataInitializer inserted the 8 old roles at start-up, and only into an empty table, so an
-- existing database would never get a new role. Idempotent: a role that already exists (same name) is kept
-- with its id, so rows inserted by RoleDataInitializer and every user_roles row pointing at them stay valid.
INSERT INTO roles (id, name, description)
SELECT gen_random_uuid(), r.name, r.name || ' role'
FROM (VALUES
    ('SUPER_ADMIN'), ('ADMIN'), ('MANAGER'), ('LANDLORD'), ('TENANT'), ('LEADER'), ('EMPLOYEE'), ('USER'),
    ('OWNER'), ('CUSTOMER'), ('SALE'), ('SALE_LEADER'), ('SALES_MANAGER'), ('LISTING_REVIEWER')
) AS r (name)
ON CONFLICT (name) DO NOTHING;
