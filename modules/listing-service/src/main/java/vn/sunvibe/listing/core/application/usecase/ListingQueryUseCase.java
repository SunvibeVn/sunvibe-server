package vn.sunvibe.listing.core.application.usecase;

import vn.sunvibe.listing.core.application.exception.ListingNotFoundException;
import vn.sunvibe.listing.core.application.query.AgreementListingsView;
import vn.sunvibe.listing.core.application.query.ListingRevisionView;
import vn.sunvibe.listing.core.application.query.ListingView;
import vn.sunvibe.listing.core.application.query.TermsSourcesView;

/** Use Case - the staff reads of listings (listing-publish E06-S1 spec 3.3; E06-S2 spec 3.7). Read-only. */
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

    /**
     * One revision of a listing, for whoever may read the listing (listing BR-5; listing-publish E06-S2 spec E06S2-D2,
     * E06S2-D14).
     *
     * @param revisionNoOrNull the revision named in the path; null when it named no well-formed number
     * @throws ListingNotFoundException the listing is malformed, unknown or out of scope, or it has no such revision
     */
    ListingRevisionView revision(String listingId, Integer revisionNoOrNull, ListingViewer viewer);

    /**
     * What the listing's assigned Sale can take a terms snapshot from now (listing-publish US-2 AC-2; E06-S2 spec
     * E06S2-D2, E06S2-D14): E05's private terms, so for the drafter only.
     *
     * @throws ListingNotFoundException the listing is malformed or unknown, or the caller is not its assigned Sale
     * @throws IllegalStateException    E05's terms break the contract
     */
    TermsSourcesView termsSources(String listingId, ListingViewer viewer);
}
