package vn.sunvibe.listing.core.application.usecase;

import vn.sunvibe.listing.core.application.exception.ListingNotFoundException;
import vn.sunvibe.listing.core.application.query.ListingPublicView;

/**
 * Use Case - the anonymous answer about one listing (listing-publish E06-S7 spec E06S7-D5; US-5 AC-5, US-7 AC-7;
 * ADR-012 decisions 1, 2, 4): the public data of its live revision and the date of its latest valid confirmation, only
 * while every display condition holds at this read. The caller is anonymous: no viewer, no scope.
 */
public interface ListingPublicQueryUseCase {

    /**
     * The public answer for the listing of that code, evaluated now.
     *
     * @throws ListingNotFoundException no publication row holds the code, its listing is not stored Published, or a
     *                                  display condition does not hold: one answer for all
     * @throws IllegalStateException    an E05 group, availability code or terms code listing does not know, or any
     *                                  failure of a read: never caught, so the answer fails closed
     */
    ListingPublicView publicListing(String code);
}
