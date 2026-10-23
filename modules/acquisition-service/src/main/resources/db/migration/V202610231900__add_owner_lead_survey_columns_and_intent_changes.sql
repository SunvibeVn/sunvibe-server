-- Survey appointments and requirement changes of owner leads (survey-schedule spec 5.3; survey-schedule brief BR-8,
-- BR-10, US-5).
-- Expand only: two nullable columns, one column with a constant default and one new table; no row is rewritten and
-- no existing column or constraint changes, so the previous server version keeps running on the migrated tables.
-- owner_leads.intent_cycle counts the requirement cycles of a lead: every stored lead is in cycle 1, and each
-- requirement change starts the next one (survey-schedule BR-10). intent and asking_price_* stay the current values;
-- submission_snapshot keeps what the owner sent.
-- owner_lead_activities.reference_id/reference_no name the survey appointment (its ULID and its number within the
-- lead) a history entry concerns; both set or both empty. There is no foreign key: survey-service owns appointments.
-- owner_lead_intent_changes is the insert-only history of requirement changes: old and new intent and price, the
-- reason and the basis (date and text of the owner's confirmation), who and when, and the cycle it started. Its
-- id is a Snowflake id; actor_user_id has no foreign key because auth-service owns users. A change always moves to a
-- rental intent (survey-schedule US-5 AC-1) and every price is in its intent's unit (acquisition-owner-intake BR-4).
-- Repeatable: IF NOT EXISTS and DROP CONSTRAINT IF EXISTS make a second run change nothing.
ALTER TABLE owner_leads ADD COLUMN IF NOT EXISTS intent_cycle INTEGER NOT NULL DEFAULT 1;
ALTER TABLE owner_leads DROP CONSTRAINT IF EXISTS chk_owner_leads_intent_cycle;
ALTER TABLE owner_leads ADD CONSTRAINT chk_owner_leads_intent_cycle CHECK (intent_cycle > 0);

ALTER TABLE owner_lead_activities ADD COLUMN IF NOT EXISTS reference_id VARCHAR(26);
ALTER TABLE owner_lead_activities ADD COLUMN IF NOT EXISTS reference_no INTEGER;
ALTER TABLE owner_lead_activities DROP CONSTRAINT IF EXISTS chk_owner_lead_activities_reference_pair;
ALTER TABLE owner_lead_activities ADD CONSTRAINT chk_owner_lead_activities_reference_pair
    CHECK ((reference_id IS NULL) = (reference_no IS NULL));
ALTER TABLE owner_lead_activities DROP CONSTRAINT IF EXISTS chk_owner_lead_activities_reference_no;
ALTER TABLE owner_lead_activities ADD CONSTRAINT chk_owner_lead_activities_reference_no
    CHECK (reference_no IS NULL OR reference_no > 0);

CREATE TABLE IF NOT EXISTS owner_lead_intent_changes (
    id BIGINT NOT NULL,
    lead_id VARCHAR(26) NOT NULL,
    from_intent VARCHAR(20) NOT NULL,
    to_intent VARCHAR(20) NOT NULL,
    from_price_amount NUMERIC(15, 0),
    from_price_unit VARCHAR(10),
    to_price_amount NUMERIC(15, 0),
    to_price_unit VARCHAR(10),
    reason VARCHAR(500) NOT NULL,
    basis_confirmed_on DATE NOT NULL,
    basis_text VARCHAR(500) NOT NULL,
    actor_user_id VARCHAR(26) NOT NULL,
    new_intent_cycle INTEGER NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT owner_lead_intent_changes_pkey PRIMARY KEY (id),
    CONSTRAINT fk_owner_lead_intent_changes_lead_id FOREIGN KEY (lead_id) REFERENCES owner_leads (id),
    CONSTRAINT chk_owner_lead_intent_changes_from_intent
        CHECK (from_intent IN ('SHORT_TERM_RENT', 'LONG_TERM_RENT', 'SALE')),
    CONSTRAINT chk_owner_lead_intent_changes_to_intent CHECK (to_intent IN ('SHORT_TERM_RENT', 'LONG_TERM_RENT')),
    CONSTRAINT chk_owner_lead_intent_changes_intent_differs CHECK (from_intent <> to_intent),
    CONSTRAINT chk_owner_lead_intent_changes_from_price_pair
        CHECK ((from_price_amount IS NULL) = (from_price_unit IS NULL)),
    CONSTRAINT chk_owner_lead_intent_changes_to_price_pair CHECK ((to_price_amount IS NULL) = (to_price_unit IS NULL)),
    CONSTRAINT chk_owner_lead_intent_changes_from_price_positive
        CHECK (from_price_amount IS NULL OR from_price_amount > 0),
    CONSTRAINT chk_owner_lead_intent_changes_to_price_positive CHECK (to_price_amount IS NULL OR to_price_amount > 0),
    CONSTRAINT chk_owner_lead_intent_changes_from_price_unit CHECK (from_price_unit IS NULL OR from_price_unit =
        CASE from_intent WHEN 'SHORT_TERM_RENT' THEN 'NIGHT' WHEN 'LONG_TERM_RENT' THEN 'MONTH' ELSE 'TOTAL' END),
    CONSTRAINT chk_owner_lead_intent_changes_to_price_unit CHECK (to_price_unit IS NULL OR to_price_unit =
        CASE to_intent WHEN 'SHORT_TERM_RENT' THEN 'NIGHT' ELSE 'MONTH' END),
    CONSTRAINT chk_owner_lead_intent_changes_new_intent_cycle CHECK (new_intent_cycle > 1)
);

-- the time-ordered requirement history of one lead; it also serves as the index of the lead_id foreign key
CREATE INDEX IF NOT EXISTS idx_owner_lead_intent_changes_lead_id_occurred_at
    ON owner_lead_intent_changes (lead_id, occurred_at);
