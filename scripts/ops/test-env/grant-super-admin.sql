-- TEST ENVIRONMENT ONLY (W35a plan OD-3, owner decision 2026-11-10): grants SUPER_ADMIN to the one active account with
-- this email, so that the owner can grant ADMIN through the API there (SV-142: the application has no first-admin path).
-- Never run it on a production database; SV-142's production runbook is hardening F9.
-- scripts/ops/README.md "Test environment on one VM" gives the command:
--   docker compose ... exec -T postgres psql -v ON_ERROR_STOP=1 -U sunvibe -d sunvibe_core -v email=<address> \
--     < scripts/ops/test-env/grant-super-admin.sql
-- The email is matched as the application stores its key: ASCII lower case, spaces trimmed at both ends
-- (chk_users_email_key_matches_email). The account must be ACTIVE and not deleted, and exactly one may match; otherwise
-- nothing is granted, a notice says why and psql exits non-zero. A second run changes nothing (INSERT 0 0).
-- The grant writes no audit event, unlike a grant through the API. The account's roles are printed after.
\set ON_ERROR_STOP on
\if :{?email}
\else
  -- psql's \quit sets no exit code; a failed statement under ON_ERROR_STOP does (3)
  DO $$ BEGIN RAISE EXCEPTION 'grant-super-admin: set the email with psql -v email=<address>'; END $$;
\endif

SELECT count(*) = 1 AS one_match, count(*) AS matches
FROM users
WHERE email_key = lower(btrim(:'email') COLLATE "C")
  AND user_status = 'ACTIVE'
  AND deleted_at IS NULL
\gset

\if :one_match
  INSERT INTO user_roles (user_id, role_id)
  SELECT u.id, r.id
  FROM users u
  JOIN roles r ON r.name = 'SUPER_ADMIN'
  WHERE u.email_key = lower(btrim(:'email') COLLATE "C")
    AND u.user_status = 'ACTIVE'
    AND u.deleted_at IS NULL
  ON CONFLICT (user_id, role_id) DO NOTHING;

  SELECT u.id AS user_id, string_agg(r.name, ', ' ORDER BY r.name) AS roles
  FROM users u
  JOIN user_roles ur ON ur.user_id = u.id
  JOIN roles r ON r.id = ur.role_id
  WHERE u.email_key = lower(btrim(:'email') COLLATE "C")
    AND u.user_status = 'ACTIVE'
    AND u.deleted_at IS NULL
  GROUP BY u.id;
\else
  \warn 'Active accounts with this email:' :matches
  DO $$ BEGIN RAISE EXCEPTION 'grant-super-admin refused: no single active account has this email; nothing was granted'; END $$;
\endif
