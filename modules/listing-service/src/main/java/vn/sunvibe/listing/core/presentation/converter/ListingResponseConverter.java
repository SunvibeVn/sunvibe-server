package vn.sunvibe.listing.core.presentation.converter;

import org.springframework.stereotype.Component;
import vn.sunvibe.listing.core.application.query.AgreementListingsView;
import vn.sunvibe.listing.core.application.query.FreshnessRecordResult;
import vn.sunvibe.listing.core.application.query.ListingCreateResult;
import vn.sunvibe.listing.core.application.query.ListingFreshnessView;
import vn.sunvibe.listing.core.application.query.ListingRevisionView;
import vn.sunvibe.listing.core.application.query.ListingView;
import vn.sunvibe.listing.core.application.query.ReconfirmationListView;
import vn.sunvibe.listing.core.application.query.ReviewQueueView;
import vn.sunvibe.listing.core.application.query.RevisionCommandResult;
import vn.sunvibe.listing.core.application.query.RevisionDecisionView;
import vn.sunvibe.listing.core.application.query.RevisionReviewView;
import vn.sunvibe.listing.core.application.query.TermsSourcesView;
import vn.sunvibe.listing.core.application.query.UnitLocationResult;
import vn.sunvibe.listing.core.domain.model.listing.RevisionFeatures;
import vn.sunvibe.listing.core.domain.model.listing.RevisionTerms;
import vn.sunvibe.listing.core.domain.model.listing.UnitSnapshot;
import vn.sunvibe.listing.core.presentation.dto.response.AgreementListingsResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingCreatedResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingCreationResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingDepositResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingDetailResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingFeaturesResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingFeeResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingFreshnessRecordResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingFreshnessRecordedResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingFreshnessResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingItemResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingPendingTermsResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingReconfirmationItemResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingReconfirmationsResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingRentResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingReviewAdvertisingResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingReviewFreshnessResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingReviewQueueItemResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingReviewQueueResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingReviewUnitResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingRevisionChangeResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingRevisionDecisionResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingRevisionDetailResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingRevisionItemResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingRevisionProblemResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingRevisionReviewResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingTermsResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingTermsSnapshotResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingTermsSourcesResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingUnitLocationResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingUnitSnapshotResponse;

/** Result and views to response DTOs, the codes as strings: a pure function of its arguments. */
@Component
public class ListingResponseConverter {

    /** ADR-006: whole VND. */
    private static final String VND = "VND";
    /** listing BR-7: the rent always carries the month. */
    private static final String MONTH = "MONTH";

    public ListingCreatedResponse toCreated(ListingCreateResult result) {
        return new ListingCreatedResponse(result.listingId(), result.code());
    }

    public ListingDetailResponse toDetail(ListingView view) {
        return new ListingDetailResponse(view.listingId(), view.code(), view.state().name(),
                view.transactionType().code(), view.unitId(), view.agreementId(), view.createdAt(), view.dataVersion(),
                view.viewerIsAssignedSale(),
                view.revisions().stream()
                        .map(revision -> new ListingRevisionItemResponse(revision.revisionNo(),
                                revision.status().name(), revision.createdAt()))
                        .toList(),
                view.hasOlderRevisions(), view.openRevisionNoOrNull());
    }

    public AgreementListingsResponse toAgreementListings(AgreementListingsView view) {
        AgreementListingsView.Creation creation = view.creation();
        return new AgreementListingsResponse(view.agreementId(),
                view.listings().stream()
                        .map(item -> new ListingItemResponse(item.listingId(), item.code(), item.state().name(),
                                item.createdAt()))
                        .toList(),
                view.hasOlder(),
                new ListingCreationResponse(creation.viewerMayCreate(), creation.advertisingNotStarted(),
                        creation.reasonGroups().stream().map(Enum::name).toList()));
    }

