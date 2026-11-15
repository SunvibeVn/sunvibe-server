package vn.sunvibe.brokerage.core.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionStatus;

import java.time.Instant;
import java.time.LocalDate;

/** JPA Entity - a version of a brokerage agreement (E05-S2 spec 5.3), written through its agreement. */
@Entity
@Table(name = "brokerage_agreement_versions")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgreementVersionEntity {

    @Id
    @Column(name = "id", length = 26)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agreement_id")
    private BrokerageAgreementEntity agreement;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private VersionStatus status;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "basis_lead_id", nullable = false, length = 26)
    private String basisLeadId;

    @Column(name = "basis_intake_id", nullable = false, length = 26)
    private String basisIntakeId;

    @Column(name = "created_by_user_id", nullable = false, length = 26)
    private String createdByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Override
    public String toString() {
        return "AgreementVersionEntity[" + id + "]";
    }
}
