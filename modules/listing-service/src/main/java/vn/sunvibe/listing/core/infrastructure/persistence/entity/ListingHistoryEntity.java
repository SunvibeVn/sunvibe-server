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
import vn.sunvibe.listing.core.domain.model.listing.ListingHistoryAction;

import java.time.Instant;

/** JPA Entity - one row of a listing's history (listing-publish E06-S1 spec 5.3; listing BR-3). Insert-only. */
@Entity
@Table(name = "listing_history")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListingHistoryEntity {

    @Id
    @Column(name = "id", length = 26)
    private String id;

    @Column(name = "listing_id", nullable = false, length = 26)
    private String listingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 40)
    private ListingHistoryAction action;

    @Column(name = "revision_no")
    private Integer revisionNo;

    /** Null for an event of the system. */
    @Column(name = "actor_user_id", length = 26)
    private String actorUserId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Override
    public String toString() {
        return "ListingHistoryEntity[" + id + "]";
    }
}
