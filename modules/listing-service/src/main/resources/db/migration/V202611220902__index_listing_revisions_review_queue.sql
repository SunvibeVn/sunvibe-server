-- The review queue (listing-publish E06-S4 spec 5.3, E06S4-D10; US-4 AC-1): the Pending review revisions read by keyset
-- in (submitted_at, id) order. A plain CREATE INDEX blocks writes on listing_revisions for its build, so it is alone in
-- its file (flyway.md); the build is short because listing_revisions holds a few dozen rows on staging and none in
-- production.
SET LOCAL lock_timeout = '5s';

CREATE INDEX idx_listing_revisions_review_queue ON listing_revisions (submitted_at, id)
    WHERE status = 'PENDING_REVIEW';
