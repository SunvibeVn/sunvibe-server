package vn.sunvibe.listing.core.domain.repository;

import vn.sunvibe.listing.core.domain.exception.ListingChangedException;
import vn.sunvibe.listing.core.domain.exception.ListingCodeTakenException;
import vn.sunvibe.listing.core.domain.exception.OpenListingExistsException;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessRecord;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessSummary;
import vn.sunvibe.listing.core.domain.model.listing.Listing;
import vn.sunvibe.listing.core.domain.model.listing.ListingHistoryEntry;
import vn.sunvibe.listing.core.domain.model.listing.ListingRevision;
import vn.sunvibe.listing.core.domain.model.listing.RevisionDecision;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Repository Interface - listings (listing-publish E06-S1 spec 5.1). The reads never load every revision of a listing
 * (E06S1-D17): each bounded read takes {@code limit + 1} rows newest first and answers {@code limit} with a flag.
 */
public interface ListingDomainRepository {

    /**
     * Inserts a new listing with its revisions and pending history, and flushes, so the two unique indexes refuse here.
     *
     * @throws OpenListingExistsException the agreement already has a listing not stored as archived (listing BR-1)
     * @throws ListingCodeTakenException  another listing holds the code (listing-detail BR-15)
     */
    void insert(Listing listing);

    /** The listing's own row. */
    Optional<ListingRow> findRow(String listingId);

    /** The {@code limit} newest revisions of the listing, by number descending. */
    ListingRevisionPage findNewestRevisions(String listingId, int limit);

    /** The {@code limit} newest listings of the agreement, by creation time then id, descending. */
    ListingRowPage findNewestByAgreement(String agreementId, int limit);

    /** Whether the agreement has a listing whose stored state is not {@code ARCHIVED}. */
    boolean hasOpen(String agreementId);

    /** Whether a listing holds this code. */
    boolean existsByCode(String code);

    /**
     * The listing loaded for a revision command (listing-publish E06-S2 spec E06S2-D19): its row, its open revision and
     * the target revision in their full form with their fees, and its greatest revision number, in four statements in
     * that order (plan E06S2-P16). Empty for an unknown listing; a target the listing lacks is not in the set.
     */
    Optional<Listing> findForCommand(String listingId, Integer targetRevisionNoOrNull);

    /** One revision of the listing in its full form, with its fees; two statements (E06S2-D14). */
    Optional<ListingRevision> findRevision(String listingId, int revisionNo);

    /** The number of the listing's Draft or Pending review revision, if it has one; one statement (E06S2-D20). */
    Optional<Integer> findOpenRevisionNo(String listingId);

    /**
     * Writes a revision command (listing-publish E06-S2 spec E06S2-D19): the listing row's guarded version update
     * first, with the stored state when the command changed it (E06-S6 spec E06S6-D6), then only the revision rows the
     * commands changed in their order (fee rows replaced only with the terms, written with a copy), then the pending
     * decisions (E06-S4 spec E06S4-D8), then the pending history; no entity stays managed.
     *
     * @throws ListingChangedException another command moved the row version since the load, or a revision index or the
     *                                 decision's key refused a row as the backstop
     */
    void update(Listing listing);

    /**
     * The listing row locked {@code FOR UPDATE} until the transaction ends (listing-publish E06-S6 spec E06S6-D5 step
     * (8)): its row version as locked; empty for an unknown listing. One statement.
     */
    Optional<Long> lockRow(String listingId);

    /** The number of the listing's Approved revision still waiting, if any (E06-S6 spec E06S6-D7); one statement. */
    Optional<Integer> findApprovedRevisionNo(String listingId);

    /**
     * The listing loaded for a put live (listing-publish E06-S6 spec E06S6-D5 step (6)): its row, the target revision when
     * one is named, the Approved revision still waiting and the live revision, each in its full form with its fees, and
     * its greatest revision number, unlocked, in the four statements of {@link #findForCommand}. Empty for an unknown
     * listing; a target the listing lacks is not in the set.
     */
    Optional<Listing> findForPutLive(String listingId, Integer targetRevisionNoOrNull);

