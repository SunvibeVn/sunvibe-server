package vn.sunvibe.listing.core.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.sunvibe.listing.core.infrastructure.persistence.entity.ListingFreshnessRecordEntity;

import java.util.Optional;

/**
 * Spring Data JPA Repository - the freshness records of listings (listing-publish E06-S3 spec 5.3). Reads only: a record
 * is inserted by the adapter's entity manager after the listing row's guarded update (E06S3-D6), never through here.
 */
public interface ListingFreshnessRecordJpaRepository extends JpaRepository<ListingFreshnessRecordEntity, String> {

    /** The latest record: the greatest listing row version, on uq_listing_freshness_records_version. */
    Optional<ListingFreshnessRecordEntity> findFirstByListingIdOrderByListingRowVersionDesc(String listingId);
}
