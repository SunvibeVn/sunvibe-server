package vn.sunvibe.listing.core.presentation.converter;

import org.springframework.stereotype.Component;
import vn.sunvibe.listing.core.application.query.AgreementListingsView;
import vn.sunvibe.listing.core.application.query.ListingCreateResult;
import vn.sunvibe.listing.core.application.query.ListingView;
import vn.sunvibe.listing.core.presentation.dto.response.AgreementListingsResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingCreatedResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingCreationResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingDetailResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingItemResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingRevisionItemResponse;

/** Result and views to response DTOs, the codes as strings: a pure function of its arguments. */
@Component
public class ListingResponseConverter {

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
                view.hasOlderRevisions());
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
}
