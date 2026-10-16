-- Wave W8 (property-units brief US-1, US-2, BR-7, BR-10, E-2, E-3; owner decisions Q-7, C-14): every existing property
-- gets its canonical unit and one ownership. Data migration: under D-02 the dev database is disposable.
-- Repeatable: a property that already has a unit (or an ownership) is skipped, so running the script again inserts
-- nothing. No legacy column is changed; property_availability is neither read nor changed (BR-13).
-- Unit: code, type, rooms and area come from the property; the state follows properties.status (BR-7, E-2).
-- Ownership: owner_id is copied as is, even when no account has it (E-3). valid_from is the stored date of
-- properties.created_at: CAST keeps the local date, there is no time zone shift (C-14).
-- created_at and updated_at are the time of this migration (spec A-5); version takes its default 0.
-- ULIDs (ADR-006) come from a temporary function that is dropped at the end of the script: nothing stays in the
-- database. 10 characters of time (milliseconds of clock_timestamp()) then 16 characters from 80 random bits of
-- gen_random_uuid() (core since PostgreSQL 13, no extension; the fixed version and variant nibbles are skipped),
-- in the Crockford base32 alphabet (no I, L, O, U).
CREATE OR REPLACE FUNCTION w8_tmp_ulid() RETURNS VARCHAR(26)
LANGUAGE plpgsql
AS $$
DECLARE
    alphabet CONSTANT TEXT := '0123456789ABCDEFGHJKMNPQRSTVWXYZ';
    t BIGINT := floor(extract(epoch FROM clock_timestamp()) * 1000)::BIGINT;
    u TEXT := replace(gen_random_uuid()::TEXT, '-', '');
    r BIT(80) := ('x' || substr(u, 1, 12) || substr(u, 14, 3) || substr(u, 18, 5))::BIT(80);
    result TEXT := '';
BEGIN
    -- time part: 48 bits as 10 characters of 5 bits, most significant first
    FOR i IN REVERSE 9..0 LOOP
        result := result || substr(alphabet, ((t >> (5 * i)) & 31)::INTEGER + 1, 1);
    END LOOP;
    -- random part: 80 bits as 16 characters of 5 bits
    FOR j IN 0..15 LOOP
        result := result || substr(alphabet, substring(r FROM 5 * j + 1 FOR 5)::INTEGER + 1, 1);
    END LOOP;
    RETURN result;
END;
$$;

INSERT INTO units (id, property_id, unit_code, unit_type, bedrooms, bathrooms, area_sqm, availability_state,
                   created_at, updated_at)
SELECT w8_tmp_ulid(), p.id, p.property_code, p.property_type, p.bedrooms, p.bathrooms, p.square_meters,
       CASE
           WHEN p.status IN ('AVAILABLE', 'DRAFT', 'PENDING_APPROVAL') THEN 'AVAILABLE'
           WHEN p.status = 'RENTED' THEN 'OCCUPIED'
           WHEN p.status = 'UNDER_MAINTENANCE' THEN 'MAINTENANCE'
           ELSE 'BLOCKED' -- UNAVAILABLE, SOLD and any other value
       END,
       now(), now()
FROM properties p
WHERE NOT EXISTS (SELECT 1 FROM units u WHERE u.property_id = p.id);

INSERT INTO property_ownerships (id, property_id, user_id, relation_type, verification_state, valid_from, created_at)
SELECT w8_tmp_ulid(), p.id, p.owner_id, 'OWNER', 'UNVERIFIED', CAST(p.created_at AS DATE), now()
FROM properties p
WHERE NOT EXISTS (SELECT 1 FROM property_ownerships o WHERE o.property_id = p.id);

DROP FUNCTION w8_tmp_ulid();
