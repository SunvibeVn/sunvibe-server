-- Owner lead, the record created when an owner sends the intake form (acquisition-owner-intake brief BR-1 to BR-5,
-- acquisition-owner-lead-queue spec section 4). Sending the form never creates a property or listing (acquisition
-- module brief BR-1). Contact, address and coordinates are private (acquisition module brief BR-2).
-- Expand only: new tables, no existing table, column or row changes; no FK to other modules. Province and ward are
-- kept as code plus the name chosen at submission, without a foreign key to the property-service catalog (ADR-009);
-- assigned_user_id has no FK to users because auth-service owns users.
-- id is a ULID (ADR-006). request_code is the public code SV-XXXX-XXXX and is unique
-- (acquisition-owner-intake BR-11).
-- status holds the 12 OwnerLead states of rebuild doc 09. Only some transitions are allowed by the code; the CHECK
-- only keeps a typo out. source WEB is the only value written for now, the others are reserved.
-- phone_normalized is +84 and 9 digits, so that the same phone matches (acquisition-owner-intake BR-2).
-- address_key is the lower-cased, single-spaced address used to find a repeated submission
-- (acquisition-owner-intake BR-10). submission_snapshot is the request as received, so a later edit by staff
-- never loses the original (acquisition-owner-intake BR-5).
-- latitude and longitude are both set or both empty. The asking price is an amount plus a unit, both set or both
-- empty, and the amount is above zero (acquisition-owner-intake BR-4).
-- version is the optimistic lock of the lead queue.
-- Repeatable: IF NOT EXISTS makes a second run change nothing.
CREATE TABLE IF NOT EXISTS owner_leads (
    id VARCHAR(26) NOT NULL,
    request_code VARCHAR(12) NOT NULL,
    status VARCHAR(30) NOT NULL,
    intent VARCHAR(20) NOT NULL,
    source VARCHAR(20) NOT NULL,
    contact_name VARCHAR(100) NOT NULL,
    phone_normalized VARCHAR(16) NOT NULL,
    email VARCHAR(254),
    province_code VARCHAR(10) NOT NULL,
    province_name VARCHAR(100) NOT NULL,
    ward_code VARCHAR(10) NOT NULL,
    ward_name VARCHAR(100) NOT NULL,
    address_detail VARCHAR(255) NOT NULL,
    address_key VARCHAR(255) NOT NULL,
    latitude NUMERIC(9, 6),
    longitude NUMERIC(9, 6),
    property_type VARCHAR(20),
    asking_price_amount NUMERIC(15, 0),
    asking_price_unit VARCHAR(10),
    available_from DATE,
    notes VARCHAR(1000),
    preferred_callback_time VARCHAR(100),
    consent_record_id VARCHAR(26) NOT NULL,
    submission_snapshot TEXT NOT NULL,
    assigned_user_id VARCHAR(26),
    next_action_at TIMESTAMP WITH TIME ZONE,
    duplicate_of_lead_id VARCHAR(26),
    close_reason VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT owner_leads_pkey PRIMARY KEY (id),
    CONSTRAINT uk_owner_leads_request_code UNIQUE (request_code),
    CONSTRAINT fk_owner_leads_consent_record_id FOREIGN KEY (consent_record_id) REFERENCES consent_records (id),
    CONSTRAINT fk_owner_leads_duplicate_of_lead_id FOREIGN KEY (duplicate_of_lead_id) REFERENCES owner_leads (id),
    CONSTRAINT chk_owner_leads_status CHECK (status IN ('NEW', 'VALIDATING', 'ASSIGNED', 'CONTACTED',
        'SURVEY_SCHEDULED', 'QUALIFIED', 'AGREEMENT_PENDING', 'ONBOARDED', 'DUPLICATE', 'REJECTED', 'WITHDRAWN',
        'ON_HOLD')),
    CONSTRAINT chk_owner_leads_intent CHECK (intent IN ('SHORT_TERM_RENT', 'LONG_TERM_RENT', 'SALE')),
    CONSTRAINT chk_owner_leads_source CHECK (source IN ('WEB', 'REFERRAL', 'OUTBOUND', 'PARTNER', 'STAFF_ENTRY')),
    CONSTRAINT chk_owner_leads_property_type CHECK (property_type IN ('STUDIO', 'ONE_BEDROOM', 'TWO_BEDROOM',
        'THREE_BEDROOM', 'FOUR_BEDROOM', 'DUPLEX', 'PENTHOUSE', 'TOWNHOUSE', 'LAND', 'OTHER')),
    CONSTRAINT chk_owner_leads_price_unit CHECK (asking_price_unit IN ('NIGHT', 'MONTH', 'TOTAL')),
    CONSTRAINT chk_owner_leads_geo_pair CHECK ((latitude IS NULL) = (longitude IS NULL)),
    CONSTRAINT chk_owner_leads_price_pair CHECK ((asking_price_amount IS NULL) = (asking_price_unit IS NULL)),
    CONSTRAINT chk_owner_leads_price_positive CHECK (asking_price_amount IS NULL OR asking_price_amount > 0)
);

-- phone + time finds a repeated submission and the duplicates of a lead (acquisition-owner-intake BR-10)
CREATE INDEX IF NOT EXISTS idx_owner_leads_phone_created_at ON owner_leads (phone_normalized, created_at);
CREATE INDEX IF NOT EXISTS idx_owner_leads_ward_code ON owner_leads (ward_code);
CREATE INDEX IF NOT EXISTS idx_owner_leads_assigned ON owner_leads (assigned_user_id, status, created_at);
CREATE INDEX IF NOT EXISTS idx_owner_leads_status_created_at ON owner_leads (status, created_at);
CREATE INDEX IF NOT EXISTS idx_owner_leads_consent_record_id ON owner_leads (consent_record_id);
CREATE INDEX IF NOT EXISTS idx_owner_leads_duplicate_of ON owner_leads (duplicate_of_lead_id);

-- Leads that look like the same owner or address (acquisition-owner-intake brief US-4): flagged, never merged by the
-- system. One row per pair; the primary key covers lead_id as the index of its foreign key.
CREATE TABLE IF NOT EXISTS owner_lead_duplicate_candidates (
    lead_id VARCHAR(26) NOT NULL,
    candidate_lead_id VARCHAR(26) NOT NULL,
    detected_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT owner_lead_duplicate_candidates_pkey PRIMARY KEY (lead_id, candidate_lead_id),
    CONSTRAINT fk_owner_lead_duplicate_candidates_lead_id FOREIGN KEY (lead_id) REFERENCES owner_leads (id),
    CONSTRAINT fk_owner_lead_duplicate_candidates_candidate_lead_id FOREIGN KEY (candidate_lead_id)
        REFERENCES owner_leads (id)
);

CREATE INDEX IF NOT EXISTS idx_owner_lead_duplicate_candidates_candidate
    ON owner_lead_duplicate_candidates (candidate_lead_id);
