-- Phone/e-mail verification. A new account is PENDING until its phone is verified.
-- Existing accounts keep their status and start unverified (NULL).
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone_verified_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified_at TIMESTAMP WITH TIME ZONE;

-- One-time codes proving control of a phone number (SMS) or e-mail.
-- Only a salted SHA-256 of the code is stored.
CREATE TABLE verification_codes (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id VARCHAR(26) NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    channel VARCHAR(10) NOT NULL,
    destination VARCHAR(100) NOT NULL,
    code_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    consumed_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_verification_codes_user_channel ON verification_codes (user_id, channel, created_at);

-- Password reset codes can now go by SMS too: e-mail column becomes a generic destination.
ALTER TABLE password_reset_token RENAME COLUMN email TO destination;
ALTER TABLE password_reset_token ADD COLUMN IF NOT EXISTS channel VARCHAR(10) NOT NULL DEFAULT 'EMAIL';
ALTER INDEX IF EXISTS idx_password_reset_token_email RENAME TO idx_password_reset_token_destination;
