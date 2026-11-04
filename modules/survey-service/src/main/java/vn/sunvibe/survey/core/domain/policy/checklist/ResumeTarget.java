package vn.sunvibe.survey.core.domain.policy.checklist;

/** Where a resume takes a held lead (survey-checklist submission spec 4.8, 7.2 {@code resume.target}). */
public enum ResumeTarget {
    /** The record can still be completed. */
    SURVEY_SCHEDULED,
    /** The cycle has a usable PASSED revision. */
    QUALIFIED
}
