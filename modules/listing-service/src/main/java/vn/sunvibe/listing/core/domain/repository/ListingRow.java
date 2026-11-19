package vn.sunvibe.listing.core.domain.repository;

import vn.sunvibe.listing.core.domain.model.listing.ListingCode;
import vn.sunvibe.listing.core.domain.model.listing.ListingState;
import vn.sunvibe.listing.core.domain.model.listing.TransactionType;

import java.time.Instant;

/**
 * A listing's own row, without its revisions (listing-publish E06-S1 spec 5.1).
 *
 * @param storedState the stored state; a read answers {@link ListingState#effective(boolean)}
 */
public record ListingRow(String listingId, ListingCode code, String unitId, TransactionType transactionType,
                         String agreementId, ListingState storedState, String createdBy, Instant createdAt,
                         long rowVersion) {
}
