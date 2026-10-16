-- Wave W8 (property-units brief BR-2..BR-6, BR-14; spec section 4): a unit is the part of a property that is rented
-- or sold. Every property has one canonical unit (BR-3): V202610161003 creates it for the existing properties and
-- property create for new ones. Expand-only (BR-13): no legacy column or table changes.
-- id is a ULID (ADR-006); property_id is as long as properties.id. unit_code is unique within a property (BR-4);
-- property_id alone is not unique, because more units per property come later (spec A-3). No CHECK on unit_type and
-- availability_state: the application validates the enum values (spec A-2, like the property tables).
-- idx_units_property_id is the index of the foreign key (flyway rule), even though uk_units_property_id_unit_code
-- also starts with property_id.
-- Repeatable: IF NOT EXISTS makes a second run change nothing.
CREATE TABLE IF NOT EXISTS units (
    id VARCHAR(26) NOT NULL,
    property_id VARCHAR(36) NOT NULL,
    unit_code VARCHAR(50) NOT NULL,
    unit_type VARCHAR(50) NOT NULL,
    bedrooms INTEGER NOT NULL,
    bathrooms INTEGER NOT NULL,
    area_sqm DOUBLE PRECISION NOT NULL,
    availability_state VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT units_pkey PRIMARY KEY (id),
    CONSTRAINT fk_units_property_id FOREIGN KEY (property_id) REFERENCES properties (id),
    CONSTRAINT uk_units_property_id_unit_code UNIQUE (property_id, unit_code)
);

CREATE INDEX IF NOT EXISTS idx_units_property_id ON units (property_id);
