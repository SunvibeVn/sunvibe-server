package vn.sunvibe.listing.core.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Objects;

/** JPA Key - one fee of a revision's terms snapshot: the revision and the fee's number in E05's order (E06S2-D6). */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ListingRevisionFeeKey implements Serializable {

    @Column(name = "revision_id", length = 26)
    private String revisionId;

    @Column(name = "fee_no")
    private int feeNo;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ListingRevisionFeeKey key)) {
            return false;
        }
        return feeNo == key.feeNo && Objects.equals(revisionId, key.revisionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(revisionId, feeNo);
    }

    @Override
    public String toString() {
        return "ListingRevisionFeeKey[" + revisionId + "/" + feeNo + "]";
    }
}
