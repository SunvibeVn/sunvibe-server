package vn.sunvibe.survey.core.domain.model.media;

/** How a photo was removed (survey-media spec 4.7, A-9). */
public enum RemovalKind {
    /** By the holder's removal command. */
    HOLDER,
    /** By a G1 save that ended the photo's placement; the save holds the correction. */
    CASCADE
}
