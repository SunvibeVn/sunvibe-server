-- Which unit each surveyed space became (property-create intake spec 5.2 file 8, D5-20): condition and amenities stay in the
-- revision and reach a unit through this row, so P101's balcony reads on P101 and never on P102. canonical is false on a
-- link. unit_id is a plain id: an FK to units would block its writers until the file ends (spec 5.2).
SET LOCAL lock_timeout = '5s';

CREATE TABLE inventory_unit_mappings (
    intake_id VARCHAR(26) NOT NULL,
    space_id VARCHAR(26) NOT NULL,
    space_code VARCHAR(50) NOT NULL,
    unit_id VARCHAR(26) NOT NULL,
    canonical BOOLEAN NOT NULL,
    CONSTRAINT inventory_unit_mappings_pkey PRIMARY KEY (intake_id, space_id),
    CONSTRAINT fk_inventory_unit_mappings_intake_id FOREIGN KEY (intake_id) REFERENCES inventory_intakes (id),
    CONSTRAINT uq_inventory_unit_mappings_intake_unit UNIQUE (intake_id, unit_id)
);

CREATE UNIQUE INDEX uq_inventory_unit_mappings_canonical ON inventory_unit_mappings (intake_id) WHERE canonical;
CREATE INDEX idx_inventory_unit_mappings_unit_id ON inventory_unit_mappings (unit_id);
