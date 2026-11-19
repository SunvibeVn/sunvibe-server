package vn.sunvibe.listing.core.application.usecase;

import vn.sunvibe.listing.core.application.exception.ListingNotFoundException;
import vn.sunvibe.listing.core.application.query.AgreementListingsView;
import vn.sunvibe.listing.core.application.query.ListingView;

/** Use Case - the staff reads of listings (listing-publish E06-S1 spec 3.3). Read-only. */
public interface ListingQueryUseCase {

    /**
     * The listing page (listing BR-5, BR-16).
     *
     * @throws ListingNotFoundException the id is malformed or unknown, or the listing is out of the viewer's scope: one
     *                                  answer for all three
     */
    ListingView listing(String listingId, ListingViewer viewer);

    /**
     * The listing section of an agreement's page.
     *
     * @throws ListingNotFoundException the id is malformed or unknown, or the agreement is out of the viewer's scope
     */
    AgreementListingsView ofAgreement(String agreementId, ListingViewer viewer);
}
