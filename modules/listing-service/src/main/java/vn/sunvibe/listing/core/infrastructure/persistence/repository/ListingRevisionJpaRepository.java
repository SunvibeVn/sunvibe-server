package vn.sunvibe.listing.core.infrastructure.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.sunvibe.listing.core.domain.model.listing.RevisionStatus;
import vn.sunvibe.listing.core.infrastructure.persistence.entity.ListingRevisionEntity;

import java.time.Instant;
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

    /**
     * The authors of each revision asked (listing-publish E06-S4 spec E06S4-D3, plan E06S4-P4), one row per revision and
     * author: the creator and the actors of the authoring history rows of the revision and of every revision it was
     * copied from, followed back within its listing. The chain ends because a copy's source has a lower number, and
     * {@code UNION} drops repeated rows. One statement for the set.
     */
    @Query(value = "WITH RECURSIVE chain (root_id, listing_id, revision_no, copied_from, created_by) AS ("
            + " SELECT r.id, r.listing_id, r.revision_no, r.copied_from_revision_no, r.created_by"
            + " FROM listing_revisions r WHERE r.id IN (:revisionIds)"
            + " UNION"
            + " SELECT c.root_id, s.listing_id, s.revision_no, s.copied_from_revision_no, s.created_by"
            + " FROM chain c JOIN listing_revisions s ON s.listing_id = c.listing_id AND s.revision_no = c.copied_from)"
            + " SELECT c.root_id AS \"revisionId\", c.created_by AS \"authorId\" FROM chain c"
            + " UNION"
            + " SELECT c.root_id, h.actor_user_id FROM chain c"
            + " JOIN listing_history h ON h.listing_id = c.listing_id AND h.revision_no = c.revision_no"
            + " WHERE h.actor_user_id IS NOT NULL"
            + " AND h.action IN ('CREATED', 'REVISION_STARTED', 'REVISION_SAVED', 'TERMS_CAPTURED',"
            + " 'REVISION_SUBMITTED', 'REVISION_COPIED')", nativeQuery = true)
    List<AuthorRow> findAuthorRows(@Param("revisionIds") Collection<String> revisionIds);

    /**
     * The first chunk of the review queue (listing-publish E06-S4 spec E06S4-D10, plan E06S4-P5): the Pending review
     * revisions of listings not stored as archived, in (submission time, revision id) order; served by
     * idx_listing_revisions_review_queue.
     */
    @Query(value = "SELECT r.id AS \"id\", r.listing_id AS \"listingId\", l.code AS \"code\","
            + " l.agreement_id AS \"agreementId\", r.revision_no AS \"revisionNo\", r.submitted_at AS \"submittedAt\","
            + " r.title AS \"title\", r.unit_asset_type AS \"unitAssetType\", r.unit_province_code AS \"unitProvinceCode\","
            + " r.unit_province_name AS \"unitProvinceName\", r.unit_ward_code AS \"unitWardCode\","
            + " r.unit_ward_name AS \"unitWardName\", r.unit_bedrooms AS \"unitBedrooms\","
            + " r.unit_bathrooms AS \"unitBathrooms\", r.unit_area_sqm AS \"unitAreaSqm\""
            + " FROM listing_revisions r JOIN listings l ON l.id = r.listing_id"
            + " WHERE r.status = 'PENDING_REVIEW' AND l.state <> 'ARCHIVED'"
            + " ORDER BY r.submitted_at, r.id LIMIT :limit", nativeQuery = true)
    List<ReviewRow> findReviewFirst(@Param("limit") int limit);

    /** The next chunk of {@link #findReviewFirst}: the rows after the keyset cursor (submission time, revision id). */
    @Query(value = "SELECT r.id AS \"id\", r.listing_id AS \"listingId\", l.code AS \"code\","
            + " l.agreement_id AS \"agreementId\", r.revision_no AS \"revisionNo\", r.submitted_at AS \"submittedAt\","
            + " r.title AS \"title\", r.unit_asset_type AS \"unitAssetType\", r.unit_province_code AS \"unitProvinceCode\","
            + " r.unit_province_name AS \"unitProvinceName\", r.unit_ward_code AS \"unitWardCode\","
            + " r.unit_ward_name AS \"unitWardName\", r.unit_bedrooms AS \"unitBedrooms\","
            + " r.unit_bathrooms AS \"unitBathrooms\", r.unit_area_sqm AS \"unitAreaSqm\""
            + " FROM listing_revisions r JOIN listings l ON l.id = r.listing_id"
            + " WHERE r.status = 'PENDING_REVIEW' AND l.state <> 'ARCHIVED'"
            + " AND (r.submitted_at, r.id) > (CAST(:afterAt AS TIMESTAMPTZ), CAST(:afterId AS VARCHAR))"
            + " ORDER BY r.submitted_at, r.id LIMIT :limit", nativeQuery = true)
    List<ReviewRow> findReviewAfter(@Param("afterAt") Instant afterSubmittedAt, @Param("afterId") String afterId,
                                    @Param("limit") int limit);

    /** One author of one revision asked. */
    interface AuthorRow {

        String getRevisionId();

        String getAuthorId();
    }

    /** A candidate row of the review queue: the revision, its listing's code and agreement, its title and unit snapshot. */
    interface ReviewRow {

        String getId();

        String getListingId();

        String getCode();

        String getAgreementId();

        Integer getRevisionNo();

        Instant getSubmittedAt();

        String getTitle();

        String getUnitAssetType();

        String getUnitProvinceCode();

        String getUnitProvinceName();

        String getUnitWardCode();

        String getUnitWardName();

        Integer getUnitBedrooms();

        Integer getUnitBathrooms();

        Double getUnitAreaSqm();
    }
}
