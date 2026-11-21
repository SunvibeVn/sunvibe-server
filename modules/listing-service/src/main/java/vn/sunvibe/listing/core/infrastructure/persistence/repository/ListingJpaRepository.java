package vn.sunvibe.listing.core.infrastructure.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.sunvibe.listing.core.domain.model.listing.ListingState;
import vn.sunvibe.listing.core.infrastructure.persistence.entity.ListingEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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

    /**
     * The guarded write of a freshness record (listing-publish E06-S3 spec E06S3-D6, plan E06S3-P2): the row version, the
     * time and the two freshness columns move together only if no other command moved the row version since the load;
     * answers the rows written (0 or 1). The revision commands take the same row version through
     * {@link #advanceVersion}, so each refuses a load the other has moved. A null binds with its type through the cast.
     */
    @Modifying
    @Query(value = "UPDATE listings SET updated_at = :now, row_version = row_version + 1,"
            + " freshness_confirmed_on = CAST(:confirmedOn AS DATE),"
            + " freshness_latest_valid = CAST(:latestValid AS BOOLEAN)"
            + " WHERE id = :id AND row_version = :loaded", nativeQuery = true)
    int recordFreshness(@Param("id") String id, @Param("loaded") long loadedVersion, @Param("now") Instant now,
                        @Param("confirmedOn") LocalDate confirmedOnOrNull, @Param("latestValid") Boolean latestValidOrNull);

    /** The listing's two freshness columns, unmapped on {@link ListingEntity}; one statement on the primary key. */
    @Query(value = "SELECT freshness_confirmed_on AS \"confirmedOn\", freshness_latest_valid AS \"latestValid\""
            + " FROM listings WHERE id = :id", nativeQuery = true)
    Optional<FreshnessColumns> findFreshnessColumns(@Param("id") String id);

    /**
     * The first chunk of listings to reconfirm (listing-publish E06-S3 spec E06S3-D7): not stored as archived, confirmed
     * on or before the date, in (date, id) order; served by idx_listings_freshness_due.
     */
    @Query(value = "SELECT id AS \"id\", code AS \"code\", agreement_id AS \"agreementId\", state AS \"state\","
            + " freshness_confirmed_on AS \"confirmedOn\", freshness_latest_valid AS \"latestValid\" FROM listings"
            + " WHERE state <> 'ARCHIVED' AND freshness_confirmed_on <= :onOrBefore"
            + " ORDER BY freshness_confirmed_on, id LIMIT :limit", nativeQuery = true)
    List<ReconfirmationRow> findDueFirst(@Param("onOrBefore") LocalDate onOrBefore, @Param("limit") int limit);

    /** The next chunk of {@link #findDueFirst}: the rows after the keyset cursor (date, id). */
    @Query(value = "SELECT id AS \"id\", code AS \"code\", agreement_id AS \"agreementId\", state AS \"state\","
            + " freshness_confirmed_on AS \"confirmedOn\", freshness_latest_valid AS \"latestValid\" FROM listings"
            + " WHERE state <> 'ARCHIVED' AND freshness_confirmed_on <= :onOrBefore"
            + " AND (freshness_confirmed_on, id) > (CAST(:afterDate AS DATE), CAST(:afterId AS VARCHAR))"
            + " ORDER BY freshness_confirmed_on, id LIMIT :limit", nativeQuery = true)
    List<ReconfirmationRow> findDueAfter(@Param("onOrBefore") LocalDate onOrBefore,
                                         @Param("afterDate") LocalDate afterDate, @Param("afterId") String afterId,
                                         @Param("limit") int limit);

    /** The two freshness columns of a listing row. */
    interface FreshnessColumns {

        LocalDate getConfirmedOn();

        Boolean getLatestValid();
    }

    /** A candidate row of the reconfirmation list. */
    interface ReconfirmationRow {

        String getId();

        String getCode();

        String getAgreementId();

        String getState();

        LocalDate getConfirmedOn();

        Boolean getLatestValid();
    }
}
