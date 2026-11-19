package vn.sunvibe.listing.core.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.sunvibe.listing.core.domain.model.listing.ListingState;
import vn.sunvibe.listing.core.domain.model.listing.TransactionType;

import java.time.Instant;

/**
 * JPA Entity - a listing's own row (listing-publish E06-S1 spec 5.3). The row version is null in the entity built for a
 * new listing, so the insert of a row with an assigned id is a persist (stored 0, E06S1-P13); later commands bump it.
 * No collection of revisions or history: the adapter writes and reads those tables itself, bounded.
 */
@Entity
@Table(name = "listings")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListingEntity {

    @Id
    @Column(name = "id", length = 26)
    private String id;

    @Column(name = "code", nullable = false, length = 12)
    private String code;

    @Column(name = "unit_id", nullable = false, length = 26)
    private String unitId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private TransactionType transactionType;

    @Column(name = "agreement_id", nullable = false, length = 26)
    private String agreementId;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 30)
    private ListingState state;

    @Column(name = "created_by", nullable = false, length = 26)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "row_version")
    private Long rowVersion;

    @Override
    public String toString() {
        return "ListingEntity[" + id + "]";
    }
}
