package vn.sunvibe.listing.core.infrastructure.persistence.mapper.manual;

import org.springframework.stereotype.Component;
import vn.sunvibe.listing.core.domain.model.listing.ChangeReason;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessRecord;
import vn.sunvibe.listing.core.domain.model.listing.Listing;
import vn.sunvibe.listing.core.domain.model.listing.ListingAccessLog;
import vn.sunvibe.listing.core.domain.model.listing.ListingCode;
import vn.sunvibe.listing.core.domain.model.listing.ListingHistoryEntry;
import vn.sunvibe.listing.core.domain.model.listing.ListingRevision;
import vn.sunvibe.listing.core.domain.model.listing.ReviewNote;
import vn.sunvibe.listing.core.domain.model.listing.RevisionContent;
import vn.sunvibe.listing.core.domain.model.listing.RevisionDecision;
import vn.sunvibe.listing.core.domain.model.listing.RevisionFeatures;
import vn.sunvibe.listing.core.domain.model.listing.RevisionTerms;
import vn.sunvibe.listing.core.domain.model.listing.UnitSnapshot;
import vn.sunvibe.listing.core.domain.repository.ListingRow;
import vn.sunvibe.listing.core.domain.repository.ReviewCandidate;
import vn.sunvibe.listing.core.infrastructure.persistence.entity.ListingAccessLogEntity;
import vn.sunvibe.listing.core.infrastructure.persistence.entity.ListingEntity;
import vn.sunvibe.listing.core.infrastructure.persistence.entity.ListingFreshnessRecordEntity;
import vn.sunvibe.listing.core.infrastructure.persistence.entity.ListingHistoryEntity;
import vn.sunvibe.listing.core.infrastructure.persistence.entity.ListingRevisionEntity;
import vn.sunvibe.listing.core.infrastructure.persistence.entity.ListingRevisionFeeEntity;
import vn.sunvibe.listing.core.infrastructure.persistence.entity.ListingRevisionFeeKey;
import vn.sunvibe.listing.core.infrastructure.persistence.entity.ListingRevisionReviewEntity;
import vn.sunvibe.listing.core.infrastructure.persistence.repository.ListingRevisionJpaRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manual Mapper - a listing to its tables' entities, and rows back to domain values through the domain's own factories
 * ({@link ListingRevision#load}, {@link Listing#loadForCommand}, {@link ListingCode#of}); no reflection. A revision maps
 * every column both ways (listing-publish E06-S2 spec E06S2-D19), so a write by id keeps no field unchanged by mistake.
 */
@Component
public class ListingEntityMapper {

    /** listing-publish E06-S4 spec 5.3: the codes of a decision's reasons, joined. */
    private static final String REASON_SEPARATOR = ",";

    /** The listing's own row of a new listing: a null row version, so the insert is a persist (E06S1-P13). */
    public ListingEntity toNewEntity(Listing listing) {
        return ListingEntity.builder()
                .id(listing.id())
                .code(listing.code().value())
                .unitId(listing.unitId())
                .transactionType(listing.transactionType())
                .agreementId(listing.agreementId())
                .state(listing.state())
                .createdBy(listing.createdBy())
                .createdAt(listing.createdAt())
                .updatedAt(listing.updatedAt())
                .rowVersion(null)
                .build();
    }

    public List<ListingRevisionEntity> toRevisionEntities(Listing listing) {
        return listing.revisions().stream().map(revision -> toRevisionEntity(listing.id(), revision)).toList();
    }

    /** Every column of a revision in its full form; its fees are {@link #toFeeEntities}. */
    public ListingRevisionEntity toRevisionEntity(String listingId, ListingRevision revision) {
        RevisionContent content = revision.content();
        RevisionFeatures features = content.features();
        RevisionTerms terms = revision.termsOrNull();
        UnitSnapshot unit = revision.unitSnapshotOrNull();
        ListingRevisionEntity.ListingRevisionEntityBuilder builder = ListingRevisionEntity.builder()
                .id(revision.id())
                .listingId(listingId)
                .revisionNo(revision.revisionNo())
                .status(revision.status())
                .createdBy(revision.createdBy())
                .createdAt(revision.createdAt())
                .updatedAt(revision.updatedAt())
                .title(content.titleOrNull())
                .description(content.descriptionOrNull())
                .featureFurnishing(features.furnishingOrNull())
                .featureAirConditioner(features.airConditioner())
                .featureWaterHeater(features.waterHeater())
                .featureFridge(features.fridge())
                .featureWashingMachine(features.washingMachineOrNull())
                .featureKitchen(features.kitchenOrNull())
                .featureBalcony(features.balcony())
                .featureElevator(features.elevator())
                .featureMotorbikeParking(features.motorbikeParking())
                .featureCarParking(features.carParking())
                .featureInternet(features.internet())
                .featurePets(features.petsOrNull())
                .featureNoSharedLandlord(features.noSharedLandlord())
                .submittedAt(revision.submittedAtOrNull())
                .submittedBy(revision.submittedByOrNull())
                .copiedFromRevisionNo(revision.copiedFromRevisionNoOrNull())
                .wentLiveAt(revision.wentLiveAtOrNull())
                .replacedAt(revision.replacedAtOrNull());
        if (terms != null) {
            builder.termsSource(terms.source())
                    .termsPendingStartDate(terms.pendingStartDateOrNull())
                    .termsCapturedAt(terms.capturedAt())
                    .termsMonthlyRent(BigDecimal.valueOf(terms.monthlyRent()))
                    .termsDepositBasis(terms.deposit().basis())
                    .termsDepositMonths(terms.deposit().monthsOrNull())
                    .termsDepositAmount(terms.deposit().amountOrNull() == null ? null
                            : BigDecimal.valueOf(terms.deposit().amountOrNull()))
                    .termsMinimumTermMonths(terms.minimumTermMonths())
                    .termsPaymentPeriodMonths(terms.paymentPeriodMonths());
        }
        if (unit != null) {
            builder.unitAssetType(unit.assetTypeOrNull())
                    .unitProvinceCode(unit.provinceCodeOrNull())
                    .unitProvinceName(unit.provinceNameOrNull())
                    .unitWardCode(unit.wardCodeOrNull())
                    .unitWardName(unit.wardNameOrNull())
                    .unitBedrooms(unit.bedrooms())
                    .unitBathrooms(unit.bathrooms())
                    .unitAreaSqm(unit.areaSqm());
        }
        return builder.build();
    }

    /** The fee rows of a revision's terms snapshot, numbered 1 to n in E05's order; none without a snapshot. */
    public List<ListingRevisionFeeEntity> toFeeEntities(ListingRevision revision) {
        RevisionTerms terms = revision.termsOrNull();
        if (terms == null) {
            return List.of();
        }
        List<ListingRevisionFeeEntity> rows = new ArrayList<>();
        for (int index = 0; index < terms.fees().size(); index++) {
            RevisionTerms.Fee fee = terms.fees().get(index);
            rows.add(ListingRevisionFeeEntity.builder()
                    .id(new ListingRevisionFeeKey(revision.id(), index + 1))
                    .name(fee.name())
                    .basis(fee.basis())
                    .amount(fee.amountOrNull() == null ? null : BigDecimal.valueOf(fee.amountOrNull()))
                    .chargeUnit(fee.chargeUnitOrNull())
                    .build());
        }
        return rows;
    }

    public List<ListingHistoryEntity> toHistoryEntities(Listing listing) {
        return listing.pendingHistory().stream().map(entry -> toHistoryEntity(listing.id(), entry)).toList();
    }

    public ListingRow toRow(ListingEntity entity) {
        return new ListingRow(entity.getId(), ListingCode.of(entity.getCode()), entity.getUnitId(),
                entity.getTransactionType(), entity.getAgreementId(), entity.getState(), entity.getCreatedBy(),
                entity.getCreatedAt(), entity.getRowVersion() == null ? 0L : entity.getRowVersion());
    }

    /** The header form of the newest-revisions read: no content, no fee row. */
    public ListingRevision toRevision(ListingRevisionEntity entity) {
        return ListingRevision.load(entity.getId(), entity.getRevisionNo(), entity.getStatus(), entity.getCreatedBy(),
                entity.getCreatedAt(), entity.getUpdatedAt(), entity.getWentLiveAt(), entity.getReplacedAt());
    }

    /**
     * The full form of a revision. Its terms come from the row's own terms columns only (listing-publish E06-S2 plan
     * E06S2-P16): without a terms source the fee rows given are ignored, whatever a concurrent capture left visible;
     * with one, the fee rows given are its fees in fee number order.
     */
    public ListingRevision toFullRevision(ListingRevisionEntity entity, List<ListingRevisionFeeEntity> feeRows) {
        RevisionFeatures features = new RevisionFeatures(entity.getFeatureFurnishing(),
                entity.isFeatureAirConditioner(), entity.isFeatureWaterHeater(), entity.isFeatureFridge(),
                entity.getFeatureWashingMachine(), entity.getFeatureKitchen(), entity.isFeatureBalcony(),
                entity.isFeatureElevator(), entity.isFeatureMotorbikeParking(), entity.isFeatureCarParking(),
                entity.isFeatureInternet(), entity.getFeaturePets(), entity.isFeatureNoSharedLandlord());
        RevisionContent content = new RevisionContent(entity.getTitle(), entity.getDescription(), features);
        return ListingRevision.load(entity.getId(), entity.getRevisionNo(), entity.getStatus(), entity.getCreatedBy(),
                entity.getCreatedAt(), entity.getUpdatedAt(), content, terms(entity, feeRows), unit(entity),
                entity.getSubmittedAt(), entity.getSubmittedBy(), entity.getCopiedFromRevisionNo(), entity.getWentLiveAt(),
                entity.getReplacedAt());
    }

    /**
     * A decision as its row (listing-publish E06-S4 spec E06S4-D8): the reasons as their codes joined by commas in
     * {@link ChangeReason} order, none for an outcome without reasons.
     */
    public ListingRevisionReviewEntity toReviewEntity(String listingId, RevisionDecision decision) {
        return ListingRevisionReviewEntity.builder()
                .revisionId(decision.revisionId())
                .listingId(listingId)
                .outcome(decision.outcome())
                .checklistVersion(decision.checklistVersionOrNull() == null ? null
                        : decision.checklistVersionOrNull().shortValue())
                .changeReasons(decision.changeReasons().isEmpty() ? null : decision.changeReasons().stream()
                        .map(Enum::name)
                        .collect(Collectors.joining(REASON_SEPARATOR)))
                .rejectionGroup(decision.rejectionGroupOrNull())
                .note(decision.noteOrNull() == null ? null : decision.noteOrNull().text())
                .decidedBy(decision.decidedBy())
                .decidedAt(decision.decidedAt())
                .build();
    }

    /** A stored decision of the revision of that number; the record checks its shape again. */
    public RevisionDecision toDecision(ListingRevisionReviewEntity entity, int revisionNo) {
        List<ChangeReason> reasons = entity.getChangeReasons() == null ? List.of()
                : Arrays.stream(entity.getChangeReasons().split(REASON_SEPARATOR)).map(ChangeReason::valueOf).toList();
        return new RevisionDecision(entity.getRevisionId(), revisionNo, entity.getOutcome(), entity.getDecidedBy(),
                entity.getDecidedAt(), entity.getChecklistVersion() == null ? null : entity.getChecklistVersion().intValue(),
                reasons, entity.getRejectionGroup(), entity.getNote() == null ? null : new ReviewNote(entity.getNote()));
    }

    /** A candidate of the review queue (listing-publish E06-S4 plan E06S4-P5), its unit snapshot as stored. */
    public ReviewCandidate toCandidate(ListingRevisionJpaRepository.ReviewRow row) {
        UnitSnapshot unit = row.getUnitBedrooms() == null ? null
                : new UnitSnapshot(row.getUnitAssetType(), row.getUnitProvinceCode(), row.getUnitProvinceName(),
                row.getUnitWardCode(), row.getUnitWardName(), row.getUnitBedrooms(), row.getUnitBathrooms(),
                row.getUnitAreaSqm());
        return new ReviewCandidate(row.getListingId(), row.getCode(), row.getAgreementId(), row.getId(),
                row.getRevisionNo(), row.getSubmittedAt(), row.getTitle(), unit);
    }

    /** An access log as its row; it holds no value of what was opened (E06S4-D12). */
    public ListingAccessLogEntity toAccessLogEntity(ListingAccessLog log) {
        return ListingAccessLogEntity.builder()
                .id(log.id())
                .userId(log.userId())
                .listingId(log.listingId())
                .revisionNo(log.revisionNo())
                .action(log.action())
                .accessedAt(log.accessedAt())
                .build();
    }

    private static RevisionTerms terms(ListingRevisionEntity entity, List<ListingRevisionFeeEntity> feeRows) {
        if (entity.getTermsSource() == null) {
            return null;
        }
        List<RevisionTerms.Fee> fees = feeRows.stream()
                .sorted(Comparator.comparingInt(row -> row.getId().getFeeNo()))
                .map(row -> new RevisionTerms.Fee(row.getName(), row.getBasis(), wholeOrNull(row.getAmount()),
                        row.getChargeUnit()))
                .toList();
        return new RevisionTerms(entity.getTermsSource(), entity.getTermsPendingStartDate(), entity.getTermsCapturedAt(),
                entity.getTermsMonthlyRent().longValueExact(),
                new RevisionTerms.Deposit(entity.getTermsDepositBasis(), entity.getTermsDepositMonths(),
                        wholeOrNull(entity.getTermsDepositAmount())),
                entity.getTermsMinimumTermMonths(), entity.getTermsPaymentPeriodMonths(), fees);
    }

    private static UnitSnapshot unit(ListingRevisionEntity entity) {
        if (entity.getUnitBedrooms() == null) {
            return null;
        }
        return new UnitSnapshot(entity.getUnitAssetType(), entity.getUnitProvinceCode(), entity.getUnitProvinceName(),
                entity.getUnitWardCode(), entity.getUnitWardName(), entity.getUnitBedrooms(), entity.getUnitBathrooms(),
                entity.getUnitAreaSqm());
    }

    private static Long wholeOrNull(BigDecimal amountOrNull) {
        return amountOrNull == null ? null : amountOrNull.longValueExact();
    }

    /** A freshness record as its row; every column, the note included (listing-publish E06-S3 spec E06S3-D6). */
    public ListingFreshnessRecordEntity toFreshnessRecordEntity(FreshnessRecord record) {
        return ListingFreshnessRecordEntity.builder()
                .id(record.id())
                .listingId(record.listingId())
                .listingRowVersion(record.listingRowVersion())
                .contactDate(record.contactDate())
                .recordedAt(record.recordedAt())
                .recordedBy(record.recordedBy())
                .channel(record.channel())
                .outcome(record.outcome())
                .termsStillApply(record.termsStillApplyOrNull())
                .unitUnchanged(record.unitUnchangedOrNull())
                .valid(record.valid())
                .note(record.noteOrNull())
                .build();
    }

    /** A stored freshness record; the record checks its answers and note again, and derives its validity. */
    public FreshnessRecord toFreshnessRecord(ListingFreshnessRecordEntity entity) {
        return new FreshnessRecord(entity.getId(), entity.getListingId(), entity.getListingRowVersion(),
                entity.getContactDate(), entity.getRecordedAt(), entity.getRecordedBy(), entity.getChannel(),
                entity.getOutcome(), entity.getTermsStillApply(), entity.getUnitUnchanged(), entity.getNote());
    }

    public static ListingHistoryEntity toHistoryEntity(String listingId, ListingHistoryEntry entry) {
        return ListingHistoryEntity.builder()
                .id(entry.id())
                .listingId(listingId)
                .action(entry.action())
                .revisionNo(entry.revisionNoOrNull())
                .actorUserId(entry.actorIdOrNull())
                .occurredAt(entry.occurredAt())
                .build();
    }
}