    public ListingRevisionChangeResponse toChange(RevisionCommandResult result) {
        return new ListingRevisionChangeResponse(result.listingId(), result.revisionNo(), result.status().name(),
                result.dataVersion());
    }

    public ListingRevisionDetailResponse toRevisionDetail(ListingRevisionView view) {
        RevisionTerms terms = view.termsOrNull();
        return new ListingRevisionDetailResponse(view.listingId(), view.revisionNo(), view.status().name(),
                view.createdAt(), view.updatedAt(), view.content().titleOrNull(), view.content().descriptionOrNull(),
                toFeatures(view.content().features()),
                terms == null ? null : new ListingTermsSnapshotResponse(terms.source().name(),
                        terms.pendingStartDateOrNull(), terms.capturedAt(), toTerms(terms)),
                toUnitSnapshot(view.unitSnapshotOrNull()), view.submittedAtOrNull(), view.dataVersion(),
                view.problems().stream()
                        .map(problem -> new ListingRevisionProblemResponse(problem.part().name(), problem.rule().name()))
                        .toList());
    }

    public ListingTermsSourcesResponse toTermsSources(TermsSourcesView view) {
        RevisionTerms pending = view.pendingOrNull();
        return new ListingTermsSourcesResponse(view.ended(),
                view.inEffectOrNull() == null ? null : toTerms(view.inEffectOrNull()),
                pending == null ? null : new ListingPendingTermsResponse(pending.pendingStartDateOrNull(),
                        toTerms(pending)));
    }

    /** The rent in ADR-006's shape, the deposit and the fees in E05's flat forms, the fees in E05's order. */
    public ListingTermsResponse toTerms(RevisionTerms terms) {
        RevisionTerms.Deposit deposit = terms.deposit();
        return new ListingTermsResponse(new ListingRentResponse(terms.monthlyRent(), VND, MONTH),
                new ListingDepositResponse(deposit.basis().name(), deposit.monthsOrNull(), deposit.amountOrNull(),
                        deposit.amountOrNull() == null ? null : VND),
                terms.minimumTermMonths(), terms.paymentPeriodMonths(),
                terms.fees().stream()
                        .map(fee -> new ListingFeeResponse(fee.name(), fee.basis().name(), fee.amountOrNull(),
                                fee.amountOrNull() == null ? null : VND,
                                fee.chargeUnitOrNull() == null ? null : fee.chargeUnitOrNull().name()))
                        .toList());
    }

    public ListingFreshnessRecordedResponse toFreshnessRecorded(FreshnessRecordResult result) {
        return new ListingFreshnessRecordedResponse(result.listingId(), result.dataVersion(), result.valid());
    }

    /** The freshness read with its latest record, the note included; no user id (E06S3-D8). */
    public ListingFreshnessResponse toFreshness(ListingFreshnessView view) {
        ListingFreshnessView.LatestRecord latest = view.latestOrNull();
        return new ListingFreshnessResponse(view.listingId(), view.confirmedOnOrNull(), view.reconfirmFromOrNull(),
                view.validThroughOrNull(), view.reconfirmationDue(), view.deadlinePassed(),
                latest == null ? null : new ListingFreshnessRecordResponse(latest.contactDate(), latest.recordedAt(),
                        latest.channel().name(), latest.outcome().name(), latest.termsStillApplyOrNull(),
                        latest.unitUnchangedOrNull(), latest.valid(), latest.noteOrNull()));
    }

    public ListingReconfirmationsResponse toReconfirmations(ReconfirmationListView view) {
        return new ListingReconfirmationsResponse(
                view.items().stream()
                        .map(item -> new ListingReconfirmationItemResponse(item.listingId(), item.code(),
                                item.state().name(), item.confirmedOn(), item.reconfirmFrom(), item.validThrough(),
                                item.deadlinePassed(), item.latestRecordValid()))
                        .toList(),
                view.hasMore());
    }

