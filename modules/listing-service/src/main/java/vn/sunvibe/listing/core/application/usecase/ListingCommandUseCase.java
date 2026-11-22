package vn.sunvibe.listing.core.application.usecase;

import vn.sunvibe.listing.core.application.command.FreshnessRecordCommand;
import vn.sunvibe.listing.core.application.command.ListingCreateCommand;
import vn.sunvibe.listing.core.application.command.RevisionCopyCommand;
import vn.sunvibe.listing.core.application.command.RevisionSaveCommand;
import vn.sunvibe.listing.core.application.command.RevisionStartCommand;
import vn.sunvibe.listing.core.application.command.RevisionSubmitCommand;
import vn.sunvibe.listing.core.application.command.RevisionWithdrawCommand;
import vn.sunvibe.listing.core.application.command.TermsCaptureCommand;
import vn.sunvibe.listing.core.application.exception.ListingNotFoundException;
import vn.sunvibe.listing.core.application.exception.ListingRefusedException;
import vn.sunvibe.listing.core.application.query.FreshnessRecordResult;
import vn.sunvibe.listing.core.application.query.ListingCreateResult;
import vn.sunvibe.listing.core.application.query.RevisionCommandResult;
import vn.sunvibe.listing.core.domain.exception.ListingChangedException;
import vn.sunvibe.listing.core.domain.exception.RevisionNotSubmittableException;
import vn.sunvibe.listing.core.domain.exception.RevisionStateException;

/**
 * Use Case - the commands on listings (listing-publish E06-S1 spec 3.1; E06-S2 spec 3.1 to 3.6). Every revision command
 * is the assigned Sale's (a SALE or SALE_LEADER holding the agreement's basis lead now) and answers, in this order
 * (E06S2-D3): {@link ListingNotFoundException} for an unknown, malformed or out-of-scope listing;
 * {@link ListingRefusedException} {@code LISTING_ARCHIVED}; {@link ListingChangedException} for a stale data version;
 * {@link ListingNotFoundException} for a revision the listing does not have; then the command's own refusal.
 */
public interface ListingCommandUseCase {

    /**
     * Creates a listing under the agreement for its assigned Sale (listing-publish US-1 AC-1 to AC-4).
     *
     * @throws ListingNotFoundException the agreement is unknown, of another transaction type, or the caller is not a
     *                                  SALE or SALE_LEADER holding its basis lead now: one answer for all three
     * @throws ListingRefusedException  E05's answer does not allow a listing, or the agreement already has one
     */
    ListingCreateResult create(ListingCreateCommand command, ListingViewer viewer);

    /**
     * Adds an empty Draft numbered after the greatest number (listing-publish US-2 AC-6).
     *
     * @throws RevisionStateException {@code OPEN_REVISION_EXISTS} while a Draft or Pending review revision is open
     */
    RevisionCommandResult startRevision(RevisionStartCommand command, ListingViewer viewer);

    /**
     * Replaces a Draft's title, description and features (listing-publish US-2 AC-1, AC-3).
     *
     * @throws RevisionStateException {@code REVISION_NOT_DRAFT}
     */
    RevisionCommandResult saveContent(RevisionSaveCommand command, ListingViewer viewer);

    /**
     * Takes a Draft's terms snapshot from E05's version in effect or waiting to start (listing-publish US-2 AC-2, BR-4).
     *
     * @throws RevisionStateException  {@code REVISION_NOT_DRAFT}, before E05 is read
     * @throws ListingRefusedException {@code TERMS_SOURCE_ABSENT}
     * @throws IllegalStateException   E05's terms break the contract (500, nothing written)
     */
    RevisionCommandResult captureTerms(TermsCaptureCommand command, ListingViewer viewer);

    /**
     * Submits a complete Draft for review with its unit snapshot (listing-publish US-2 AC-4, AC-5).
     *
     * @throws RevisionStateException          {@code REVISION_NOT_DRAFT}
     * @throws RevisionNotSubmittableException every rule the Draft breaks, before property is read
     * @throws IllegalStateException           property answers no profile or one off its contract (500)
     */
    RevisionCommandResult submit(RevisionSubmitCommand command, ListingViewer viewer);

    /**
     * Withdraws a Pending review revision before a decision (listing-publish US-2 AC-5).
     *
     * @throws RevisionStateException {@code REVISION_NOT_PENDING}
     */
    RevisionCommandResult withdraw(RevisionWithdrawCommand command, ListingViewer viewer);

    /**
     * Copies a Live, Approved or Needs changes revision into a new Draft numbered after the greatest number, by the
     * caller, with the source's content and terms snapshot; the source stays as it is (listing-publish US-2 AC-6, AC-7,
     * BR-6; E06-S4 spec 3.6, E06S4-D14; E06-S6 spec E06S6-D10).
     *
     * @throws RevisionStateException {@code REVISION_NOT_COPYABLE} for a source of any other status, then
     *                                {@code OPEN_REVISION_EXISTS} while a Draft or Pending review revision is open
     */
    RevisionCommandResult copyRevision(RevisionCopyCommand command, ListingViewer viewer);

    /**
     * Records a contact of the assigned Sale with the person letting the unit, dated today in Vietnam time
     * (listing-publish US-6 AC-1, AC-2, BR-9, BR-11; E06-S3 spec 3.1, E06S3-D3). A valid confirmation tries to put the
     * approved revision live (E06-S6 spec E06S6-D7), which may change the listing's state.
     *
     * @throws ListingNotFoundException the listing is unknown or malformed, or the caller is not a SALE or SALE_LEADER
     *                                  holding its agreement's basis lead now: one answer for all
     * @throws ListingRefusedException  {@code LISTING_ARCHIVED}, stored or because the agreement ended
     * @throws ListingChangedException  the data version is stale, or another command moved it meanwhile
     * @throws IllegalStateException    the put live a valid confirmation tries meets an E05 group or availability code
     *                                  listing does not know; nothing written
     */
    FreshnessRecordResult recordFreshness(FreshnessRecordCommand command, ListingViewer viewer);
}
