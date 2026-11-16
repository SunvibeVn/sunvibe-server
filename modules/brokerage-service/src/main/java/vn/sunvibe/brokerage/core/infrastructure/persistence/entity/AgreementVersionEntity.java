package vn.sunvibe.brokerage.core.infrastructure.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.sunvibe.brokerage.core.domain.model.agreement.DepositBasis;
import vn.sunvibe.brokerage.core.domain.model.agreement.EarlyLeaseEndType;
import vn.sunvibe.brokerage.core.domain.model.agreement.FeeFormulaType;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA Entity - a version of a brokerage agreement (E05-S2 spec 5.3) with its content (E05-S3 spec S3-D9), its fee
 * policy and its sending time (E05-S4 spec S4-D4), written through its agreement.
 */
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

    @Column(name = "photo_consent")
    private Boolean photoConsent;

    @Column(name = "exterior_photo_consent", nullable = false)
    private boolean exteriorPhotoConsent;

    @Column(name = "monthly_rent_amount", precision = 15, scale = 0)
    private BigDecimal monthlyRentAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "deposit_basis", length = 10)
    private DepositBasis depositBasis;

    @Column(name = "deposit_months")
    private Integer depositMonths;

    @Column(name = "deposit_amount", precision = 15, scale = 0)
    private BigDecimal depositAmount;

    @Column(name = "minimum_term_months")
    private Integer minimumTermMonths;

    @Column(name = "payment_period_months")
    private Integer paymentPeriodMonths;

    /** Whether the fee list is stated: false = not stated yet; true with no row = no mandatory fee besides the rent. */
    @Column(name = "mandatory_fees_stated", nullable = false)
    private boolean mandatoryFeesStated;

    /** Matched by key (version, number) on a merge: a key in both lists is updated, one only stored is removed. */
    @OneToMany(mappedBy = "version", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id.feeNo ASC")
    @Builder.Default
    private List<AgreementVersionFeeEntity> fees = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_formula_type", length = 20)
    private FeeFormulaType feeFormulaType;

    @Column(name = "fee_fixed_amount", precision = 15, scale = 0)
    private BigDecimal feeFixedAmount;

    @Column(name = "fee_free_reason", length = 500)
    private String feeFreeReason;

    @Column(name = "fee_due_days")
    private Integer feeDueDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "renewal_fee_type", length = 20)
    private FeeFormulaType renewalFeeType;

    @Column(name = "renewal_fee_fixed_amount", precision = 15, scale = 0)
    private BigDecimal renewalFeeFixedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "early_lease_end_type", length = 20)
    private EarlyLeaseEndType earlyLeaseEndType;

    @Column(name = "early_lease_end_refund_percent")
    private Integer earlyLeaseEndRefundPercent;

    @Column(name = "early_lease_end_within_months")
    private Integer earlyLeaseEndWithinMonths;

    @Column(name = "protection_days")
    private Integer protectionDays;

    /** When the version was sent to the owner; null for a draft (E05-S4 spec S4-D4). */
    @Column(name = "sent_at")
    private Instant sentAt;

    /**
     * The fee's and the renewal's percentage tiers, matched by key (version, renewal, number) on a merge as the fee rows
     * are: a start moves between rows by an update, so no unique index on the start exists (E05-S4 spec S4-D4).
     */
    @OneToMany(mappedBy = "version", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id.renewal ASC, id.tierNo ASC")
    @Builder.Default
    private List<AgreementVersionFeeTierEntity> feeTiers = new ArrayList<>();

    @Override
    public String toString() {
        return "AgreementVersionEntity[" + id + "]";
    }
}
