package vn.sunvibe.listing.core.application.usecase;

import vn.sunvibe.listing.core.application.command.ListingCreateCommand;
import vn.sunvibe.listing.core.application.exception.ListingNotFoundException;
import vn.sunvibe.listing.core.application.exception.ListingRefusedException;
import vn.sunvibe.listing.core.application.query.ListingCreateResult;

/** Use Case - the commands on listings (listing-publish E06-S1 spec 3.1). */
public interface ListingCommandUseCase {

    /**
     * Creates a listing under the agreement for its assigned Sale (listing-publish US-1 AC-1 to AC-4).
     *
     * @throws ListingNotFoundException the agreement is unknown, of another transaction type, or the caller is not a
     *                                  SALE or SALE_LEADER holding its basis lead now: one answer for all three
     * @throws ListingRefusedException  E05's answer does not allow a listing, or the agreement already has one
     */
    ListingCreateResult create(ListingCreateCommand command, ListingViewer viewer);
}
