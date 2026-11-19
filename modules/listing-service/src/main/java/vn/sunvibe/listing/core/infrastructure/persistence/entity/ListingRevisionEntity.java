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
import vn.sunvibe.listing.core.domain.model.listing.RevisionStatus;

import java.time.Instant;

/** JPA Entity - one revision of a listing (listing-publish E06-S1 spec 5.3), kept beside the listing's entity. */
@Entity
@Table(name = "listing_revisions")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListingRevisionEntity {

    @Id
    @Column(name = "id", length = 26)
    private String id;

    @Column(name = "listing_id", nullable = false, length = 26)
    private String listingId;

    @Column(name = "revision_no", nullable = false)
    private int revisionNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private RevisionStatus status;

    @Column(name = "created_by", nullable = false, length = 26)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Override
    public String toString() {
        return "ListingRevisionEntity[" + id + "]";
    }
}
