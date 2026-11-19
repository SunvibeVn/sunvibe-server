package vn.sunvibe.listing.core.infrastructure.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.sunvibe.listing.core.infrastructure.persistence.entity.ListingRevisionEntity;

import java.util.List;

/** Spring Data JPA Repository - the revisions of the listings (listing-publish E06-S1 spec 5.3). */
public interface ListingRevisionJpaRepository extends JpaRepository<ListingRevisionEntity, String> {

    /** The listing's revisions, the greatest number first; one statement on uq_listing_revisions_number. */
    @Query("select r from ListingRevisionEntity r where r.listingId = :listingId order by r.revisionNo desc")
    List<ListingRevisionEntity> findNewest(@Param("listingId") String listingId, Pageable page);
}
