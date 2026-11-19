package vn.sunvibe.brokerage.core.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.sunvibe.brokerage.core.domain.model.agreement.SuspensionReasonType;

import java.time.Instant;

/**
 * JPA Entity - one suspension of an agreement (E05-S8 spec S8-D6, S8-D9, 5.3), kept beside the agreement's entity: no
 * collection of it on {@link BrokerageAgreementEntity}, so no load path of the agreement reads it and no merge of the
 * agreement can remove one. Only {@code AgreementSuspensionRepositoryAdapter} writes the table.
 */
@Entity
@Table(name = "brokerage_agreement_suspensions")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgreementSuspensionEntity {

    @Id
    @Column(name = "id", length = 26)
    private String id;

    @Column(name = "agreement_id", nullable = false, length = 26)
    private String agreementId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_type", nullable = false, length = 20)
    private SuspensionReasonType reasonType;

    /** Private (brokerage-agreement BR-17): never in a text of this entity. */
    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "suspended_by", nullable = false, length = 26)
    private String suspendedBy;

    @Column(name = "suspended_at", nullable = false)
    private Instant suspendedAt;

    @Column(name = "released_by", length = 26)
    private String releasedBy;

    @Column(name = "released_at")
    private Instant releasedAt;

    /** Private (brokerage-agreement BR-17): never in a text of this entity. */
    @Column(name = "release_reason", length = 500)
    private String releaseReason;

    @Override
    public String toString() {
        return "AgreementSuspensionEntity[" + id + "]";
    }
}
