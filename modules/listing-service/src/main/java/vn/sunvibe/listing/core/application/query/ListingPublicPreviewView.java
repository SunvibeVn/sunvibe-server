package vn.sunvibe.listing.core.application.query;

import vn.sunvibe.listing.core.domain.model.listing.RevisionFeatures;
import vn.sunvibe.listing.core.domain.model.listing.RevisionStatus;
import vn.sunvibe.listing.core.domain.model.listing.RevisionTerms;

import java.time.LocalDate;

/**
 * Query View - a revision's public data as it would be published, for the staff who read the listing (listing-publish
 * E06-S7 spec section 4.2, E06S7-D9; US-2 AC-3): the groups of the anonymous answer from the revision itself, its unit
 * fields and the snapshot's place names absent until its submission took them, its terms absent until a snapshot was
 * taken, {@code liveOnOrNull} only once it went live, and the listing's KT-3 date now, absent without a valid
 * confirmation. Data only: no display condition. {@link #toString()} names no value.
 */
public record ListingPublicPreviewView(int revisionNo, RevisionStatus revisionStatus, String code,
                                       String transactionType, String propertyTypeOrNull, String provinceCodeOrNull,
                                       String provinceNameOrNull, String wardCodeOrNull, String wardNameOrNull,
                                       Integer bedroomsOrNull, Integer bathroomsOrNull, Double areaSqmOrNull,
                                       RevisionTerms termsOrNull, String titleOrNull, String descriptionOrNull,
                                       RevisionFeatures features, LocalDate liveOnOrNull,
                                       LocalDate lastConfirmedOnOrNull) {

    @Override
    public String toString() {
        return "ListingPublicPreviewView[" + revisionNo + "]";
    }
}
