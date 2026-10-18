-- E2E seed accounts for a LOCAL development database (auth-browser-session spec §3.13).
-- Not a Flyway migration: never copy it under db/migration and never run it against a shared or production database.
--
-- Creates or resets four ACTIVE accounts with a verified phone:
--   01JE2EACCT0000000000000001  0900000101  "E2E Customer"      roles TENANT, CUSTOMER
--   01JE2EACCT0000000000000002  0900000102  "E2E Sale"          role  SALE
--   01JE2EACCT0000000000000003  0900000103  "E2E Trưởng nhóm"   role  SALE_LEADER
--   01JE2EACCT0000000000000004  0900000104  "E2E Admin"         role  ADMIN
-- Idempotent: a re-run resets the passwords, the lockout counters and the roles.
-- The passwords come only from psql variables, filled from the env vars the UI E2E tests read (at least 8 characters
-- each); the script hashes them first, so the INSERT carries only the bcrypt hashes (pgcrypto, $2a$, cost 10), which
-- Spring's BCrypt encoder reads.
-- Fails before any change when one of the four phones already belongs to another account, or when the server is
-- not on this machine (psql host other than localhost, 127.0.0.1, ::1 or a Unix socket), whatever -v target says.
--
-- Run (bash; DATABASE_URL is a libpq URL such as postgresql://sunvibe@localhost:5432/sunvibe, not the JDBC DB_URL):
--   psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -v target=local \
--     -v customer_password="$E2E_CUSTOMER_PASSWORD" -v sale_password="$E2E_SALE_PASSWORD" \
--     -v leader_password="$E2E_LEADER_PASSWORD" -v admin_password="$E2E_ADMIN_PASSWORD" \
--     -f scripts/dev/seed-e2e-accounts.sql
-- Then run the UI E2E tests (npm run e2e in sunvibe-ui) with the same four env vars set. The lead fixtures of the
-- owner lead queue (scripts/dev/seed-e2e-owner-leads.sql) need these accounts first.

\set ON_ERROR_STOP on

-- The file is UTF-8 (names with diacritics); psql on Windows would use the console code page.
\encoding UTF8

-- Guard: refuse to run, before any change, unless -v target=local is given.
\if :{?target}
\else
\set target ''
\endif
SELECT :'target' = 'local' AS seed_target_is_local \gset
\if :seed_target_is_local
\else
DO $$
BEGIN
    RAISE EXCEPTION 'seed-e2e-accounts.sql refused: it runs only against a local database, pass -v target=local';
END
$$;
\endif

-- Guard: refuse to run, before any statement that carries a password or writes, against a server that is not on
-- this machine (a DATABASE_URL pointing at a shared database). HOST is psql's own variable: the host name or address
-- connected to, or the socket directory (starts with /) for a Unix socket.
SELECT :'HOST' IN ('localhost', '127.0.0.1', '::1') OR :'HOST' LIKE '/%' AS seed_host_is_local \gset
\if :seed_host_is_local
\else
DO $$
BEGIN
    RAISE EXCEPTION 'seed-e2e-accounts.sql refused: the server is not on this machine';
END
$$;
\endif

-- All four passwords must be given and non-empty; the refusal names the first one that is missing.
\if :{?customer_password}
\else
\set customer_password ''
\endif
SELECT :'customer_password' <> '' AS seed_customer_password_given \gset
\if :seed_customer_password_given
\else
DO $$
BEGIN
    RAISE EXCEPTION 'seed-e2e-accounts.sql refused: pass -v customer_password=...';
END
$$;
\endif
\if :{?sale_password}
\else
\set sale_password ''
\endif
SELECT :'sale_password' <> '' AS seed_sale_password_given \gset
\if :seed_sale_password_given
\else
DO $$
BEGIN
    RAISE EXCEPTION 'seed-e2e-accounts.sql refused: pass -v sale_password=...';
END
$$;
\endif
\if :{?leader_password}
\else
\set leader_password ''
\endif
SELECT :'leader_password' <> '' AS seed_leader_password_given \gset
\if :seed_leader_password_given
\else
DO $$
BEGIN
    RAISE EXCEPTION 'seed-e2e-accounts.sql refused: pass -v leader_password=...';
END
$$;
\endif
\if :{?admin_password}
\else
\set admin_password ''
\endif
SELECT :'admin_password' <> '' AS seed_admin_password_given \gset
\if :seed_admin_password_given
\else
DO $$
BEGIN
    RAISE EXCEPTION 'seed-e2e-accounts.sql refused: pass -v admin_password=...';
END
$$;
\endif

-- Each password must have at least 8 characters.
SELECT length(:'customer_password') >= 8 AND length(:'sale_password') >= 8 AND length(:'leader_password') >= 8
       AND length(:'admin_password') >= 8 AS seed_passwords_long_enough \gset
\if :seed_passwords_long_enough
\else
DO $$
BEGIN
    RAISE EXCEPTION 'seed-e2e-accounts.sql refused: each password must have at least 8 characters';
END
$$;
\endif

-- The seed phone numbers must not belong to another (not deleted) account; checked before any change.
SELECT count(*) = 0 AS seed_phones_free
FROM users
WHERE username IN ('0900000101', '0900000102')
  AND deleted_at IS NULL
  AND id NOT IN ('01JE2EACCT0000000000000001', '01JE2EACCT0000000000000002') \gset
\if :seed_phones_free
\else
DO $$
BEGIN
    RAISE EXCEPTION 'seed-e2e-accounts.sql refused: 0900000101 or 0900000102 already belongs to another account';
END
$$;
\endif

SELECT count(*) = 0 AS seed_staff_phones_free
FROM users
WHERE username IN ('0900000103', '0900000104')
  AND deleted_at IS NULL
  AND id NOT IN ('01JE2EACCT0000000000000003', '01JE2EACCT0000000000000004') \gset
\if :seed_staff_phones_free
\else
DO $$
BEGIN
    RAISE EXCEPTION 'seed-e2e-accounts.sql refused: 0900000103 or 0900000104 already belongs to another account';
END
$$;
\endif

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Hash first, so the INSERT below carries only the hashes, never a plaintext password.
SELECT crypt(:'customer_password', gen_salt('bf', 10)) AS customer_hash,
       crypt(:'sale_password', gen_salt('bf', 10)) AS sale_hash,
       crypt(:'leader_password', gen_salt('bf', 10)) AS leader_hash,
       crypt(:'admin_password', gen_salt('bf', 10)) AS admin_hash \gset

INSERT INTO users (id, full_name, username, password, user_status, phone_verified_at, failed_login_attempts,
                   locked_until, created_date, last_update, deleted_at)
VALUES ('01JE2EACCT0000000000000001', 'E2E Customer', '0900000101',
        :'customer_hash', 'ACTIVE', now(), 0, NULL, now(), now(), NULL),
       ('01JE2EACCT0000000000000002', 'E2E Sale', '0900000102',
        :'sale_hash', 'ACTIVE', now(), 0, NULL, now(), now(), NULL),
       ('01JE2EACCT0000000000000003', 'E2E Trưởng nhóm', '0900000103',
        :'leader_hash', 'ACTIVE', now(), 0, NULL, now(), now(), NULL),
       ('01JE2EACCT0000000000000004', 'E2E Admin', '0900000104',
        :'admin_hash', 'ACTIVE', now(), 0, NULL, now(), now(), NULL)
ON CONFLICT (id) DO UPDATE SET
    full_name = EXCLUDED.full_name,
    username = EXCLUDED.username,
    password = EXCLUDED.password,
    user_status = EXCLUDED.user_status,
    phone_verified_at = COALESCE(users.phone_verified_at, EXCLUDED.phone_verified_at),
    failed_login_attempts = 0,
    locked_until = NULL,
    last_update = EXCLUDED.last_update,
    deleted_at = NULL;

-- Roles are replaced, not merged, so a role granted by hand since the last run is removed.
DELETE FROM user_roles WHERE user_id IN ('01JE2EACCT0000000000000001', '01JE2EACCT0000000000000002',
                                         '01JE2EACCT0000000000000003', '01JE2EACCT0000000000000004');

INSERT INTO user_roles (user_id, role_id)
SELECT seed.user_id, roles.id
FROM (VALUES ('01JE2EACCT0000000000000001', 'TENANT'),
             ('01JE2EACCT0000000000000001', 'CUSTOMER'),
             ('01JE2EACCT0000000000000002', 'SALE'),
             ('01JE2EACCT0000000000000003', 'SALE_LEADER'),
             ('01JE2EACCT0000000000000004', 'ADMIN')) AS seed (user_id, role_name)
JOIN roles ON roles.name = seed.role_name;

-- Every role must exist (the Flyway role seed must have run); otherwise roll everything back.
DO $$
BEGIN
    IF (SELECT count(*) FROM user_roles
        WHERE user_id IN ('01JE2EACCT0000000000000001', '01JE2EACCT0000000000000002',
                          '01JE2EACCT0000000000000003', '01JE2EACCT0000000000000004')) <> 5 THEN
        RAISE EXCEPTION 'seed-e2e-accounts.sql: role TENANT, CUSTOMER, SALE, SALE_LEADER or ADMIN is missing; run the Flyway migrations first';
    END IF;
END
$$;

COMMIT;
