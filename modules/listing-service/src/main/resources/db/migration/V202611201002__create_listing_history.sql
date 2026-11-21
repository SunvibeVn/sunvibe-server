-- The history of a listing (listing-publish E06-S1 spec 5.3, E06S1-D7; listing BR-3, LM-2): the event, its actor, its
-- time and its revision. Creates a new, empty table. Insert-only. action is a fixed enum name with no CHECK, as
-- brokerage_agreement_events.event_type, so later slices add events without a migration. actor_user_id names a user of
-- auth-service and is NULL for an event of the system. No free text.
SET LOCAL lock_timeout = '5s';

CREATE TABLE listing_history (
    id VARCHAR(26) NOT NULL,
    listing_id VARCHAR(26) NOT NULL,
    action VARCHAR(40) NOT NULL,
    revision_no INTEGER,
    actor_user_id VARCHAR(26),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT listing_history_pkey PRIMARY KEY (id),
    CONSTRAINT fk_listing_history_listing FOREIGN KEY (listing_id) REFERENCES listings (id)
);

CREATE INDEX idx_listing_history_listing ON listing_history (listing_id, occurred_at);
