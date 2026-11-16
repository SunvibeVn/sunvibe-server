package vn.sunvibe.brokerage.core.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.sunvibe.brokerage.core.domain.model.agreement.ChargeUnit;
import vn.sunvibe.brokerage.core.domain.model.agreement.FeeBasis;

import java.math.BigDecimal;

/**
 * JPA Entity - one mandatory fee of a version's confirmed terms (E05-S3 spec S3-D9), written through its version. Its
 * name and amount are private (brokerage-agreement BR-17), so {@link #toString()} shows the key only.
 */
@Entity
@Table(name = "brokerage_agreement_version_fees")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgreementVersionFeeEntity {

    @EmbeddedId
    private AgreementVersionFeeKey id;

    @MapsId("versionId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id")
    private AgreementVersionEntity version;

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
        return "AgreementVersionFeeEntity[" + id + "]";
    }
}
