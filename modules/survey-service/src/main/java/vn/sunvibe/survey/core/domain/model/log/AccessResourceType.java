package vn.sunvibe.survey.core.domain.model.log;

/** What a survey read showed (survey-checklist spec A-10). */
public enum AccessResourceType {
    /** The survey block of a lead; the resource id is the lead id. */
    SURVEY_BLOCK,
    /** A checklist, or the answer of a checklist command; the resource id is the checklist id. */
    CHECKLIST,
    /** A takeover checkpoint; the resource id is {@code <checklistId>:<takeoverNo>}. */
    CHECKPOINT,
    /** A survey photo list (one row per user and checklist per 10 minutes); the id is the checklist id (spec A-6). */
    MEDIA_LIST,
    /** A PREVIEW read of a photo; the resource id is the media id (survey-media spec A-6). */
    MEDIA_PREVIEW,
    /** An inline FULL read of a photo; the resource id is the media id (survey-media spec A-6). */
    MEDIA_FULL,
    /** A FULL download of a photo; the resource id is the media id (survey-media spec A-6). */
    MEDIA_DOWNLOAD,
    /** An ADMIN's read of a held photo, any variant; the resource id is the media id (survey-media spec A-23). */
    MEDIA_HELD_READ
}
