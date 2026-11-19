package vn.sunvibe.brokerage.core.infrastructure.persistence.mapper.manual;

import org.springframework.stereotype.Component;
import vn.sunvibe.brokerage.core.domain.exception.AgreementContentException;
import vn.sunvibe.brokerage.core.domain.model.agreement.ActivationDecision;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementPeriod;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementTermination;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementVersion;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageAgreement;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageFee;
import vn.sunvibe.brokerage.core.domain.model.agreement.EarlyLeaseEndRefund;
import vn.sunvibe.brokerage.core.domain.model.agreement.FeeFormula;
import vn.sunvibe.brokerage.core.domain.model.agreement.FeeFormulaType;
import vn.sunvibe.brokerage.core.domain.model.agreement.FeeTier;
import vn.sunvibe.brokerage.core.domain.model.agreement.MandatoryFee;
import vn.sunvibe.brokerage.core.domain.model.agreement.OwnerAnswer;
import vn.sunvibe.brokerage.core.domain.model.agreement.PhotoConsent;
import vn.sunvibe.brokerage.core.domain.model.agreement.SignedDocument;
import vn.sunvibe.brokerage.core.domain.model.agreement.TermsDeposit;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionTerms;
import vn.sunvibe.brokerage.core.infrastructure.persistence.entity.AgreementVersionEntity;
import vn.sunvibe.brokerage.core.infrastructure.persistence.entity.AgreementVersionFeeEntity;
import vn.sunvibe.brokerage.core.infrastructure.persistence.entity.AgreementVersionFeeKey;
import vn.sunvibe.brokerage.core.infrastructure.persistence.entity.AgreementVersionFeeTierEntity;
import vn.sunvibe.brokerage.core.infrastructure.persistence.entity.AgreementVersionFeeTierKey;
import vn.sunvibe.brokerage.core.infrastructure.persistence.entity.BrokerageAgreementEntity;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * Mapper - a brokerage agreement, its versions, their fee rows and their fee tier rows, both ways, every child mapped.
 */
@Component
public class AgreementEntityMapper {

    /**
     * The entity of the aggregate with every version and fee row. A new aggregate has no row version, so Spring Data
     * persists it; a loaded one keeps the version it was read with, so a merge checks it.
     */
    public BrokerageAgreementEntity toEntity(BrokerageAgreement agreement) {
        AgreementTermination termination = agreement.getTerminationOrNull();
        BrokerageAgreementEntity entity = BrokerageAgreementEntity.builder()
                .id(agreement.getId())
                .unitId(agreement.getUnitId())
                .unitCode(agreement.getUnitCodeOrNull())
                .propertyId(agreement.getPropertyId())
                .transactionType(agreement.getTransactionType())
                .ownerUserId(agreement.getOwnerUserId())
                .basisLeadId(agreement.getBasisLeadId())
                .status(agreement.getStatus())
                .createdByUserId(agreement.getCreatedByUserId())
                .createdAt(agreement.getCreatedAt())
                .updatedAt(agreement.getUpdatedAt())
                .version(agreement.getRowVersionOrNull())
                .endedAt(agreement.getEndedAtOrNull())
                .terminationType(termination == null ? null : termination.type())
                .terminationReportedOn(termination == null ? null : termination.reportedOnOrNull())
                .terminationChannel(termination == null ? null : termination.channelOrNull())
                .terminationIntroduced(termination == null ? null : termination.introducedOrNull())
                .terminationNote(termination == null ? null : termination.noteOrNull())
                .build();
        for (AgreementVersion version : agreement.getVersions()) {
            entity.getVersions().add(toEntity(version, entity));
        }
        return entity;
    }

