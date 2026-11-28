package vn.sunvibe.listing.core.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.sunvibe.listing.core.infrastructure.persistence.entity.ListingPublicationEntity;

import java.util.List;
import java.util.Optional;

/** Spring Data JPA Repository - the listings' publication rows (listing-publish E06-S7 spec 5.3, E06S7-D2). */
public interface ListingPublicationJpaRepository extends JpaRepository<ListingPublicationEntity, String> {

    /** The row of that code; one statement on uq_listing_publications_code. */
    Optional<ListingPublicationEntity> findByCode(String code);

    /**
     * The ids of the listings of a unit that have a publication row, in id order (E06S7-D13); one statement on
     * ix_listing_publications_unit, at most the listings of one unit.
     */
    @Query(value = "SELECT listing_id FROM listing_publications WHERE unit_id = :unitId ORDER BY listing_id",
            nativeQuery = true)
    List<String> findListingIdsOfUnit(@Param("unitId") String unitId);
}
