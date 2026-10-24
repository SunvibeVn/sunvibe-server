package vn.sunvibe.survey.core.domain.model.log;

/** What a survey read showed (survey-checklist spec A-10). */
public enum AccessResourceType {
    /** The survey block of a lead; the resource id is the lead id. */
    SURVEY_BLOCK,
    /** A checklist, or the answer of a checklist command; the resource id is the checklist id. */
    CHECKLIST,
    /** A takeover checkpoint; the resource id is {@code <checklistId>:<takeoverNo>}. */
    CHECKPOINT
}
