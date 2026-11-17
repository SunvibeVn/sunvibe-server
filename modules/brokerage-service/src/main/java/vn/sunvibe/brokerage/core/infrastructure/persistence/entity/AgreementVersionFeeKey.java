package vn.sunvibe.brokerage.core.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Objects;

/** JPA Key - one mandatory fee of a version: the version and the fee's number in its list (E05-S3 spec S3-D9). */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AgreementVersionFeeKey implements Serializable {

    @Column(name = "version_id", length = 26)
    private String versionId;

    @Column(name = "fee_no")
    private int feeNo;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AgreementVersionFeeKey key)) {
            return false;
        }
        return feeNo == key.feeNo && Objects.equals(versionId, key.versionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(versionId, feeNo);
    }

    @Override
    public String toString() {
        return "AgreementVersionFeeKey[" + versionId + "/" + feeNo + "]";
    }
}