    /**
     * Writes a valid freshness record that put the approved revision live (listing-publish E06-S6 spec E06S6-D6, D7):
     * checks that the listing was loaded on {@code loadedRowVersion} and that the record stores the next version; then
     * the listing row's guarded update of {@link #recordFreshness} with the listing's state, then the listing's revision
     * rows, then the record, its history row and the listing's pending history; one flush, no entity stays managed.
     *
     * @throws IllegalStateException   the caller broke one of the two version invariants; nothing is written
     * @throws ListingChangedException another command moved the row version since {@code loadedRowVersion} was read
     */
    void recordFreshnessWithPutLive(Listing listing, long loadedRowVersion, Instant now, FreshnessSummary next,
                                    FreshnessRecord record, ListingHistoryEntry entry);

    /**
     * The listing's freshness summary, from its row (listing-publish E06-S3 spec E06S3-D6); {@link FreshnessSummary#none()}
     * for a listing with no record. One statement.
     */
    FreshnessSummary findFreshnessSummary(String listingId);

    /** The listing's latest freshness record: the greatest listing row version. One statement. */
    Optional<FreshnessRecord> findLatestFreshnessRecord(String listingId);

    /**
     * Writes a freshness record (listing-publish E06-S3 spec E06S3-D6): the listing row's guarded update first, which
     * moves the row version, {@code updated_at} and the two summary columns together; then the record and its history
     * row, two inserts and one flush; no entity stays managed. A violation of the record's unique row version is not
     * translated: only a broken guard can produce one (plan E06S3-P14). A valid record that puts the approved revision
     * live is written by {@link #recordFreshnessWithPutLive} instead (E06-S6 spec E06S6-D6).
     *
     * @throws ListingChangedException another command moved the row version since {@code loadedRowVersion} was read
     */
    void recordFreshness(String listingId, long loadedRowVersion, Instant now, FreshnessSummary next,
                         FreshnessRecord record, ListingHistoryEntry entry);

    /**
     * At most {@code limit} listings not stored as archived whose latest valid confirmation is on or before the date, in
     * (confirmation date, id) order, after the cursor when one is given (listing-publish E06-S3 spec E06S3-D7). One
     * statement.
     */
    List<ReconfirmationCandidate> findReconfirmationCandidates(LocalDate confirmedOnOrBefore,
                                                               ReconfirmationCandidate afterOrNull, int limit);

    /**
     * The listing loaded for a review decision (listing-publish E06-S4 plan E06S4-P2): its row, the target, the approved
     * revision still waiting and the live revision (E06-S6 spec E06S6-D7), each in its full form with its fees, and its
     * greatest revision number, read as {@link #findForCommand} reads them. Empty for an unknown listing; a target the
     * listing lacks is not in the set.
     */
    Optional<Listing> findForReview(String listingId, int revisionNo);

    /**
     * The authors of each revision (listing-publish E06-S4 spec E06S4-D3, plan E06S4-P4): its creator, the actors of its
     * authoring history rows ({@code CREATED}, {@code REVISION_STARTED}, {@code REVISION_SAVED}, {@code TERMS_CAPTURED},
     * {@code REVISION_SUBMITTED}, {@code REVISION_COPIED}), and the authors of any revision it was copied from, followed
     * back through every copy. One statement for the set; none for an empty set. A revision unknown here is left out.
     */
    Map<String, Set<String>> findAuthors(Collection<String> revisionIds);

    /**
     * At most {@code limit} Pending review revisions of listings not stored as archived, in (submission time, revision id)
     * order, after the cursor when both parts are given (listing-publish E06-S4 spec E06S4-D10, plan E06S4-P5). One
     * statement.
     */
    List<ReviewCandidate> findReviewCandidates(Instant afterSubmittedAtOrNull, String afterRevisionIdOrNull, int limit);

    /** The stored decision of the listing's revision of that number (E06-S4 spec E06S4-D15); one statement. */
    Optional<RevisionDecision> findDecision(String listingId, int revisionNo);
}
