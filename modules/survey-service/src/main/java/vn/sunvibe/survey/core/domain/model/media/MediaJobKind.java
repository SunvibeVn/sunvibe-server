package vn.sunvibe.survey.core.domain.model.media;

/** The work a media job does (survey-media spec 4.4 step 3, 4.7). */
public enum MediaJobKind {
    /** Check, scan and normalize the committed source. */
    PROCESS,
    /** Delete every object of a removed READY photo, due 10 minutes after the request. */
    DELETE_CONTENT
}
