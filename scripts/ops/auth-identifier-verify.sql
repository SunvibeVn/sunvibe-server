-- auth-email-access spec 8, O-3: the identifier backfill after it ran. Read-only, counts only; exits non-zero if a check that
-- must be 0 is not, or if the backfill left no trace. Run it before traffic reaches the new server, or a sign-in moves
-- last_login and a new account moves the counts. Compare row_hash with the preflight taken after the old server stopped, the
-- keyed counts with the line auth.backfill.identifier-keys of the log and everything with the rehearsal O-1b. On a failure
-- follow spec 7.5.
-- Usage: psql "$DATABASE_URL" -f scripts/ops/auth-identifier-verify.sql
\set QUIET on
\set ON_ERROR_STOP on
\pset format unaligned
\pset tuples_only on
\pset fieldsep '|'

BEGIN TRANSACTION READ ONLY;

WITH live AS (
    SELECT * FROM users WHERE deleted_at IS NULL
),
staff AS (
    SELECT DISTINCT ur.user_id
    FROM user_roles ur JOIN roles r ON r.id = ur.role_id
    WHERE r.name IN ('SALE', 'SALE_LEADER', 'SALES_MANAGER', 'LISTING_REVIEWER', 'ADMIN', 'SUPER_ADMIN', 'EMPLOYEE', 'MANAGER',
                     'LEADER')
),
owners AS (
    SELECT ur.user_id
    FROM user_roles ur JOIN roles r ON r.id = ur.role_id
    WHERE r.name = 'OWNER'
    UNION
    SELECT user_id FROM property_ownerships
),
-- D4-3, now with the keys: a verified email counts only with its key
in_use AS (
    SELECT l.id, l.id IN (SELECT user_id FROM staff) AS is_staff
    FROM live l
    WHERE l.user_status NOT IN ('LOCK', 'TERMINATED')
      AND (l.email_key IS NULL OR l.email_verified_at IS NULL)
      AND (l.id IN (SELECT user_id FROM staff)
           OR l.id IN (SELECT user_id FROM owners)
           OR l.last_login > now() - INTERVAL '90 days')
)
SELECT 'live_accounts', count(*)::text FROM live
UNION ALL SELECT 'emails_keyed', count(*)::text FROM live WHERE email_key IS NOT NULL
UNION ALL SELECT 'phones_keyed', count(*)::text FROM live WHERE phone_key IS NOT NULL
UNION ALL SELECT 'emails_without_key', count(*)::text FROM live WHERE email IS NOT NULL AND email_key IS NULL
UNION ALL SELECT 'phones_without_key', count(*)::text FROM live WHERE username IS NOT NULL AND phone_key IS NULL
UNION ALL SELECT 'key_on_deleted_row', count(*)::text FROM users
    WHERE deleted_at IS NOT NULL AND (email_key IS NOT NULL OR phone_key IS NOT NULL)
UNION ALL SELECT 'key_differs_from_email', count(*)::text FROM users
    WHERE email_key IS NOT NULL AND email_key IS DISTINCT FROM lower(btrim(email) COLLATE "C")
UNION ALL SELECT 'canonical_shared_with_key', count(*)::text FROM (
        SELECT 1 FROM live WHERE email IS NOT NULL
        GROUP BY lower(btrim(email) COLLATE "C")
        HAVING count(*) > 1 AND count(email_key) > 0) shared
UNION ALL SELECT 'in_use_staff', count(*)::text FROM in_use WHERE is_staff
UNION ALL SELECT 'in_use_others', count(*)::text FROM in_use WHERE NOT is_staff
UNION ALL SELECT 'row_hash', coalesce(md5(string_agg(md5(format('%L|%L|%L|%L|%L|%L|%L|%L|%L|%L', id, email, username, user_status,
        extract(epoch FROM created_date), extract(epoch FROM last_update), extract(epoch FROM last_login),
        extract(epoch FROM deleted_at), extract(epoch FROM email_verified_at), extract(epoch FROM phone_verified_at))), ''
        ORDER BY id)), '0')
    FROM users;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM flyway_schema_history WHERE version = '202611051011' AND success) THEN
        RAISE EXCEPTION 'auth-identifier-verify: the backfill is not recorded as applied';
    END IF;
    IF EXISTS (SELECT 1 FROM users WHERE deleted_at IS NULL AND (email IS NOT NULL OR username IS NOT NULL))
       AND NOT EXISTS (SELECT 1 FROM users WHERE deleted_at IS NULL AND (email_key IS NOT NULL OR phone_key IS NOT NULL)) THEN
        RAISE EXCEPTION 'auth-identifier-verify: rows hold values and no key was written';
    END IF;
    IF EXISTS (SELECT 1 FROM users WHERE deleted_at IS NOT NULL AND (email_key IS NOT NULL OR phone_key IS NOT NULL)) THEN
        RAISE EXCEPTION 'auth-identifier-verify: a deleted row holds a key';
    END IF;
    IF EXISTS (SELECT 1 FROM users WHERE email_key IS NOT NULL
               AND email_key IS DISTINCT FROM lower(btrim(email) COLLATE "C")) THEN
        RAISE EXCEPTION 'auth-identifier-verify: a key differs from its email';
    END IF;
    IF EXISTS (SELECT 1 FROM users WHERE deleted_at IS NULL AND email IS NOT NULL
               GROUP BY lower(btrim(email) COLLATE "C") HAVING count(*) > 1 AND count(email_key) > 0) THEN
        RAISE EXCEPTION 'auth-identifier-verify: a canonical value is shared by rows of which one holds the key';
    END IF;
END
$$;

ROLLBACK;
