package vn.sunvibe.survey.core.domain.model.checklist;

/** The Sale's judgement of the six-type rental scope (survey-checklist BR-2, US-4 AC-3). */
public enum Fit {
    /** Within the scope; a full assessment can go on. */
    IN_SCOPE,
    /** Outside the scope; the finding is kept. */
    OUT_OF_SCOPE,
    /** Not clear yet. */
    UNDETERMINED
}
