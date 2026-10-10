-- Append-only security audit log of the auth module (see AuditEventType).
-- Rows are purged after app.security.audit.retention (default 180 days).
CREATE TABLE auth_audit_events (
    id BIGINT NOT NULL PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    user_id VARCHAR(26),
    subject VARCHAR(100),
    details VARCHAR(255),
    ip_address VARCHAR(45),
    user_agent VARCHAR(255),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_auth_audit_events_user_id ON auth_audit_events (user_id, occurred_at);
CREATE INDEX idx_auth_audit_events_type ON auth_audit_events (event_type, occurred_at);
CREATE INDEX idx_auth_audit_events_ip ON auth_audit_events (ip_address, occurred_at);
CREATE INDEX idx_auth_audit_events_occurred_at ON auth_audit_events (occurred_at);
