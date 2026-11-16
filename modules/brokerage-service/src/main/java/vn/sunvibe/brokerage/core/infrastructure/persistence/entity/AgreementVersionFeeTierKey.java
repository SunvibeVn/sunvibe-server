package vn.sunvibe.brokerage.core.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Objects;

/**
 * JPA Key - one percentage tier of a version's fee policy: the version, whether it is the renewal's tier, and the tier's
 * number in its list (E05-S4 spec S4-D4).
 */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AgreementVersionFeeTierKey implements Serializable {

    @Column(name = "version_id", length = 26)
    private String versionId;

    @Column(name = "renewal")
    private boolean renewal;

    @Column(name = "tier_no")
    private int tierNo;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AgreementVersionFeeTierKey key)) {
            return false;
        }
        return renewal == key.renewal && tierNo == key.tierNo && Objects.equals(versionId, key.versionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(versionId, renewal, tierNo);
    }

    @Override
    public String toString() {
        return "AgreementVersionFeeTierKey[" + versionId + "/" + renewal + "/" + tierNo + "]";
    }
}
