package vn.sunvibe.listing.core.presentation.converter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.listing.core.application.query.ListingPublicPreviewView;
import vn.sunvibe.listing.core.application.query.ListingPublicView;
import vn.sunvibe.listing.core.presentation.dto.response.ListingPublicPreviewResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingPublicResponse;

/**
 * The anonymous answer's view to its DTO (listing-publish E06-S7 spec E06S7-D6): a pure function of its argument, the
 * terms and features in the staff schemas' shapes ({@link ListingResponseConverter#toTerms},
 * {@link ListingResponseConverter#toFeatures}).
 */
@Component
@RequiredArgsConstructor
public class ListingPublicResponseConverter {

    private final ListingResponseConverter responses;

    public ListingPublicResponse toResponse(ListingPublicView view) {
        return new ListingPublicResponse(view.code(), view.transactionType(), view.propertyTypeOrNull(),
                view.provinceCodeOrNull(), view.wardCodeOrNull(), view.bedrooms(), view.bathrooms(), view.areaSqm(),
                responses.toTerms(view.terms()), view.title(), view.description(),
                ListingResponseConverter.toFeatures(view.features()), view.liveOn(), view.lastConfirmedOn());
    }

    /** The staff preview's view to its DTO (E06S7-D9): the same shapes, absent where the revision holds none yet. */
    public ListingPublicPreviewResponse toResponse(ListingPublicPreviewView view) {
        return new ListingPublicPreviewResponse(view.revisionNo(), view.revisionStatus().name(),
                view.provinceNameOrNull(), view.wardNameOrNull(), view.code(), view.transactionType(),
                view.propertyTypeOrNull(), view.provinceCodeOrNull(), view.wardCodeOrNull(), view.bedroomsOrNull(),
                view.bathroomsOrNull(), view.areaSqmOrNull(),
                view.termsOrNull() == null ? null : responses.toTerms(view.termsOrNull()), view.titleOrNull(),
                view.descriptionOrNull(), ListingResponseConverter.toFeatures(view.features()), view.liveOnOrNull(),
                view.lastConfirmedOnOrNull());
    }
}
