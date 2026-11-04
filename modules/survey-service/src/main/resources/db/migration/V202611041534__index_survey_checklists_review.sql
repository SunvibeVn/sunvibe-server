-- The indexes of the revision chain and of the review (survey-checklist submission spec I-1, 5.3, plan PD-4, PD-8).
-- Alone in its file: a plain CREATE INDEX holds a SHARE lock on survey_checklists for its build (writes wait); that
-- table has one row per started visit, so the build is short, and no other lock is held with it.
-- uq_survey_checklists_submitted_per_cycle: spec I-1, at most one SUBMITTED revision per lead and intent cycle, also
-- under concurrent submits; it also serves the review queue, whose query repeats the predicate as a literal.
SET LOCAL lock_timeout = '5s';

CREATE UNIQUE INDEX IF NOT EXISTS uq_survey_checklists_submitted_per_cycle ON survey_checklists (lead_id, intent_cycle)
    WHERE status = 'SUBMITTED';
CREATE INDEX IF NOT EXISTS idx_survey_checklists_previous_checklist_id ON survey_checklists (previous_checklist_id);
