-- The agreements by creation time, newest first, then id, for the staff agreement list's default order
-- (brokerage-agreement E05-S7 spec S7-D1, S7-D9; a total order, so pages neither repeat nor skip a row).
-- Cost (flyway.md, spec S7-D9): a plain CREATE INDEX holds a SHARE lock on its table, which blocks writes for the build;
-- the table holds only the E05 chain's agreements on staging and no row in production, so the build takes
-- milliseconds, bounded by lock_timeout. Expand-only: the running version ignores the index.
SET LOCAL lock_timeout = '5s';

CREATE INDEX idx_brokerage_agreements_created_at ON brokerage_agreements (created_at DESC, id DESC);
