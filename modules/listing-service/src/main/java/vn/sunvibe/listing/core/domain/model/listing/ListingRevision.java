package vn.sunvibe.listing.core.domain.model.listing;

import java.time.Instant;

/**
 * A revision of a listing (listing BR-2: the listing is a stable shell and its revisions are its content; section 3.3
 * for the statuses). The content columns arrive with the revision save of a later slice.
 */
public final class ListingRevision {

    private final String id;
    private final int revisionNo;
    private final RevisionStatus status;
    private final String createdBy;
    private final Instant createdAt;
    private final Instant updatedAt;

    private ListingRevision(String id, int revisionNo, RevisionStatus status, String createdBy, Instant createdAt,
                            Instant updatedAt) {
        if (id == null || status == null || createdBy == null || createdAt == null || updatedAt == null) {
            throw new IllegalStateException("a listing revision needs its id, status, creator and times");
        }
        if (revisionNo < 1) {
            throw new IllegalStateException("a listing revision is numbered from 1");
        }
        this.id = id;
        this.revisionNo = revisionNo;
        this.status = status;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** A new revision, in the status {@code DRAFT}, created and last changed at {@code now}. */
    public static ListingRevision create(String id, int revisionNo, String createdBy, Instant now) {
        return new ListingRevision(id, revisionNo, RevisionStatus.DRAFT, createdBy, now, now);
    }

    /** A stored revision. */
    public static ListingRevision load(String id, int revisionNo, RevisionStatus status, String createdBy,
                                       Instant createdAt, Instant updatedAt) {
        return new ListingRevision(id, revisionNo, status, createdBy, createdAt, updatedAt);
    }

    public String id() {
        return id;
    }

    public int revisionNo() {
        return revisionNo;
    }

    public RevisionStatus status() {
        return status;
    }

    public String createdBy() {
        return createdBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
