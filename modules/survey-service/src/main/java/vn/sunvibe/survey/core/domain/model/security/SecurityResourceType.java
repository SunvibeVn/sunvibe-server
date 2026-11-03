package vn.sunvibe.survey.core.domain.model.security;

/** What a refused access concerned (survey-checklist spec 4.10). */
public enum SecurityResourceType {
    /** The survey block of a lead. */
    LEAD,
    /** A survey appointment (checklist start). */
    APPOINTMENT,
    /** A survey checklist. */
    CHECKLIST,
    /** A takeover checkpoint of a checklist. */
    CHECKPOINT,
    /** A survey photo; the resource id is the media id (survey-media spec 4.9). */
    MEDIA,
    /** The photos of a checklist (the list or a create); the resource id is the checklist id (spec 4.9). */
    MEDIA_SET
}
