-- Wave W8 (property-units brief BR-9, BR-10; spec section 4): who owns a property is a record of its own, not a role
-- (ADR-005). W8 writes only OWNER / UNVERIFIED rows, valid from the property's creation date and with no end
-- (BR-10): V202610161003 creates them for the existing properties and property create for new ones.
-- id is a ULID (ADR-006). user_id has no foreign key because auth-service owns users, as for properties.owner_id
-- (W5-4); it is 36 characters long so that any legacy owner_id fits (E-3). valid_to NULL means no end.
-- No CHECK on relation_type and verification_state (spec A-2). No version or updated_at: nothing updates an
-- ownership in W8 (spec A-4).
-- Repeatable: IF NOT EXISTS makes a second run change nothing.
CREATE TABLE IF NOT EXISTS property_ownerships (
    id VARCHAR(26) NOT NULL,
    property_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    relation_type VARCHAR(40) NOT NULL,
    verification_state VARCHAR(20) NOT NULL,
    valid_from DATE NOT NULL,
    valid_to DATE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT property_ownerships_pkey PRIMARY KEY (id),
    CONSTRAINT fk_property_ownerships_property_id FOREIGN KEY (property_id) REFERENCES properties (id)
);

CREATE INDEX IF NOT EXISTS idx_property_ownerships_property_id ON property_ownerships (property_id);
CREATE INDEX IF NOT EXISTS idx_property_ownerships_user_id ON property_ownerships (user_id);
