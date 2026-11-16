package vn.sunvibe.brokerage.core.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * JPA Entity - one percentage tier of a version's brokerage fee or renewal fee (E05-S4 spec S4-D4), written through its
 * version. Its start and percentage are private (brokerage-agreement BR-17), so {@link #toString()} shows the key only.
 */
@Entity
@Table(name = "brokerage_agreement_version_fee_tiers")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgreementVersionFeeTierEntity {

    @EmbeddedId
    private AgreementVersionFeeTierKey id;

    @MapsId("versionId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id")
    private AgreementVersionEntity version;

    @Column(name = "from_months", nullable = false)
    private int fromMonths;

    @Column(name = "percent", nullable = false)
    private int percent;

    @Override
    public String toString() {
        return "AgreementVersionFeeTierEntity[" + id + "]";
    }
}
