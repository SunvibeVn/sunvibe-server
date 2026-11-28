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
 * JPA Entity - one mandatory fee of a listing's publication (listing-publish E06-S7 spec 5.3, E06S7-D2), copied from its
 * live revision. No JPA association: the adapter reads and writes these rows by their listing id. {@link #toString()}
 * shows the key only.
 */
@Entity
@Table(name = "listing_publication_fees")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListingPublicationFeeEntity {

    @EmbeddedId
    private ListingPublicationFeeKey id;

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
        return "ListingPublicationFeeEntity[" + id + "]";
    }
}
