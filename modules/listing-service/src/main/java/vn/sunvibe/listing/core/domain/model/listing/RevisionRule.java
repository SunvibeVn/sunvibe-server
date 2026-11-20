package vn.sunvibe.listing.core.domain.model.listing;

/**
 * A rule a revision must meet to be submitted (listing-publish US-2 AC-4, BR-2; E06-S2 spec E06S2-D7, E06S2-D8), in
 * the order the violations are answered.
 */
public enum RevisionRule {
    REQUIRED,
    TOO_SHORT,
    TOO_LONG,
    PHONE_NUMBER,
    EMAIL,
    LINK,
    SOCIAL_HANDLE,
    FORBIDDEN_WORD
}
