-- auth-email-access spec 8, O-1: what the identifier backfill will meet, before it runs. Read-only, counts only.
-- Runs on the schema before the release too, so it reads no column that release adds.
-- SQL only approximates the policy of Email and PhoneNumber, and a verified row in a collision counts as unverified after
-- the backfill, so these counts are indicative: the rehearsal O-1b decides. Zero real accounts is a result to record.
-- Run it again after the old server stopped, and keep that row_hash: a sign-in moves last_login, so an earlier hash
-- cannot match the verify script.
-- Usage: psql "$DATABASE_URL" -f scripts/ops/auth-identifier-preflight.sql
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
-- D4-3: live, not LOCK or TERMINATED, no verified email, and a reason to keep the way in
in_use AS (
    SELECT l.id, l.id IN (SELECT user_id FROM staff) AS is_staff
    FROM live l
    WHERE l.user_status NOT IN ('LOCK', 'TERMINATED')
      AND l.email_verified_at IS NULL
      AND (l.id IN (SELECT user_id FROM staff)
           OR l.id IN (SELECT user_id FROM owners)
           OR l.last_login > now() - INTERVAL '90 days')
),
email_groups AS (
    SELECT lower(btrim(email) COLLATE "C") AS folded, count(*) AS holders
    FROM live WHERE email IS NOT NULL
    GROUP BY 1
),
phone_groups AS (
    SELECT regexp_replace(username, '^(84|0)', '') AS digits, count(*) AS holders
    FROM live WHERE username ~ '^(0[1-9]|84[1-9])[0-9]*$'
    GROUP BY 1
)
SELECT 'live_accounts', count(*)::text FROM live
UNION ALL SELECT 'with_email', count(*)::text FROM live WHERE email IS NOT NULL
UNION ALL SELECT 'without_email', count(*)::text FROM live WHERE email IS NULL
UNION ALL SELECT 'email_verified_at_set', count(*)::text FROM live WHERE email_verified_at IS NOT NULL
UNION ALL SELECT 'phone_verified_at_set', count(*)::text FROM live WHERE phone_verified_at IS NOT NULL
UNION ALL SELECT 'email_collision_groups', count(*)::text FROM email_groups WHERE holders > 1
UNION ALL SELECT 'email_collision_rows', coalesce(sum(holders), 0)::text FROM email_groups WHERE holders > 1
UNION ALL SELECT 'email_not_folded', count(*)::text FROM live WHERE email <> lower(btrim(email) COLLATE "C")
UNION ALL SELECT 'email_without_one_at', count(*)::text FROM live WHERE email IS NOT NULL AND email !~ '^[^@]+@[^@]+$'
UNION ALL SELECT 'username_outside_old_shape', count(*)::text FROM live WHERE username !~ '^[0-9]{10,12}$'
UNION ALL SELECT 'phone_shape_0', count(*)::text FROM live WHERE username ~ '^0[1-9][0-9]*$'
UNION ALL SELECT 'phone_shape_00', count(*)::text FROM live WHERE username ~ '^00[0-9]*$'
UNION ALL SELECT 'phone_shape_84', count(*)::text FROM live WHERE username ~ '^84[0-9]+$'
UNION ALL SELECT 'phone_shape_other', count(*)::text FROM live WHERE username !~ '^(0|84)[0-9]+$'
UNION ALL SELECT 'phone_digit_collision_groups', count(*)::text FROM phone_groups WHERE holders > 1
UNION ALL SELECT 'phone_digit_collision_rows', coalesce(sum(holders), 0)::text FROM phone_groups WHERE holders > 1
UNION ALL SELECT 'pending', count(*)::text FROM live WHERE user_status = 'PENDING'
UNION ALL SELECT 'in_use_staff', count(*)::text FROM in_use WHERE is_staff
UNION ALL SELECT 'in_use_others', count(*)::text FROM in_use WHERE NOT is_staff
-- Compared with the verify script: the values the backfill must leave alone
UNION ALL SELECT 'row_hash', coalesce(md5(string_agg(md5(format('%L|%L|%L|%L|%L|%L|%L|%L|%L|%L', id, email, username, user_status,
        extract(epoch FROM created_date), extract(epoch FROM last_update), extract(epoch FROM last_login),
        extract(epoch FROM deleted_at), extract(epoch FROM email_verified_at), extract(epoch FROM phone_verified_at))), ''
        ORDER BY id)), '0')
    FROM users;

ROLLBACK;
