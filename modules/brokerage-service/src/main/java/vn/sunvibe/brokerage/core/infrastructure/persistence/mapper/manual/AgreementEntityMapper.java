package vn.sunvibe.brokerage.core.infrastructure.persistence.mapper.manual;

import org.springframework.stereotype.Component;
import vn.sunvibe.brokerage.core.domain.exception.AgreementContentException;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementPeriod;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementVersion;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageAgreement;
import vn.sunvibe.brokerage.core.domain.model.agreement.MandatoryFee;
import vn.sunvibe.brokerage.core.domain.model.agreement.PhotoConsent;
import vn.sunvibe.brokerage.core.domain.model.agreement.TermsDeposit;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionTerms;
import vn.sunvibe.brokerage.core.infrastructure.persistence.entity.AgreementVersionEntity;
import vn.sunvibe.brokerage.core.infrastructure.persistence.entity.AgreementVersionFeeEntity;
import vn.sunvibe.brokerage.core.infrastructure.persistence.entity.AgreementVersionFeeKey;
import vn.sunvibe.brokerage.core.infrastructure.persistence.entity.BrokerageAgreementEntity;

import java.math.BigDecimal;
import java.util.List;

/** Mapper - a brokerage agreement, its versions and their fee rows, both ways, every child mapped. */
@Component
public class AgreementEntityMapper {

    /**
     * The entity of the aggregate with every version and fee row. A new aggregate has no row version, so Spring Data
     * persists it; a loaded one keeps the version it was read with, so a merge checks it.
     */
    public BrokerageAgreementEntity toEntity(BrokerageAgreement agreement) {
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
                .build();
        for (AgreementVersion version : agreement.getVersions()) {
            entity.getVersions().add(toEntity(version, entity));
        }
        return entity;
    }

    private static AgreementVersionEntity toEntity(AgreementVersion version, BrokerageAgreementEntity agreement) {
        VersionTerms terms = version.getTerms();
        TermsDeposit deposit = terms.depositOrNull();
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
                .build();
        List<MandatoryFee> fees = terms.mandatoryFeesOrNull() == null ? List.of() : terms.mandatoryFeesOrNull();
        for (int index = 0; index < fees.size(); index++) {
            MandatoryFee fee = fees.get(index);
            entity.getFees().add(AgreementVersionFeeEntity.builder()
                    .id(new AgreementVersionFeeKey(version.getId(), index + 1))
                    .version(entity)
                    .name(fee.name())
                    .basis(fee.basis())
                    .amount(decimal(fee.amountOrNull()))
                    .chargeUnit(fee.chargeUnitOrNull())
                    .build());
        }
        return entity;
    }

    /** The aggregate of a stored agreement, its versions by number, their fees by number. */
    public BrokerageAgreement toDomain(BrokerageAgreementEntity entity) {
        List<AgreementVersion> versions = entity.getVersions().stream().map(AgreementEntityMapper::toDomain).toList();
        return BrokerageAgreement.load(entity.getId(), entity.getUnitId(), entity.getUnitCode(),
                entity.getPropertyId(), entity.getTransactionType(), entity.getOwnerUserId(), entity.getBasisLeadId(),
                entity.getStatus(), entity.getCreatedByUserId(), entity.getCreatedAt(), entity.getUpdatedAt(),
                entity.getVersion(), versions);
    }

    private static AgreementVersion toDomain(AgreementVersionEntity version) {
        PhotoConsent consent;
        VersionTerms terms;
        try {
            consent = new PhotoConsent(version.getPhotoConsent(), version.isExteriorPhotoConsent());
            terms = new VersionTerms(exact(version.getMonthlyRentAmount()), deposit(version),
                    version.getMinimumTermMonths(), version.getPaymentPeriodMonths(), fees(version));
        } catch (AgreementContentException | ArithmeticException e) {
            // a data fault, never a request error (SV-312 (6)); the message names no stored value
            throw new IllegalStateException("A stored agreement version breaks its content rules.", e);
        }
        return AgreementVersion.load(version.getId(), version.getVersionNo(), version.getStatus(),
                new AgreementPeriod(version.getStartDate(), version.getEndDate()), version.getBasisLeadId(),
                version.getBasisIntakeId(), version.getCreatedByUserId(), version.getCreatedAt(),
                version.getUpdatedAt(), consent, terms);
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
