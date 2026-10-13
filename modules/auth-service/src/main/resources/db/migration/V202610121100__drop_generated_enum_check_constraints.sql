-- Hibernate schema generation (ddl-auto create/update, used on some local databases) adds a CHECK
-- listing the enum values of every @Enumerated(EnumType.STRING) column. No migration defines them,
-- and Hibernate never refreshes them, so each new enum value (e.g. new AuditEventType entries) is
-- rejected by Postgres. Enum values are validated by the application; drop the stale constraints.
-- IF EXISTS: a database built only by Flyway (production) has none of them, so this is a no-op there.
ALTER TABLE auth_audit_events DROP CONSTRAINT IF EXISTS auth_audit_events_event_type_check;
ALTER TABLE password_reset_token DROP CONSTRAINT IF EXISTS password_reset_token_channel_check;
ALTER TABLE refresh_tokens DROP CONSTRAINT IF EXISTS refresh_tokens_revoke_reason_check;
ALTER TABLE refresh_tokens DROP CONSTRAINT IF EXISTS refresh_tokens_status_check;
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_kyc_status_check;
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_user_status_check;
ALTER TABLE verification_codes DROP CONSTRAINT IF EXISTS verification_codes_channel_check;
