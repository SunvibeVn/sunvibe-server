package vn.sunvibe.listing.core.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Objects;

/** JPA Key - one fee of a listing's publication: the listing and the fee's number in E05's order (E06S7-D2). */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ListingPublicationFeeKey implements Serializable {

    @Column(name = "listing_id", length = 26)
    private String listingId;

    @Column(name = "fee_no")
    private int feeNo;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ListingPublicationFeeKey key)) {
            return false;
        }
        return feeNo == key.feeNo && Objects.equals(listingId, key.listingId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(listingId, feeNo);
    }

    @Override
    public String toString() {
        return "ListingPublicationFeeKey[" + listingId + "/" + feeNo + "]";
    }
}
