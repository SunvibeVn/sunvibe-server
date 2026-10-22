-- Owner lead property types become the six types Sunvibe takes (acquisition-owner-intake BR-3, Q-16): HOUSE,
-- WHOLE_BUILDING, VILLA, HOMESTAY, CONDOTEL, APARTMENT; no "other" value, and the type stays optional (NULL).
-- Stored leads move to the new types: every apartment layout (STUDIO .. PENTHOUSE) becomes APARTMENT, TOWNHOUSE
-- becomes HOUSE, and LAND and OTHER lose their type, since land is not a Sunvibe type and "other" says nothing.
-- submission_snapshot keeps the value as the owner sent it; no other column or table changes.
-- Owner-approved exception to expand-only (dev data only): the change is lossy and has no down migration, and the
-- previous server version cannot run on the migrated table. Rollback means recreating the dev database.
-- Repeatable: the constraint is dropped if present and added again, and each UPDATE only touches old values, so a
-- second run changes nothing. Flyway runs the whole file in one transaction.
ALTER TABLE owner_leads DROP CONSTRAINT IF EXISTS chk_owner_leads_property_type;

UPDATE owner_leads SET property_type = 'APARTMENT'
WHERE property_type IN ('STUDIO', 'ONE_BEDROOM', 'TWO_BEDROOM', 'THREE_BEDROOM', 'FOUR_BEDROOM', 'DUPLEX',
                        'PENTHOUSE');

UPDATE owner_leads SET property_type = 'HOUSE'
WHERE property_type = 'TOWNHOUSE';

UPDATE owner_leads SET property_type = NULL
WHERE property_type IN ('LAND', 'OTHER');

ALTER TABLE owner_leads ADD CONSTRAINT chk_owner_leads_property_type CHECK (property_type IN ('HOUSE',
    'WHOLE_BUILDING', 'VILLA', 'HOMESTAY', 'CONDOTEL', 'APARTMENT'));
