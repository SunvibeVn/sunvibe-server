-- Progressive lockout after wrong passwords (see LoginLockoutPolicy).
ALTER TABLE users ADD COLUMN IF NOT EXISTS failed_login_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS locked_until TIMESTAMP WITH TIME ZONE;

-- password_reset_token used to be created only by Hibernate (ddl-auto). Define it
-- here so new databases get it from Flyway; IF NOT EXISTS keeps existing ones intact.
CREATE TABLE IF NOT EXISTS password_reset_token (
    id BIGINT NOT NULL PRIMARY KEY,
    token VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    otp_code VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    is_used BOOLEAN NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE
);

-- Wrong OTP codes per reset token; the token is burned after too many (PasswordResetPolicy).
ALTER TABLE password_reset_token ADD COLUMN IF NOT EXISTS failed_attempts INTEGER NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX IF NOT EXISTS idx_password_reset_token_token ON password_reset_token (token);
CREATE INDEX IF NOT EXISTS idx_password_reset_token_email ON password_reset_token (email);
CREATE INDEX IF NOT EXISTS idx_password_reset_token_user_id ON password_reset_token (user_id);
