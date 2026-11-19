package vn.sunvibe.listing.core.domain.repository;

import vn.sunvibe.listing.core.domain.exception.ListingCodeTakenException;
import vn.sunvibe.listing.core.domain.exception.OpenListingExistsException;
import vn.sunvibe.listing.core.domain.model.listing.Listing;

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
}
