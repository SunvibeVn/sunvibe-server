package vn.sunvibe.listing.core.application.query;

import vn.sunvibe.listing.core.domain.model.listing.RevisionFeatures;
import vn.sunvibe.listing.core.domain.model.listing.RevisionTerms;

import java.time.LocalDate;

/**
 * Query View - the anonymous answer about a listing (listing-publish E06-S7 spec E06S7-D6; listing-publish BR-14 without
 * photos): exactly the public columns of its publication row and the date of its latest valid confirmation (KT-3).
 * {@code liveOn} is the Vietnam date of the put live (listing BR-6 group 9). No id, no digest, no state, no reason.
 * {@link #toString()} names no value.
 */
public record ListingPublicView(String code, String transactionType, String propertyTypeOrNull,
                                String provinceCodeOrNull, String wardCodeOrNull, int bedrooms, int bathrooms,
                                double areaSqm, RevisionTerms terms, String title, String description,
                                RevisionFeatures features, LocalDate liveOn, LocalDate lastConfirmedOn) {

    @Override
    public String toString() {
        return "ListingPublicView[hidden]";
    }
}
