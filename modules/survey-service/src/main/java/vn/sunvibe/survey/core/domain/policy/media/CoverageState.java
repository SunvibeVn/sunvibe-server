package vn.sunvibe.survey.core.domain.policy.media;

/** The state of one (placement, category) pair of the evidence coverage (survey-media spec 4.6). */
public enum CoverageState {
    /** Required, with at least one usable photo of exactly that placement and category. */
    COVERED,
    /** Required, no usable photo, but a selected PROCESSING photo that is not held. */
    PROCESSING,
    /** Required and neither covered nor processing. */
    MISSING,
    /** Exempted (G4 NOT_APPLICABLE with its reason), or a shared pair not evaluated without spaces. */
    NOT_REQUIRED,
    /** An optional category (SHARED_AMENITY, DETAIL): never required (spec A-12). */
    OPTIONAL
}
