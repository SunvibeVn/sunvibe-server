-- The activated versions by their start date, for the start job's selection (brokerage-agreement E05-S6 spec S6-D6:
-- start_date at or before today in Vietnam, oldest start first; a version without an activation record never matches).
-- Cost (flyway.md, spec 5.3): a plain CREATE INDEX holds a SHARE lock on its table, which blocks writes for the build;
-- the table holds only the E05 drafts and sent versions on staging and no row in production, so the build takes
-- milliseconds, bounded by lock_timeout.
SET LOCAL lock_timeout = '5s';

CREATE INDEX idx_brokerage_agreement_versions_activated_start_date ON brokerage_agreement_versions (start_date)
    WHERE status = 'ACTIVATED';
