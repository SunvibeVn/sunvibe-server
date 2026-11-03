package vn.sunvibe.survey.core.domain.model.security;

/** Why the access was refused (survey-checklist spec 4.10). */
public enum SecurityOutcome {
    /** The lead exists but the caller cannot see it (404). */
    NOT_VISIBLE,
    /** The caller sees the lead but is not its assignee (403). */
    NOT_ASSIGNEE,
    /** The scan found malware in a photo's source; the photo is REJECTED (survey-media spec 4.9). */
    MALWARE_DETECTED,
    /** An ADMIN held a photo (survey-media spec A-23). */
    HELD,
    /** An ADMIN released a photo's hold (survey-media spec A-23). */
    RELEASED,
    /** The first read refused by an hourly read budget in that hour (survey-media spec A-26). */
    BUDGET_EXCEEDED
}
