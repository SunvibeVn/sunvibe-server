package vn.sunvibe.listing.core.infrastructure.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.sunvibe.listing.core.domain.model.listing.ListingState;
import vn.sunvibe.listing.core.infrastructure.persistence.entity.ListingEntity;

import java.time.Instant;
import java.util.List;

/** Spring Data JPA Repository - the listings' own rows (listing-publish E06-S1 spec 5.3). */
public interface ListingJpaRepository extends JpaRepository<ListingEntity, String> {

    /** The agreement's listings, newest first, then the greater id; one statement on idx_listings_agreement_id. */
    @Query("select l from ListingEntity l where l.agreementId = :agreementId order by l.createdAt desc, l.id desc")
    List<ListingEntity> findNewest(@Param("agreementId") String agreementId, Pageable page);

    boolean existsByAgreementIdAndStateNot(String agreementId, ListingState state);

    boolean existsByCode(String code);

    /**
     * The guarded write of a revision command (listing-publish E06-S2 spec E06S2-D19, listing BR-3): moves the row
     * version only if no other command moved it since the load, and answers the rows written (0 or 1). It runs first,
     * so it takes the listing row's lock and a concurrent command waits on it, then finds the version moved.
     */
    @Modifying
    @Query(value = "UPDATE listings SET updated_at = :now, row_version = row_version + 1"
            + " WHERE id = :id AND row_version = :loaded", nativeQuery = true)
    int advanceVersion(@Param("id") String id, @Param("loaded") long loadedVersion, @Param("now") Instant now);
}
