-- Two indexes for the reads of brokerage-agreement E05-S5 (spec S5-D11): the owner's agreements by owner_user_id (the
-- owner read of spec S5-D1; the table has an index on basis_lead_id only), and the versions waiting for the owner by
-- their sending time (the lapse job's selection of spec S5-D8: sent_at at or before now - 7 days, oldest first; a
-- version without sent_at never matches).
-- Cost (flyway.md, spec 5.3): each plain CREATE INDEX holds a SHARE lock on its table, which blocks writes for the build;
-- both tables hold only the E05 drafts and sent versions on staging and no row in production, so each build takes
-- milliseconds, bounded by lock_timeout.
SET LOCAL lock_timeout = '5s';

CREATE INDEX idx_brokerage_agreements_owner_user_id ON brokerage_agreements (owner_user_id);

CREATE INDEX idx_brokerage_agreement_versions_awaiting_owner_sent_at ON brokerage_agreement_versions (sent_at)
    WHERE status = 'AWAITING_OWNER';
