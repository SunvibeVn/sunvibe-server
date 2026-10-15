-- Wave W6 (auth-roles brief BR-2, US-2; owner decision W6-2): dual grant for existing accounts.
-- LANDLORD -> OWNER, TENANT -> CUSTOMER, EMPLOYEE -> SALE. The old roles are kept (BR-12); no other mapping
-- (MANAGER, LEADER, USER, ADMIN and SUPER_ADMIN accounts are left alone). Data migration: under D-02 the
-- dev database is disposable. Idempotent: a grant that already exists is skipped.
INSERT INTO user_roles (user_id, role_id)
SELECT ur.user_id, new_role.id
FROM user_roles ur
JOIN roles old_role ON old_role.id = ur.role_id
JOIN (VALUES ('LANDLORD', 'OWNER'), ('TENANT', 'CUSTOMER'), ('EMPLOYEE', 'SALE')) AS mapping (old_name, new_name)
    ON mapping.old_name = old_role.name
JOIN roles new_role ON new_role.name = mapping.new_name
ON CONFLICT (user_id, role_id) DO NOTHING;
