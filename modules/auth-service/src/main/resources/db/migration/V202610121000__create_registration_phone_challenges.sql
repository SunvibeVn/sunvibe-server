-- "Verify first, register later": one-time codes proving control of a phone number that has no
-- account yet, and the single-use registration token issued once the code is confirmed.
-- Only hashes of the code and of the token are stored, never the values themselves.
CREATE TABLE registration_phone_challenges (
    id BIGINT NOT NULL PRIMARY KEY,
    phone VARCHAR(20) NOT NULL,
    code_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    consumed_at TIMESTAMP WITH TIME ZONE,
    verified_at TIMESTAMP WITH TIME ZONE,
    token_hash VARCHAR(64),
    token_expires_at TIMESTAMP WITH TIME ZONE,
    registered_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_registration_phone_challenges_phone ON registration_phone_challenges (phone, created_at);
CREATE UNIQUE INDEX uq_registration_phone_challenges_token ON registration_phone_challenges (token_hash)
    WHERE token_hash IS NOT NULL;
