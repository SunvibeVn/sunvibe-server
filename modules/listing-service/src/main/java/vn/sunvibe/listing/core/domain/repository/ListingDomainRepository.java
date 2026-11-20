package vn.sunvibe.listing.core.domain.repository;

import vn.sunvibe.listing.core.domain.exception.ListingChangedException;
import vn.sunvibe.listing.core.domain.exception.ListingCodeTakenException;
import vn.sunvibe.listing.core.domain.exception.OpenListingExistsException;
import vn.sunvibe.listing.core.domain.model.listing.Listing;
import vn.sunvibe.listing.core.domain.model.listing.ListingRevision;

import java.util.Optional;

/**
 * Repository Interface - listings (listing-publish E06-S1 spec 5.1). The reads never load every revision of a listing
 * (E06S1-D17): each bounded read takes {@code limit + 1} rows newest first and answers {@code limit} with a flag.
 */
public interface ListingDomainRepository {

    /**
     * Inserts a new listing with its revisions and pending history, and flushes, so the two unique indexes refuse here.
     *
     * @throws OpenListingExistsException the agreement already has a listing not stored as archived (listing BR-1)
     * @throws ListingCodeTakenException  another listing holds the code (listing-detail BR-15)
     */
    void insert(Listing listing);

    /** The listing's own row. */
    Optional<ListingRow> findRow(String listingId);

    /** The {@code limit} newest revisions of the listing, by number descending. */
    ListingRevisionPage findNewestRevisions(String listingId, int limit);

    /** The {@code limit} newest listings of the agreement, by creation time then id, descending. */
    ListingRowPage findNewestByAgreement(String agreementId, int limit);

    /** Whether the agreement has a listing whose stored state is not {@code ARCHIVED}. */
    boolean hasOpen(String agreementId);

    /** Whether a listing holds this code. */
    boolean existsByCode(String code);

    /**
     * The listing loaded for a revision command (listing-publish E06-S2 spec E06S2-D19): its row, its open revision and
     * the target revision in their full form with their fees, and its greatest revision number, in four statements in
     * that order (plan E06S2-P16). Empty for an unknown listing; a target the listing lacks is not in the set.
     */
    Optional<Listing> findForCommand(String listingId, Integer targetRevisionNoOrNull);

    /** One revision of the listing in its full form, with its fees; two statements (E06S2-D14). */
    Optional<ListingRevision> findRevision(String listingId, int revisionNo);

    /** The number of the listing's Draft or Pending review revision, if it has one; one statement (E06S2-D20). */
    Optional<Integer> findOpenRevisionNo(String listingId);

    /**
     * Writes a revision command (listing-publish E06-S2 spec E06S2-D19): the listing row's guarded version update
     * first, then only the revision rows the commands changed (fee rows replaced only with the terms), then the pending
     * history; no entity stays managed.
     *
     * @throws ListingChangedException another command moved the row version since the load, or a revision index refused
     *                                 a row as the backstop
     */
    void update(Listing listing);
}
