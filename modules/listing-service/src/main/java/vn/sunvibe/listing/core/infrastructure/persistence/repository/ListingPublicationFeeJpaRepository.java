package vn.sunvibe.listing.core.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.sunvibe.listing.core.infrastructure.persistence.entity.ListingPublicationFeeEntity;
import vn.sunvibe.listing.core.infrastructure.persistence.entity.ListingPublicationFeeKey;

import java.util.List;

/** Spring Data JPA Repository - the fee rows of the listings' publications (listing-publish E06-S7 spec 5.3). */
public interface ListingPublicationFeeJpaRepository
        extends JpaRepository<ListingPublicationFeeEntity, ListingPublicationFeeKey> {

    /** The fee rows of one publication in fee number order; one statement on the primary key. */
    @Query("select f from ListingPublicationFeeEntity f where f.id.listingId = :listingId order by f.id.feeNo")
    List<ListingPublicationFeeEntity> findOfListing(@Param("listingId") String listingId);
}