    /** The queue; each row's unit in the revision read's snapshot schema (E06S4-D10). */
    public ListingReviewQueueResponse toReviewQueue(ReviewQueueView view) {
        return new ListingReviewQueueResponse(
                view.items().stream()
                        .map(item -> new ListingReviewQueueItemResponse(item.listingId(), item.code(),
                                item.revisionNo(), item.submittedAt(), item.title(), toUnitSnapshot(item.unitOrNull()),
                                item.viewerMayReview(), item.reasons().stream().map(Enum::name).toList()))
                        .toList(),
                view.hasMore());
    }

    /** The review read; E05's terms in the schemas of the terms sources (E06S4-D11). */
    public ListingRevisionReviewResponse toRevisionReview(RevisionReviewView view) {
        RevisionReviewView.Advertising advertising = view.advertisingOrNull();
        RevisionReviewView.Unit unit = view.unit();
        RevisionReviewView.Freshness freshness = view.freshness();
        return new ListingRevisionReviewResponse(view.listingId(), view.code(), view.revisionNo(), view.status().name(),
                view.dataVersion(), view.viewerMayReview(), view.reasons().stream().map(Enum::name).toList(),
                advertising == null ? null : toAdvertising(advertising), view.termsMatch().name(),
                new ListingReviewUnitResponse(unit.unitCodeOrNull(), nameOrNull(unit.availabilityOrNull()),
                        unit.availabilityDiffers()),
                new ListingReviewFreshnessResponse(freshness.confirmedOnOrNull(), freshness.validThroughOrNull(),
                        freshness.deadlinePassed(), freshness.latestValidOrNull()),
                view.checklist().stream().map(Enum::name).toList());
    }

    /** The decision read; no decider (E06S4-D15). */
    public ListingRevisionDecisionResponse toRevisionDecision(RevisionDecisionView view) {
        return new ListingRevisionDecisionResponse(view.outcome().name(), view.decidedAt(),
                view.checklistVersionOrNull(), view.changeReasons().stream().map(Enum::name).toList(),
                nameOrNull(view.rejectionGroupOrNull()), view.noteOrNull());
    }

    public ListingUnitLocationResponse toUnitLocation(UnitLocationResult result) {
        return new ListingUnitLocationResponse(result.unitCodeOrNull(), result.locationLabelOrNull(),
                result.streetOrNull(), result.wardNameOrNull());
    }

    private ListingReviewAdvertisingResponse toAdvertising(RevisionReviewView.Advertising advertising) {
        RevisionTerms pending = advertising.pendingOrNull();
        return new ListingReviewAdvertisingResponse(advertising.allowed(), advertising.ended(),
                advertising.reasonGroups().stream().map(Enum::name).toList(), advertising.endDateOrNull(),
                advertising.interiorPhotos(), advertising.exteriorPhotos(),
                advertising.termsInEffectOrNull() == null ? null : toTerms(advertising.termsInEffectOrNull()),
                pending == null ? null : new ListingPendingTermsResponse(pending.pendingStartDateOrNull(),
                        toTerms(pending)));
    }

    private static ListingFeaturesResponse toFeatures(RevisionFeatures features) {
        return new ListingFeaturesResponse(nameOrNull(features.furnishingOrNull()), features.airConditioner(),
                features.waterHeater(), features.fridge(), nameOrNull(features.washingMachineOrNull()),
                nameOrNull(features.kitchenOrNull()), features.balcony(), features.elevator(),
                features.motorbikeParking(), features.carParking(), features.internet(),
                nameOrNull(features.petsOrNull()), features.noSharedLandlord());
    }

    private static ListingUnitSnapshotResponse toUnitSnapshot(UnitSnapshot unit) {
        if (unit == null) {
            return null;
        }
        return new ListingUnitSnapshotResponse(unit.assetTypeOrNull(), unit.provinceCodeOrNull(),
                unit.provinceNameOrNull(), unit.wardCodeOrNull(), unit.wardNameOrNull(), unit.bedrooms(),
                unit.bathrooms(), unit.areaSqm());
    }

    private static String nameOrNull(Enum<?> valueOrNull) {
        return valueOrNull == null ? null : valueOrNull.name();
    }
}
