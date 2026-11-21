package vn.sunvibe.listing.core.domain.model.listing;

/**
 * What the contact found (listing-publish US-6 AC-1, BR-9; E06-S3 spec E06S3-D4): the unit still available, no longer
 * available, or the person could not be reached.
 */
public enum FreshnessOutcome {
    AVAILABLE,
    NOT_AVAILABLE,
    UNREACHABLE
}
