-- History of a lead and who looked at it (acquisition module brief BR-4, acquisition-owner-lead-queue BR-7). Both
-- tables are insert-only: the code never updates or deletes a row, so there is no version or updated_at.
-- Expand only: new tables, no existing table, column or row changes; no FK to other modules. actor_user_id,
-- user_id and the assignee columns have no foreign key because auth-service owns users.
-- id is a Snowflake id (BIGINT). actor_user_id NULL means the system or the anonymous owner who sent the form.
-- Repeatable: IF NOT EXISTS makes a second run change nothing.
CREATE TABLE IF NOT EXISTS owner_lead_activities (
    id BIGINT NOT NULL,
    lead_id VARCHAR(26) NOT NULL,
    type VARCHAR(30) NOT NULL,
    actor_user_id VARCHAR(26),
    from_status VARCHAR(30),
    to_status VARCHAR(30),
    from_assignee_id VARCHAR(26),
    to_assignee_id VARCHAR(26),
    note VARCHAR(2000),
    next_action_at TIMESTAMP WITH TIME ZONE,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT owner_lead_activities_pkey PRIMARY KEY (id),
    CONSTRAINT fk_owner_lead_activities_lead_id FOREIGN KEY (lead_id) REFERENCES owner_leads (id)
);

-- the time-ordered history of one lead; it also serves as the index of the lead_id foreign key
CREATE INDEX IF NOT EXISTS idx_owner_lead_activities_lead_id_occurred_at
    ON owner_lead_activities (lead_id, occurred_at);

-- Every time a staff member opens the detail of a lead, which holds contact, address and coordinates
-- (acquisition-owner-lead-queue BR-7): who, when, which lead.
CREATE TABLE IF NOT EXISTS owner_lead_access_logs (
    id BIGINT NOT NULL,
    lead_id VARCHAR(26) NOT NULL,
    user_id VARCHAR(26) NOT NULL,
    accessed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT owner_lead_access_logs_pkey PRIMARY KEY (id),
    CONSTRAINT fk_owner_lead_access_logs_lead_id FOREIGN KEY (lead_id) REFERENCES owner_leads (id)
);

CREATE INDEX IF NOT EXISTS idx_owner_lead_access_logs_lead_id_accessed_at
    ON owner_lead_access_logs (lead_id, accessed_at);
CREATE INDEX IF NOT EXISTS idx_owner_lead_access_logs_user_id ON owner_lead_access_logs (user_id);
