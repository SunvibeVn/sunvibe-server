package vn.sunvibe.listing.core.domain.model.listing;

/**
 * Which of E05's versions a terms snapshot copies (listing-publish US-2 AC-2, BR-4; E06-S2 spec E06S2-D6): the version
 * in effect, or the activated version waiting for its start date.
 */
public enum TermsSource {
    IN_EFFECT,
    PENDING_START
}
