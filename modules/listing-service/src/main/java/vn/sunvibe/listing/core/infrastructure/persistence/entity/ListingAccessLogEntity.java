package vn.sunvibe.listing.core.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * JPA Entity - one access to a listing's private data (listing-publish E06-S4 spec 5.3, E06S4-D12, D18): its id a
 * Snowflake the application draws, no location value. Insert-only.
 */
@Entity
@Table(name = "listing_access_logs")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListingAccessLogEntity {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false, length = 26)
    private String userId;

    @Column(name = "listing_id", nullable = false, length = 26)
    private String listingId;

    @Column(name = "revision_no")
    private Integer revisionNo;

    @Column(name = "action", nullable = false, length = 40)
    private String action;

    @Column(name = "accessed_at", nullable = false)
    private Instant accessedAt;

    @Override
    public String toString() {
        return "ListingAccessLogEntity[" + id + "]";
    }
}