    private static AgreementVersionEntity toEntity(AgreementVersion version, BrokerageAgreementEntity agreement) {
        VersionTerms terms = version.getTerms();
        TermsDeposit deposit = terms.depositOrNull();
        BrokerageFee fee = version.getFee();
        FeeFormula formula = fee.formulaOrNull();
        FeeFormula renewal = fee.renewalOrNull();
        EarlyLeaseEndRefund early = fee.earlyLeaseEndOrNull();
        OwnerAnswer answer = version.getOwnerAnswerOrNull();
        ActivationDecision decision = version.getActivationOrNull();
        SignedDocument document = decision == null ? null : decision.signedDocumentOrNull();
        AgreementVersionEntity entity = AgreementVersionEntity.builder()
                .id(version.getId())
                .agreement(agreement)
                .versionNo(version.getVersionNo())
                .status(version.getStatus())
                .startDate(version.getPeriod().startDate())
                .endDate(version.getPeriod().endDate())
                .basisLeadId(version.getBasisLeadId())
                .basisIntakeId(version.getBasisIntakeId())
                .createdByUserId(version.getCreatedByUserId())
                .createdAt(version.getCreatedAt())
                .updatedAt(version.getUpdatedAt())
                .photoConsent(version.getPhotoConsent().interiorOrNull())
                .exteriorPhotoConsent(version.getPhotoConsent().exterior())
                .monthlyRentAmount(decimal(terms.monthlyRentOrNull()))
                .depositBasis(deposit == null ? null : deposit.basis())
                .depositMonths(deposit == null ? null : deposit.monthsOrNull())
                .depositAmount(deposit == null ? null : decimal(deposit.amountOrNull()))
                .minimumTermMonths(terms.minimumTermMonthsOrNull())
                .paymentPeriodMonths(terms.paymentPeriodMonthsOrNull())
                .mandatoryFeesStated(terms.mandatoryFeesOrNull() != null)
                .feeFormulaType(formula == null ? null : formula.type())
                .feeFixedAmount(formula == null ? null : decimal(formula.amountOrNull()))
                .feeFreeReason(formula == null ? null : formula.freeReasonOrNull())
                .feeDueDays(fee.dueDaysOrNull())
                .renewalFeeType(renewal == null ? null : renewal.type())
                .renewalFeeFixedAmount(renewal == null ? null : decimal(renewal.amountOrNull()))
                .earlyLeaseEndType(early == null ? null : early.type())
                .earlyLeaseEndRefundPercent(early == null ? null : early.refundPercentOrNull())
                .earlyLeaseEndWithinMonths(early == null ? null : early.withinMonthsOrNull())
                .protectionDays(fee.protectionDaysOrNull())
                .sentAt(version.getSentAtOrNull())
                .ownerAnswer(answer == null ? null : answer.type())
                .ownerAnsweredBy(answer == null ? null : answer.userId())
                .ownerAnsweredAt(answer == null ? null : answer.answeredAt())
                .ownerStatementVersion(answer == null ? null : answer.statementVersionOrNull())
                .ownerRejectionReason(answer == null ? null : answer.rejectionReasonOrNull())
                .activationDecision(decision == null ? null : decision.type())
                .activationDecidedBy(decision == null ? null : decision.userId())
                .activationDecidedAt(decision == null ? null : decision.decidedAt())
                .activationStatementVersion(decision == null ? null : decision.statementVersionOrNull())
                .signedDocumentNumber(document == null ? null : document.number())
                .signedDocumentDate(document == null ? null : document.signedOn())
                .sunvibeSignatoryName(document == null ? null : document.sunvibeSignatoryName())
                .sunvibeSignatoryTitle(document == null ? null : document.sunvibeSignatoryTitle())
                .activationRefusalType(decision == null ? null : decision.refusalTypeOrNull())
                .activationRefusalNote(decision == null ? null : decision.refusalNoteOrNull())
                .build();
        List<MandatoryFee> fees = terms.mandatoryFeesOrNull() == null ? List.of() : terms.mandatoryFeesOrNull();
        for (int index = 0; index < fees.size(); index++) {
            MandatoryFee mandatoryFee = fees.get(index);
            entity.getFees().add(AgreementVersionFeeEntity.builder()
                    .id(new AgreementVersionFeeKey(version.getId(), index + 1))
                    .version(entity)
                    .name(mandatoryFee.name())
                    .basis(mandatoryFee.basis())
                    .amount(decimal(mandatoryFee.amountOrNull()))
                    .chargeUnit(mandatoryFee.chargeUnitOrNull())
                    .build());
        }
        addTiers(entity, false, formula);
        addTiers(entity, true, renewal);
        return entity;
    }

    /**
     * Tier i of the formula's list as the row numbered i (1-based), so a merge updates, inserts or removes the rows by
     * position, as the fee rows (E05-S4 spec S4-D4).
     */
    private static void addTiers(AgreementVersionEntity entity, boolean renewal, FeeFormula formulaOrNull) {
        List<FeeTier> tiers = formulaOrNull == null ? List.of() : formulaOrNull.tiers();
        for (int index = 0; index < tiers.size(); index++) {
            entity.getFeeTiers().add(AgreementVersionFeeTierEntity.builder()
                    .id(new AgreementVersionFeeTierKey(entity.getId(), renewal, index + 1))
                    .version(entity)
                    .fromMonths(tiers.get(index).fromMonths())
                    .percent(tiers.get(index).percent())
                    .build());
        }
    }

