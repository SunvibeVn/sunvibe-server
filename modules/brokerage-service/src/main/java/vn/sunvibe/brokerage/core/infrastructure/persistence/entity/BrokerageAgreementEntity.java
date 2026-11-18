package vn.sunvibe.brokerage.core.infrastructure.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementStatus;
import vn.sunvibe.brokerage.core.domain.model.agreement.TransactionType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA Entity - a brokerage agreement (E05-S2 spec 5.3). The row version makes an insert with an assigned id a persist,
 * and later commands bump it.
 */
@Entity
@Table(name = "brokerage_agreements")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BrokerageAgreementEntity {

    @Id
    @Column(name = "id", length = 26)
    private String id;

    @Column(name = "unit_id", nullable = false, length = 26)
    private String unitId;

    @Column(name = "unit_code", length = 50)
    private String unitCode;

    @Column(name = "property_id", nullable = false, length = 36)
    private String propertyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType transactionType;

    @Column(name = "owner_user_id", nullable = false, length = 26)
    private String ownerUserId;

    @Column(name = "basis_lead_id", nullable = false, length = 26)
    private String basisLeadId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AgreementStatus status;

    @Column(name = "created_by_user_id", nullable = false, length = 26)
    private String createdByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    private Long version;

    @OneToMany(mappedBy = "agreement", cascade = CascadeType.ALL)
    @OrderBy("versionNo ASC")
    @Builder.Default
    private List<AgreementVersionEntity> versions = new ArrayList<>();

    @Override
    public String toString() {
        return "BrokerageAgreementEntity[" + id + "]";
    }
}
