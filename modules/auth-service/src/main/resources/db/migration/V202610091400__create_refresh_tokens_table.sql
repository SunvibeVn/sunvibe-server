-- Refresh tokens of sign-in sessions. One row per issued token; the tokens of
-- one session share family_id. Only the SHA-256 (hex) of the secret is stored.
-- Replaces the old Hibernate-generated "refresh_token" table, which is no
-- longer mapped and can be dropped from existing databases.
CREATE TABLE refresh_tokens (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id VARCHAR(26) NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    session_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    revoke_reason VARCHAR(30),
    user_agent VARCHAR(255),
    ip_address VARCHAR(45),
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);
