-- At most one unmarked PASSED revision per lead and intent cycle (property-create requalification spec I-1, D5-1).
-- Alone in its file: a plain CREATE INDEX holds a SHARE lock on survey_checklists for its build (writes wait), and even a
-- partial build scans every row; that table has one row per started visit and is small before go-live, so the build is
-- short, and no other lock is held with it. No IF NOT EXISTS: an index of this name with another definition must fail
-- the start-up, not stand in for this rule.
-- It fails on two PASSED revisions of one cycle; the query run before the deploy (spec 5.2) proves there are none. The
-- earlier commands allow at most one, but nothing enforced that until now.
SET LOCAL lock_timeout = '5s';

CREATE UNIQUE INDEX uq_survey_checklists_current_passed_per_cycle ON survey_checklists (lead_id, intent_cycle)
    WHERE status = 'PASSED' AND requalified_at IS NULL;
