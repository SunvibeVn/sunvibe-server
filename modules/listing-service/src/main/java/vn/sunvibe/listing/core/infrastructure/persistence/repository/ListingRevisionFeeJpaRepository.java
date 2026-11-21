package vn.sunvibe.listing.core.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.sunvibe.listing.core.infrastructure.persistence.entity.ListingRevisionFeeEntity;
import vn.sunvibe.listing.core.infrastructure.persistence.entity.ListingRevisionFeeKey;

import java.util.Collection;
import java.util.List;

/** Spring Data JPA Repository - the fee rows of the revisions' terms snapshots (listing-publish E06-S2 spec 5.3). */
public interface ListingRevisionFeeJpaRepository extends JpaRepository<ListingRevisionFeeEntity, ListingRevisionFeeKey> {

    /** The fee rows of these revisions, by revision and fee number; one statement on the primary key. */
    @Query("select f from ListingRevisionFeeEntity f where f.id.revisionId in :revisionIds"
            + " order by f.id.revisionId, f.id.feeNo")
    List<ListingRevisionFeeEntity> findOfRevisions(@Param("revisionIds") Collection<String> revisionIds);

    /**
     * Deletes the fee rows of one revision before its new snapshot's rows are inserted (E06S2-D19). A bulk statement
     * bypasses the persistence context; the adapter leaves no fee entity managed, so none goes stale.
     */
    @Modifying
    @Query("delete from ListingRevisionFeeEntity f where f.id.revisionId = :revisionId")
    int deleteByRevisionId(@Param("revisionId") String revisionId);
}
