package vn.sunvibe.listing.core.domain.model.listing;

/**
 * The outcome of a review decision (listing-publish US-4 AC-3, BR-7; E06-S4 spec E06S4-D6): approve, return for changes
 * or reject. The decided revision takes the status of the same name.
 */
public enum ReviewOutcome {
    APPROVED(RevisionStatus.APPROVED),
    NEEDS_CHANGES(RevisionStatus.NEEDS_CHANGES),
    REJECTED(RevisionStatus.REJECTED);

    private final RevisionStatus status;

    ReviewOutcome(RevisionStatus status) {
        this.status = status;
    }

    /** The status the decided revision takes. */
    public RevisionStatus status() {
        return status;
    }
}
