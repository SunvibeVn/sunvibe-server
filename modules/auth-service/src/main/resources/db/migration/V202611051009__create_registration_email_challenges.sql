-- Proof of a mailbox before an account exists (auth-email-access spec 5.2 item 4, 4.7, A-9): the columns of
-- registration_phone_challenges with email_key for phone. Only hashes of the code and of the token are stored.
-- A new table: it locks nothing that exists.
CREATE TABLE registration_email_challenges (
    id BIGINT NOT NULL PRIMARY KEY,
    email_key VARCHAR(254) NOT NULL,
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
CREATE INDEX idx_registration_email_challenges_email_key ON registration_email_challenges (email_key, created_at);
CREATE INDEX idx_registration_email_challenges_expires_at ON registration_email_challenges (expires_at);
CREATE UNIQUE INDEX uq_registration_email_challenges_token ON registration_email_challenges (token_hash)
    WHERE token_hash IS NOT NULL;
-- The backstop of the send lock (spec 4.7): one unused code per address
CREATE UNIQUE INDEX uq_registration_email_challenges_active ON registration_email_challenges (email_key)
    WHERE consumed_at IS NULL;
