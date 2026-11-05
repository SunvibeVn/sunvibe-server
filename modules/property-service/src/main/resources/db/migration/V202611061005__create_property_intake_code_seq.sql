-- The number of an intake property code SV-<TYP>-<6 digits> (property-create intake spec 5.2 file 4, A-3). A client could
-- only have taken such a code through the legacy endpoint before it refused those prefixes, so the sequence starts after
-- the highest one found; a table with no such code leaves it at 1. A plain read of properties, no lock beyond it.
-- A sequence never rolls back: a failed completion leaves a gap, which is harmless.
SET LOCAL lock_timeout = '5s';

CREATE SEQUENCE property_intake_code_seq;

SELECT setval('property_intake_code_seq', m)
FROM (SELECT max(substring(property_code FROM '^SV-(?:HOU|BLD|VIL|HMS|CDT|APT)-([0-9]{1,18})$')::bigint) m
      FROM properties) s
WHERE m IS NOT NULL;
