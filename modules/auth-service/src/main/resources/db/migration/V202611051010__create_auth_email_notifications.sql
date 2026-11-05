-- Durable queue of the notice sent to a replaced address (auth-email-access spec 5.2 item 5, 5.6, A-16, BR-13). A row is
-- deleted when sent or given up, so the old address is not kept.
-- A new table, but its foreign key takes a SHARE ROW EXCLUSIVE lock on users for a moment, hence the timeout.
SET LOCAL lock_timeout = '5s';

CREATE TABLE auth_email_notifications (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id VARCHAR(26) NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind VARCHAR(30) NOT NULL,
    destination VARCHAR(254) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_auth_email_notifications_user_id ON auth_email_notifications (user_id);
CREATE INDEX idx_auth_email_notifications_next_attempt_at ON auth_email_notifications (next_attempt_at);
