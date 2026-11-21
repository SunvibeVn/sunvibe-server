package vn.sunvibe.listing.core.domain.repository;

import vn.sunvibe.listing.core.domain.model.listing.ListingAccessLog;

/**
 * Repository Interface - the access log of listings' private data (listing-publish E06-S4 spec E06S4-D12, D18):
 * insert-only, never read in this slice.
 */
public interface ListingAccessLogDomainRepository {

    /** Inserts the row and flushes, in the caller's transaction; no entity stays managed. */
    void record(ListingAccessLog log);
}
