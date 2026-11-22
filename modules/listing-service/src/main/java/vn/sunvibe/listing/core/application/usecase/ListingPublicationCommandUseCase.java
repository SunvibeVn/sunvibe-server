package vn.sunvibe.listing.core.application.usecase;

import vn.sunvibe.listing.core.application.command.PutLiveCommand;
import vn.sunvibe.listing.core.application.exception.ListingNotFoundException;
import vn.sunvibe.listing.core.application.exception.ListingRefusedException;
import vn.sunvibe.listing.core.application.query.RevisionCommandResult;
import vn.sunvibe.listing.core.domain.exception.ListingChangedException;
import vn.sunvibe.listing.core.domain.exception.RevisionStateException;

/** Use Case - the publication commands of a listing (listing-publish E06-S6 spec E06S6-D5). */
public interface ListingPublicationCommandUseCase {

    /**
     * The assigned Sale puts the Approved revision live (listing-publish US-5 AC-1, AC-3, AC-4; E06-S6 spec E06S6-D5):
     * when every display condition holds, the revision becomes Live, the former Live revision Replaced, an Unpublished
     * listing Published, and the listing's event is appended; the listing's data version moves by one.
     *
     * @throws ListingNotFoundException the listing or the revision is unknown or malformed, or the caller is not a SALE
     *                                  or SALE_LEADER holding its agreement's basis lead now: one answer for all
     * @throws ListingRefusedException  {@code LISTING_ARCHIVED}, stored or because the agreement ended; or
     *                                  {@code PUT_LIVE_REFUSED} with the display reasons that stop it, nothing written
     * @throws ListingChangedException  the data version is stale, or another command moved it between the command's
     *                                  reads
     * @throws RevisionStateException   {@code REVISION_NOT_APPROVED}
     * @throws IllegalStateException    an E05 group, availability code or terms listing does not know; nothing written
     */
    RevisionCommandResult putLive(PutLiveCommand command, ListingViewer viewer);
}
