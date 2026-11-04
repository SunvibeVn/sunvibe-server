package vn.sunvibe.survey.core.domain.model.security;

/** What the refused caller tried (survey-checklist spec 4.10). */
public enum SecurityAction {
    /** A read. */
    READ,
    /** A checklist start. */
    START,
    /** A group save. */
    SAVE,
    /** A takeover. */
    TAKEOVER,
    /** A photo create or content upload (survey-media spec 4.9). */
    UPLOAD,
    /** A photo removal (survey-media spec 4.9). */
    REMOVE,
    /** The malware scan of a photo's source by the worker (survey-media spec 4.9). */
    SCAN,
    /** An ADMIN's security hold of a photo (survey-media spec A-23). */
    HOLD,
    /** An ADMIN's release of a photo's hold (survey-media spec A-23). */
    RELEASE,
    /** A checklist submission (survey-checklist submission spec 4.9). */
    SUBMIT,
    /** A decision on a submitted revision (survey-checklist submission spec 4.9). */
    REVIEW,
    /** The start of a new revision on a visit (survey-checklist submission spec 4.9). */
    REVISION,
    /** The deselection of a carried photo (survey-checklist submission spec 4.9). */
    DESELECT
}
