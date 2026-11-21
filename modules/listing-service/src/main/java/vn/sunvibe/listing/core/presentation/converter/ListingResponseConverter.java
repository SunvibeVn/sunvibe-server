package vn.sunvibe.listing.core.presentation.converter;

import org.springframework.stereotype.Component;
import vn.sunvibe.listing.core.application.query.AgreementListingsView;
import vn.sunvibe.listing.core.application.query.ListingCreateResult;
import vn.sunvibe.listing.core.application.query.ListingRevisionView;
import vn.sunvibe.listing.core.application.query.ListingView;
import vn.sunvibe.listing.core.application.query.RevisionCommandResult;
import vn.sunvibe.listing.core.application.query.TermsSourcesView;
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
import vn.sunvibe.listing.core.presentation.dto.response.ListingItemResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingPendingTermsResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingRentResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingRevisionChangeResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingRevisionDetailResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingRevisionItemResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingRevisionProblemResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingTermsResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingTermsSnapshotResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingTermsSourcesResponse;
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
