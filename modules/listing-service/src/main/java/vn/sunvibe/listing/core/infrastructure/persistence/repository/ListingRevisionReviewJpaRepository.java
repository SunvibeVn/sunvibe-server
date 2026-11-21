package vn.sunvibe.listing.core.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.sunvibe.listing.core.infrastructure.persistence.entity.ListingRevisionReviewEntity;

import java.util.Optional;

/** Spring Data JPA Repository - the decisions of reviewed revisions (listing-publish E06-S4 spec 5.3, E06S4-D8). */
public interface ListingRevisionReviewJpaRepository extends JpaRepository<ListingRevisionReviewEntity, String> {

    /**
     * The decision of the listing's revision of that number; one statement, joined on the revision's primary key and
     * uq_listing_revisions_number.
     */
    @Query("select d from ListingRevisionReviewEntity d, ListingRevisionEntity r where r.id = d.revisionId"
            + " and r.listingId = :listingId and r.revisionNo = :revisionNo")
    Optional<ListingRevisionReviewEntity> findOne(@Param("listingId") String listingId,
                                                  @Param("revisionNo") int revisionNo);
}
