package vn.sunvibe.listing.core.presentation.converter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.listing.core.application.query.ListingPublicView;
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
}