    /**
     * The aggregate of a stored agreement, its versions by number, their fees by number, always through the full load
     * with its end (E05-S8 spec 5.1, LD-1): a final row stored without its end loads with null in those fields.
     */
    public BrokerageAgreement toDomain(BrokerageAgreementEntity entity) {
        List<AgreementVersion> versions = entity.getVersions().stream().map(AgreementEntityMapper::toDomain).toList();
        return BrokerageAgreement.load(entity.getId(), entity.getUnitId(), entity.getUnitCode(),
                entity.getPropertyId(), entity.getTransactionType(), entity.getOwnerUserId(), entity.getBasisLeadId(),
                entity.getStatus(), entity.getCreatedByUserId(), entity.getCreatedAt(), entity.getUpdatedAt(),
                entity.getVersion(), versions, entity.getEndedAt(), termination(entity));
    }

    /**
     * The stored termination record; null when no termination column is set. A stored combination outside the rules of
     * E05-S8 spec S8-D5 is a data fault, never a request error; the message names no stored value (brokerage-agreement
     * BR-17).
     */
    private static AgreementTermination termination(BrokerageAgreementEntity entity) {
        if (entity.getTerminationType() == null && entity.getTerminationReportedOn() == null
                && entity.getTerminationChannel() == null && entity.getTerminationIntroduced() == null
                && entity.getTerminationNote() == null) {
            return null;
        }
        if (entity.getEndedAt() == null) {
            throw new IllegalStateException("A stored termination record has no end.");
        }
        try {
            return new AgreementTermination(entity.getTerminationType(), entity.getTerminationReportedOn(),
                    entity.getTerminationChannel(), entity.getTerminationIntroduced(), entity.getTerminationNote(),
                    entity.getEndedAt());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("A stored agreement breaks the termination rules.", e);
        }
    }

    private static AgreementVersion toDomain(AgreementVersionEntity version) {
        PhotoConsent consent;
        VersionTerms terms;
        BrokerageFee fee;
        try {
            consent = new PhotoConsent(version.getPhotoConsent(), version.isExteriorPhotoConsent());
            terms = new VersionTerms(exact(version.getMonthlyRentAmount()), deposit(version),
                    version.getMinimumTermMonths(), version.getPaymentPeriodMonths(), fees(version));
            fee = new BrokerageFee(formula(version.getFeeFormulaType(), tiers(version, false),
                    version.getFeeFixedAmount(), version.getFeeFreeReason()), version.getFeeDueDays(),
                    formula(version.getRenewalFeeType(), tiers(version, true), version.getRenewalFeeFixedAmount(), null),
                    earlyLeaseEnd(version), version.getProtectionDays());
        } catch (AgreementContentException | ArithmeticException e) {
            // a data fault, never a request error (SV-312 (6)); the message names no stored value
            throw new IllegalStateException("A stored agreement version breaks its content rules.", e);
        }
        return AgreementVersion.load(version.getId(), version.getVersionNo(), version.getStatus(),
                new AgreementPeriod(version.getStartDate(), version.getEndDate()), version.getBasisLeadId(),
                version.getBasisIntakeId(), version.getCreatedByUserId(), version.getCreatedAt(),
                version.getUpdatedAt(), consent, terms, fee, version.getSentAt(), ownerAnswer(version),
                activation(version));
    }

    /**
     * The stored activation decision; null when no activation column is set. A stored combination outside the rules of
     * E05-S6 spec S6-D14 is a data fault, never a request error; the message names no stored value (brokerage-agreement
     * BR-17).
     */
    private static ActivationDecision activation(AgreementVersionEntity version) {
        boolean documentStored = version.getSignedDocumentNumber() != null || version.getSignedDocumentDate() != null
                || version.getSunvibeSignatoryName() != null || version.getSunvibeSignatoryTitle() != null;
        if (version.getActivationDecision() == null && version.getActivationDecidedBy() == null
                && version.getActivationDecidedAt() == null && version.getActivationStatementVersion() == null
                && !documentStored && version.getActivationRefusalType() == null
                && version.getActivationRefusalNote() == null) {
            return null;
        }
        try {
            SignedDocument document = documentStored ? new SignedDocument(version.getSignedDocumentNumber(),
                    version.getSignedDocumentDate(), version.getSunvibeSignatoryName(),
                    version.getSunvibeSignatoryTitle()) : null;
            return new ActivationDecision(version.getActivationDecision(), version.getActivationDecidedBy(),
                    version.getActivationDecidedAt(), version.getActivationStatementVersion(), document,
                    version.getActivationRefusalType(), version.getActivationRefusalNote());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("A stored agreement version breaks the activation rules.", e);
        }
    }

