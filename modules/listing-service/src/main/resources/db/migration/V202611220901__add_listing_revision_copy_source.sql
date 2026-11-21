-- The revision a Draft was copied from (listing-publish E06-S4 spec 5.3, E06S4-D14, E06S4-D18; US-2 AC-6): its number
-- within the same listing, null for a revision not copied. The review reads it to follow a copy back to the authors of
-- its source (E06S4-D3); the domain guards that it is below the copy's own number. No CHECK and no foreign key.
-- Cost (flyway.md): listing_revisions holds rows on staging; a nullable column without a default changes only metadata,
-- no rewrite. No backfill: no revision was ever copied.
SET LOCAL lock_timeout = '5s';

ALTER TABLE listing_revisions ADD COLUMN copied_from_revision_no INTEGER;
