package vn.sunvibe.listing.core.infrastructure.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.sunvibe.listing.core.domain.model.listing.RevisionStatus;
import vn.sunvibe.listing.core.infrastructure.persistence.entity.ListingRevisionEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Spring Data JPA Repository - the revisions of the listings (listing-publish E06-S1 spec 5.3, E06-S2 spec 5.3). */
public interface ListingRevisionJpaRepository extends JpaRepository<ListingRevisionEntity, String> {

    /** The listing's revisions, the greatest number first; one statement on uq_listing_revisions_number. */
    @Query("select r from ListingRevisionEntity r where r.listingId = :listingId order by r.revisionNo desc")
    List<ListingRevisionEntity> findNewest(@Param("listingId") String listingId, Pageable page);

    /**
     * A command's working set (listing-publish E06-S2 spec E06S2-D19): the open revisions and the one numbered
     * {@code target}, the greatest number first; at most two rows, since uq_listing_revisions_open admits one open
     * revision, and the page bounds it anyway.
     */
    @Query("select r from ListingRevisionEntity r where r.listingId = :listingId"
            + " and (r.status in :openStatuses or r.revisionNo = :target) order by r.revisionNo desc")
    List<ListingRevisionEntity> findWorkingSet(@Param("listingId") String listingId,
                                               @Param("openStatuses") Collection<RevisionStatus> openStatuses,
                                               @Param("target") int target, Pageable page);

    /** The listing's greatest revision number; one statement on uq_listing_revisions_number. */
    @Query("select max(r.revisionNo) from ListingRevisionEntity r where r.listingId = :listingId")
    Integer findNewestNumber(@Param("listingId") String listingId);

    /** One revision of the listing; one statement on uq_listing_revisions_number. */
    @Query("select r from ListingRevisionEntity r where r.listingId = :listingId and r.revisionNo = :revisionNo")
    Optional<ListingRevisionEntity> findOne(@Param("listingId") String listingId, @Param("revisionNo") int revisionNo);

    /** The number of the listing's open revision; one statement on uq_listing_revisions_open. */
    @Query("select r.revisionNo from ListingRevisionEntity r where r.listingId = :listingId"
            + " and r.status in :openStatuses")
    List<Integer> findOpenNumbers(@Param("listingId") String listingId,
                                  @Param("openStatuses") Collection<RevisionStatus> openStatuses);
}
