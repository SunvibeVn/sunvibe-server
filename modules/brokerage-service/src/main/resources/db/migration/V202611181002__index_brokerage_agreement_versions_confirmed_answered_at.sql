-- The confirmed versions by their owner's confirmation time, for the lapse job's second selection (brokerage-agreement
-- E05-S6 spec S6-D5: owner_answered_at at or before now - 14 days, oldest first; a confirmed version without an owner
-- answer never matches).
-- Cost (flyway.md, spec 5.3): a plain CREATE INDEX holds a SHARE lock on its table, which blocks writes for the build;
-- the table holds only the E05 drafts and sent versions on staging and no row in production, so the build takes
-- milliseconds, bounded by lock_timeout.
SET LOCAL lock_timeout = '5s';

CREATE INDEX idx_brokerage_agreement_versions_confirmed_answered_at ON brokerage_agreement_versions (owner_answered_at)
    WHERE status = 'OWNER_CONFIRMED';
