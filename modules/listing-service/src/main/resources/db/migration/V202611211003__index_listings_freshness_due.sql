-- The listings to reconfirm (listing-publish E06-S3 spec 5.3 item 4, E06S3-D7): the candidates of the reconfirmation
-- list, read by keyset in (freshness_confirmed_on, id) order, only listings not stored as archived with a valid
-- confirmation. A plain CREATE INDEX blocks writes on listings for its build, so it is alone in its file (flyway.md);
-- the build is short because listings holds only the E2E listings on staging and no row in production (SV-217).
SET LOCAL lock_timeout = '5s';

CREATE INDEX idx_listings_freshness_due ON listings (freshness_confirmed_on, id)
    WHERE state <> 'ARCHIVED' AND freshness_confirmed_on IS NOT NULL;
