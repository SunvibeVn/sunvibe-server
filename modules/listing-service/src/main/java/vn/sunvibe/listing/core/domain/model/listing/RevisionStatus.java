package vn.sunvibe.listing.core.domain.model.listing;

/**
 * The status of a listing revision (listing section 3.3): the whole vocabulary of the brief, of which the listing
 * create reaches {@code DRAFT} only.
 */
public enum RevisionStatus {
    DRAFT,
    PENDING_REVIEW,
    NEEDS_CHANGES,
    REJECTED,
    APPROVED,
    LIVE,
    REPLACED,
    CANCELLED
}
