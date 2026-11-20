package vn.sunvibe.listing.core.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.sunvibe.listing.core.domain.model.listing.ChargeUnit;
import vn.sunvibe.listing.core.domain.model.listing.FeeBasis;

import java.math.BigDecimal;

/**
 * JPA Entity - one mandatory fee of a revision's terms snapshot (listing-publish E06-S2 spec 5.3, E06S2-D6), copied
 * from E05. No JPA association: the adapter reads and writes these rows by their revision id. E05's terms are private
 * (brokerage-agreement BR-17), so {@link #toString()} shows the key only.
 */
@Entity
@Table(name = "listing_revision_fees")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListingRevisionFeeEntity {

    @EmbeddedId
    private ListingRevisionFeeKey id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "basis", nullable = false, length = 20)
    private FeeBasis basis;

    @Column(name = "amount", precision = 15, scale = 0)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "charge_unit", length = 30)
    private ChargeUnit chargeUnit;

    @Override
    public String toString() {
        return "ListingRevisionFeeEntity[" + id + "]";
    }
}
