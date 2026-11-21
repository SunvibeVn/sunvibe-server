-- Who opened a listing's private data (listing-publish E06-S4 spec 5.3, E06S4-D12, E06S4-D18; US-4 AC-2; listing BR-5):
-- one row per opening of a revision's real location by a reviewer, never the value opened. The id is a Snowflake drawn
-- by the application (ADR-006), so the column has no default and no identity. user_id names a user of auth-service and
-- listing_id a listing: plain ids, no foreign key, since a log outlives what it names (as inventory_access_logs). No
-- index: nothing reads the table in this slice. Insert-only. A new, empty table: nothing to validate.
CREATE TABLE listing_access_logs (
    id BIGINT NOT NULL,
    user_id VARCHAR(26) NOT NULL,
    listing_id VARCHAR(26) NOT NULL,
    revision_no INTEGER,
    action VARCHAR(40) NOT NULL,
    accessed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT listing_access_logs_pkey PRIMARY KEY (id),
    CONSTRAINT ck_listing_access_logs_action CHECK (action IN ('UNIT_LOCATION_OPENED'))
);
