package vn.sunvibe.survey.core.domain.model.security;

/** Why the access was refused (survey-checklist spec 4.10). */
public enum SecurityOutcome {
    /** The lead exists but the caller cannot see it (404). */
    NOT_VISIBLE,
    /** The caller sees the lead but is not its assignee (403). */
    NOT_ASSIGNEE
}
