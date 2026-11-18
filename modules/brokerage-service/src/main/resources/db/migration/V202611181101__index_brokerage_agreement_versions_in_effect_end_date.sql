-- The activated and effective versions by their end date, for the staff agreement list's "ends within the next 30
-- days" filter and its order (brokerage-agreement E05-S7 spec S7-D4, S7-D9: only those versions can be the version that
-- counts). Cost (flyway.md, spec S7-D9): a plain CREATE INDEX holds a SHARE lock on its table, which blocks writes for
-- the build; the table holds only the E05 chain's versions on staging and no row in production, so the build takes
-- milliseconds, bounded by lock_timeout. Expand-only: the running version ignores the index.
SET LOCAL lock_timeout = '5s';

CREATE INDEX idx_brokerage_agreement_versions_in_effect_end_date ON brokerage_agreement_versions (end_date)
    WHERE status IN ('ACTIVATED', 'EFFECTIVE');