    /**
     * The stored owner answer; null when no answer column is set. A stored combination outside the rules of E05-S5 spec
     * S5-D11 is a data fault, never a request error; the message names no stored value (brokerage-agreement BR-17).
     */
    private static OwnerAnswer ownerAnswer(AgreementVersionEntity version) {
        if (version.getOwnerAnswer() == null && version.getOwnerAnsweredBy() == null
                && version.getOwnerAnsweredAt() == null && version.getOwnerStatementVersion() == null
                && version.getOwnerRejectionReason() == null) {
            return null;
        }
        try {
            return new OwnerAnswer(version.getOwnerAnswer(), version.getOwnerAnsweredBy(), version.getOwnerAnsweredAt(),
                    version.getOwnerStatementVersion(), version.getOwnerRejectionReason());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("A stored agreement version breaks the owner answer rules.", e);
        }
    }

    /**
     * The fee or renewal formula of the stored columns and tier rows; null when no type is stored, which a stored
     * amount, reason or tier row beside it makes a data fault.
     */
    private static FeeFormula formula(FeeFormulaType typeOrNull, List<FeeTier> tiers, BigDecimal amountOrNull,
                                      String freeReasonOrNull) {
        if (typeOrNull == null) {
            if (!tiers.isEmpty() || amountOrNull != null || freeReasonOrNull != null) {
                throw new IllegalStateException("A stored agreement version holds a fee part without its type.");
            }
            return null;
        }
        return FeeFormula.of(typeOrNull, tiers, exact(amountOrNull), freeReasonOrNull);
    }

    /** The fee's or the renewal's tier rows in their order; numbers other than 1 to n are a data fault. */
    private static List<FeeTier> tiers(AgreementVersionEntity version, boolean renewal) {
        List<AgreementVersionFeeTierEntity> rows = version.getFeeTiers().stream()
                .filter(row -> row.getId().isRenewal() == renewal)
                .sorted(Comparator.comparingInt(row -> row.getId().getTierNo()))
                .toList();
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index).getId().getTierNo() != index + 1) {
                throw new IllegalStateException("A stored agreement version numbers its fee tiers out of order.");
            }
        }
        return rows.stream().map(row -> new FeeTier(row.getFromMonths(), row.getPercent())).toList();
    }

    private static EarlyLeaseEndRefund earlyLeaseEnd(AgreementVersionEntity version) {
        if (version.getEarlyLeaseEndType() == null && version.getEarlyLeaseEndRefundPercent() == null
                && version.getEarlyLeaseEndWithinMonths() == null) {
            return null;
        }
        return new EarlyLeaseEndRefund(version.getEarlyLeaseEndType(), version.getEarlyLeaseEndRefundPercent(),
                version.getEarlyLeaseEndWithinMonths());
    }

    private static TermsDeposit deposit(AgreementVersionEntity version) {
        if (version.getDepositBasis() == null && version.getDepositMonths() == null
                && version.getDepositAmount() == null) {
            return null;
        }
        return new TermsDeposit(version.getDepositBasis(), version.getDepositMonths(),
                exact(version.getDepositAmount()));
    }

    /** null = not stated yet; a stated list without a row is "no mandatory fee besides the rent". */
    private static List<MandatoryFee> fees(AgreementVersionEntity version) {
        if (!version.isMandatoryFeesStated()) {
            if (!version.getFees().isEmpty()) {
                throw new IllegalStateException("A stored agreement version holds fees it does not state.");
            }
            return null;
        }
        return version.getFees().stream()
                .map(fee -> new MandatoryFee(fee.getName(), fee.getBasis(), exact(fee.getAmount()), fee.getChargeUnit()))
                .toList();
    }

    private static BigDecimal decimal(Long amountOrNull) {
        return amountOrNull == null ? null : BigDecimal.valueOf(amountOrNull);
    }

    /** ADR-006: a stored amount is whole VND; a fraction or an overflow is a data fault, never rounded. */
    private static Long exact(BigDecimal amountOrNull) {
        return amountOrNull == null ? null : amountOrNull.longValueExact();
    }
}
