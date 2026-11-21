package vn.sunvibe.listing.core.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.sunvibe.listing.core.domain.model.listing.RejectionGroup;
import vn.sunvibe.listing.core.domain.model.listing.ReviewOutcome;

import java.time.Instant;

/**
 * JPA Entity - the decision of a reviewed revision (listing-publish E06-S4 spec 5.3, E06S4-D8): one row per revision,
 * insert-only, removed only with its revision. The reasons are their codes joined by commas, in {@code ChangeReason}
 * order. The reasons, group and note are private (E06S4-D20): {@link #toString()} shows the revision id only.
 */
@Entity
@Table(name = "listing_revision_reviews")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListingRevisionReviewEntity {

    @Id
    @Column(name = "revision_id", length = 26)
    private String revisionId;

    @Column(name = "listing_id", nullable = false, length = 26)
    private String listingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 13)
    private ReviewOutcome outcome;

    @Column(name = "checklist_version")
    private Short checklistVersion;

    @Column(name = "change_reasons", length = 120)
    private String changeReasons;

    @Enumerated(EnumType.STRING)
    @Column(name = "rejection_group", length = 20)
    private RejectionGroup rejectionGroup;

    @Column(name = "note", length = 1000)
    private String note;

    @Column(name = "decided_by", nullable = false, length = 26)
    private String decidedBy;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    @Override
    public String toString() {
        return "ListingRevisionReviewEntity[" + revisionId + "]";
    }
}
