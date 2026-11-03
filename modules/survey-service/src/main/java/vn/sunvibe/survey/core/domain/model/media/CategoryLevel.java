package vn.sunvibe.survey.core.domain.model.media;

/** Where an evidence category may be placed (survey-media spec 4.6). */
public enum CategoryLevel {
    /** On the asset only: no space. */
    ASSET,
    /** On one observed space of G1 only. */
    SPACE,
    /** On the asset or on a space. */
    ASSET_OR_SPACE
}
