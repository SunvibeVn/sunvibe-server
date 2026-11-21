package vn.sunvibe.listing.core.domain.model.listing;

/**
 * Which agreement version a revision's terms snapshot equals by value (listing-publish BR-4, US-4 AC-5; E06-S4 spec
 * E06S4-D7): the version in effect, else the version waiting to start, else none; or the revision holds no snapshot.
 */
public enum TermsMatch {
    IN_EFFECT,
    PENDING_START,
    NONE,
    NO_SNAPSHOT
}
