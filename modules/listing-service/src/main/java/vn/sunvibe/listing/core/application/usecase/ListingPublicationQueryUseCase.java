package vn.sunvibe.listing.core.application.usecase;

import vn.sunvibe.listing.core.application.exception.ListingNotFoundException;
import vn.sunvibe.listing.core.application.query.ListingPublicPreviewView;
import vn.sunvibe.listing.core.application.query.ListingPublicationView;
import vn.sunvibe.listing.core.application.query.LiveDifferenceView;

/** Use Case - the publication reads of a listing (listing-publish E06-S6 spec E06S6-D11, D12). */
public interface ListingPublicationQueryUseCase {

    /**
     * The listing's publication (E06S6-D11), for the readers of the listing in its scope: the live revision and the
     * Approved revision waiting, each with its display answer, and what the viewer may do; read without a lock.
     *
     * @throws ListingNotFoundException the listing is unknown or malformed, or out of the viewer's scope: one answer
     * @throws IllegalStateException    an E05 group, availability code or terms listing does not know
     */
    ListingPublicationView publication(String listingId, ListingViewer viewer);

    /**
     * The difference of a revision of the listing from its live revision (E06S6-D12), for the readers of reviews.
     *
     * @throws ListingNotFoundException the listing or the revision is unknown or malformed, or the viewer reads no
     *                                  review: one answer for all
     * @throws IllegalStateException    the live revision is read without its terms snapshot
     */
    LiveDifferenceView liveDifference(String listingId, Integer revisionNoOrNull, ListingViewer viewer);

    /**
     * A revision's public data as it would be published (listing-publish E06-S7 spec E06S7-D9), for the readers of the
     * listing in the scope of the revision read; reads no E05 condition and no property value.
     *
     * @throws ListingNotFoundException the listing or the revision is unknown or malformed, or out of the viewer's
     *                                  scope: one answer for all
     */
    ListingPublicPreviewView publicPreview(String listingId, Integer revisionNoOrNull, ListingViewer viewer);
}
