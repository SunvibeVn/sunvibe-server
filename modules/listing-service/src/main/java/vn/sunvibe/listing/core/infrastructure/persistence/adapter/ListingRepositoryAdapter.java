package vn.sunvibe.listing.core.infrastructure.persistence.adapter;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import vn.sunvibe.listing.core.domain.exception.ListingChangedException;
import vn.sunvibe.listing.core.domain.exception.ListingCodeTakenException;
import vn.sunvibe.listing.core.domain.exception.OpenListingExistsException;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessRecord;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessSummary;
import vn.sunvibe.listing.core.domain.model.listing.Listing;
import vn.sunvibe.listing.core.domain.model.listing.ListingCode;
import vn.sunvibe.listing.core.domain.model.listing.ListingHistoryEntry;
import vn.sunvibe.listing.core.domain.model.listing.ListingRevision;
import vn.sunvibe.listing.core.domain.model.listing.ListingState;
import vn.sunvibe.listing.core.domain.model.listing.RevisionChange;
import vn.sunvibe.listing.core.domain.model.listing.RevisionChangeKind;
import vn.sunvibe.listing.core.domain.model.listing.RevisionDecision;
import vn.sunvibe.listing.core.domain.model.listing.RevisionStatus;
import vn.sunvibe.listing.core.domain.repository.ListingDomainRepository;
import vn.sunvibe.listing.core.domain.repository.ListingRevisionPage;
import vn.sunvibe.listing.core.domain.repository.ListingRow;
import vn.sunvibe.listing.core.domain.repository.ListingRowPage;
import vn.sunvibe.listing.core.domain.repository.ReconfirmationCandidate;
import vn.sunvibe.listing.core.domain.repository.ReviewCandidate;
import vn.sunvibe.listing.core.infrastructure.persistence.entity.ListingEntity;
import vn.sunvibe.listing.core.infrastructure.persistence.entity.ListingFreshnessRecordEntity;
import vn.sunvibe.listing.core.infrastructure.persistence.entity.ListingHistoryEntity;
import vn.sunvibe.listing.core.infrastructure.persistence.entity.ListingRevisionEntity;
import vn.sunvibe.listing.core.infrastructure.persistence.entity.ListingRevisionFeeEntity;
import vn.sunvibe.listing.core.infrastructure.persistence.entity.ListingRevisionReviewEntity;
import vn.sunvibe.listing.core.infrastructure.persistence.mapper.manual.ListingEntityMapper;
import vn.sunvibe.listing.core.infrastructure.persistence.repository.ListingFreshnessRecordJpaRepository;
import vn.sunvibe.listing.core.infrastructure.persistence.repository.ListingHistoryJpaRepository;
import vn.sunvibe.listing.core.infrastructure.persistence.repository.ListingJpaRepository;
import vn.sunvibe.listing.core.infrastructure.persistence.repository.ListingRevisionFeeJpaRepository;
import vn.sunvibe.listing.core.infrastructure.persistence.repository.ListingRevisionJpaRepository;
import vn.sunvibe.listing.core.infrastructure.persistence.repository.ListingRevisionReviewJpaRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Repository Implementation - listings, their revisions and history. No entity stays managed after a call. */
@Service
@RequiredArgsConstructor
public class ListingRepositoryAdapter implements ListingDomainRepository {

    /** listing BR-1, stored per agreement (listing-publish E06-S1 spec E06S1-D4): one listing not archived. */
    private static final String OPEN_LISTING_INDEX = "uq_listings_agreement_open";
    /** listing-detail BR-15: a code is unique among every code ever issued. */
    private static final String CODE_INDEX = "uq_listings_code";
    /** listing-publish BR-6: at most one Draft or Pending review revision per listing. */
    private static final String OPEN_REVISION_INDEX = "uq_listing_revisions_open";
    /** listing section 3.3: the revisions of a listing have distinct numbers. */
    private static final String REVISION_NUMBER_INDEX = "uq_listing_revisions_number";
    /** listing-publish BR-6, E06-S4 spec E06S4-D9: at most one Approved revision per listing. */
    private static final String APPROVED_REVISION_INDEX = "uq_listing_revisions_approved";
    /** listing-publish US-4 AC-3, E06-S4 spec E06S4-D8: one decision per revision. */
    private static final String DECISION_KEY = "listing_revision_reviews_pkey";
    private static final int MAX_CAUSE_DEPTH = 20;
    /** E06S2-D19: the open revision and the target, two rows at most; the limit bounds it anyway. */
    private static final int WORKING_SET_LIMIT = 3;
    private static final List<RevisionStatus> OPEN_STATUSES = List.of(RevisionStatus.DRAFT,
            RevisionStatus.PENDING_REVIEW);
    /** E06-S4 plan E06S4-P2: a decision's working set is its target and the approved revision still waiting. */
    private static final List<RevisionStatus> REVIEW_STATUSES = List.of(RevisionStatus.APPROVED);

    private final ListingJpaRepository listings;
    private final ListingRevisionJpaRepository revisions;
    private final ListingRevisionFeeJpaRepository fees;
    private final ListingHistoryJpaRepository history;
    private final ListingFreshnessRecordJpaRepository freshnessRecords;
    private final ListingRevisionReviewJpaRepository reviews;
    private final ListingEntityMapper mapper;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Inserts the listing's row and flushes, so the two unique indexes refuse a second open listing of the agreement or
     * a taken code here, where they are translated, and not later at commit; then its revisions and history rows,
     * flushed (their foreign keys need the listing's row first).
     */
    @Override
    public void insert(Listing listing) {
        ListingEntity saved;
        try {
            saved = listings.saveAndFlush(mapper.toNewEntity(listing));
        } catch (DataIntegrityViolationException e) {
            if (violates(e, OPEN_LISTING_INDEX)) {
                throw new OpenListingExistsException();
            }
            if (violates(e, CODE_INDEX)) {
                throw new ListingCodeTakenException();
            }
            throw e;
        }
        List<ListingRevisionEntity> savedRevisions = revisions.saveAllAndFlush(mapper.toRevisionEntities(listing));
        List<ListingHistoryEntity> savedHistory = history.saveAllAndFlush(mapper.toHistoryEntities(listing));
        entityManager.detach(saved);
        savedRevisions.forEach(entityManager::detach);
        savedHistory.forEach(entityManager::detach);
    }

    @Override
    public Optional<ListingRow> findRow(String listingId) {
        return listings.findById(listingId).map(entity -> {
            ListingRow row = mapper.toRow(entity);
            entityManager.detach(entity);
            return row;
        });
    }

    /** One statement: {@code limit + 1} rows on the unique (listing_id, revision_no) index. */
    @Override
    public ListingRevisionPage findNewestRevisions(String listingId, int limit) {
        List<ListingRevisionEntity> rows = revisions.findNewest(listingId, PageRequest.of(0, limit + 1));
        rows.forEach(entityManager::detach);
        return new ListingRevisionPage(rows.stream().limit(limit).map(mapper::toRevision).toList(),
                rows.size() > limit);
    }

    /** One statement: {@code limit + 1} rows on idx_listings_agreement_id, a total order (created_at, id). */
    @Override
    public ListingRowPage findNewestByAgreement(String agreementId, int limit) {
        List<ListingEntity> rows = listings.findNewest(agreementId, PageRequest.of(0, limit + 1));
        rows.forEach(entityManager::detach);
        return new ListingRowPage(rows.stream().limit(limit).map(mapper::toRow).toList(), rows.size() > limit);
    }

    @Override
    public boolean hasOpen(String agreementId) {
        return listings.existsByAgreementIdAndStateNot(agreementId, ListingState.ARCHIVED);
    }

    @Override
    public boolean existsByCode(String code) {
        return listings.existsByCode(code);
    }

    /**
     * Four statements in this order (listing-publish E06-S2 plan E06S2-P16): the listing row, the working set's
     * revisions, their fee rows, the greatest number last, so the greatest number is never below a number read before
     * it. Whatever mix of two commands the statements see belongs to a command that moved the row version, which
     * {@link #update} then finds moved. Every entity is detached.
     */
    @Override
    public Optional<Listing> findForCommand(String listingId, Integer targetRevisionNoOrNull) {
        Optional<ListingEntity> found = listings.findById(listingId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        ListingEntity row = found.get();
        entityManager.detach(row);
        // no revision is numbered below 1, so 0 selects the open revisions only (plan E06S2-P3)
        int target = targetRevisionNoOrNull == null ? 0 : targetRevisionNoOrNull;
        return Optional.of(loaded(row, OPEN_STATUSES, target));
    }

    /** Two statements: the revision on uq_listing_revisions_number, then its fee rows. */
    @Override
    public Optional<ListingRevision> findRevision(String listingId, int revisionNo) {
        Optional<ListingRevisionEntity> found = revisions.findOne(listingId, revisionNo);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        ListingRevisionEntity entity = found.get();
        entityManager.detach(entity);
        Map<String, List<ListingRevisionFeeEntity>> feesByRevision = feesOf(List.of(entity));
        return Optional.of(mapper.toFullRevision(entity, feesByRevision.getOrDefault(entity.getId(), List.of())));
    }

    @Override
    public Optional<Integer> findOpenRevisionNo(String listingId) {
        return revisions.findOpenNumbers(listingId, OPEN_STATUSES).stream().findFirst();
    }

    /**
     * listing-publish E06-S2 spec E06S2-D19, plan E06S2-P7: the listing row's guarded update first, then each changed
     * revision written as a whole in the aggregate's order (an added or copied one inserted), its fee rows replaced only
     * when its terms were and written with a copy (E06-S4 spec E06S4-D14), then each pending decision (E06S4-D8), then
     * the pending history; every entity detached. A revision index or decision key violation here means a row was
     * written without moving the listing's version: the backstop answers it as a concurrent change. A command without a
     * copy or a decision sends the statements it sent before E06-S4.
     */
    @Override
    public void update(Listing listing) {
        if (listings.advanceVersion(listing.id(), listing.rowVersion(), listing.updatedAt()) == 0) {
            throw new ListingChangedException();
        }
        for (RevisionChange change : listing.pendingRevisionChanges()) {
            ListingRevision revision = change.revision();
            entityManager.detach(saveRevision(mapper.toRevisionEntity(listing.id(), revision)));
            if (change.kind() == RevisionChangeKind.TERMS_REPLACED) {
                fees.deleteByRevisionId(revision.id());
                fees.saveAllAndFlush(mapper.toFeeEntities(revision)).forEach(entityManager::detach);
            }
            if (change.kind() == RevisionChangeKind.COPIED) {
                fees.saveAllAndFlush(mapper.toFeeEntities(revision)).forEach(entityManager::detach);
            }
        }
        for (RevisionDecision decision : listing.pendingDecisions()) {
            insertDecision(listing.id(), decision);
        }
        history.saveAllAndFlush(mapper.toHistoryEntities(listing)).forEach(entityManager::detach);
    }

    /** One statement on the primary key: the two unmapped columns (plan E06S3-P2). */
    @Override
    public FreshnessSummary findFreshnessSummary(String listingId) {
        return listings.findFreshnessColumns(listingId)
                .map(columns -> new FreshnessSummary(columns.getConfirmedOn(), columns.getLatestValid()))
                .orElse(FreshnessSummary.none());
    }

    /** One statement on uq_listing_freshness_records_version; the entity is detached. */
    @Override
    public Optional<FreshnessRecord> findLatestFreshnessRecord(String listingId) {
        return freshnessRecords.findFirstByListingIdOrderByListingRowVersionDesc(listingId).map(entity -> {
            entityManager.detach(entity);
            return mapper.toFreshnessRecord(entity);
        });
    }

    /**
     * listing-publish E06-S3 spec E06S3-D6: the listing row's guarded update first, which takes the row's lock and moves
     * its version, time and summary together; then the record and the history row by {@code persist}, so no merge
     * SELECT precedes them, one flush, both detached: three statements. A violation of the record's unique row version
     * is not translated (plan E06S3-P14): the guarded update alone decides a concurrent record.
     */
    @Override
    public void recordFreshness(String listingId, long loadedRowVersion, Instant now, FreshnessSummary next,
                                FreshnessRecord record, ListingHistoryEntry entry) {
        if (listings.recordFreshness(listingId, loadedRowVersion, now, next.confirmedOnOrNull(),
                next.latestValidOrNull()) == 0) {
            throw new ListingChangedException();
        }
        ListingFreshnessRecordEntity recordRow = mapper.toFreshnessRecordEntity(record);
        ListingHistoryEntity historyRow = ListingEntityMapper.toHistoryEntity(listingId, entry);
        entityManager.persist(recordRow);
        entityManager.persist(historyRow);
        entityManager.flush();
        entityManager.detach(recordRow);
        entityManager.detach(historyRow);
    }

    /** One statement on idx_listings_freshness_due: the first chunk, or the chunk after the cursor's (date, id). */
    @Override
    public List<ReconfirmationCandidate> findReconfirmationCandidates(LocalDate confirmedOnOrBefore,
                                                                      ReconfirmationCandidate afterOrNull, int limit) {
        List<ListingJpaRepository.ReconfirmationRow> rows = afterOrNull == null
                ? listings.findDueFirst(confirmedOnOrBefore, limit)
                : listings.findDueAfter(confirmedOnOrBefore, afterOrNull.summary().confirmedOnOrNull(),
                afterOrNull.listingId(), limit);
        return rows.stream()
                .map(row -> new ReconfirmationCandidate(row.getId(), row.getCode(), row.getAgreementId(),
                        ListingState.valueOf(row.getState()),
                        new FreshnessSummary(row.getConfirmedOn(), row.getLatestValid())))
                .toList();
    }

    /**
     * Four statements, as {@link #findForCommand} reads them (listing-publish E06-S4 plan E06S4-P2): the listing row, the
     * target and the approved revision still waiting, their fee rows, the greatest number. Every entity is detached.
     */
    @Override
    public Optional<Listing> findForReview(String listingId, int revisionNo) {
        Optional<ListingEntity> found = listings.findById(listingId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        ListingEntity row = found.get();
        entityManager.detach(row);
        return Optional.of(loaded(row, REVIEW_STATUSES, revisionNo));
    }

    /** One statement for the set, none for an empty one (plan E06S4-P4). */
    @Override
    public Map<String, Set<String>> findAuthors(Collection<String> revisionIds) {
        if (revisionIds.isEmpty()) {
            return Map.of();
        }
        Map<String, Set<String>> authors = new HashMap<>();
        for (ListingRevisionJpaRepository.AuthorRow row : revisions.findAuthorRows(List.copyOf(revisionIds))) {
            authors.computeIfAbsent(row.getRevisionId(), id -> new HashSet<>()).add(row.getAuthorId());
        }
        Map<String, Set<String>> answer = new HashMap<>();
        authors.forEach((revisionId, authorIds) -> answer.put(revisionId, Set.copyOf(authorIds)));
        return Map.copyOf(answer);
    }

    /** One statement on idx_listing_revisions_review_queue: the first chunk, or the chunk after the cursor. */
    @Override
    public List<ReviewCandidate> findReviewCandidates(Instant afterSubmittedAtOrNull, String afterRevisionIdOrNull,
                                                      int limit) {
        List<ListingRevisionJpaRepository.ReviewRow> rows = afterSubmittedAtOrNull == null || afterRevisionIdOrNull == null
                ? revisions.findReviewFirst(limit)
                : revisions.findReviewAfter(afterSubmittedAtOrNull, afterRevisionIdOrNull, limit);
        return rows.stream().map(mapper::toCandidate).toList();
    }

    /** One statement; the entity is detached. */
    @Override
    public Optional<RevisionDecision> findDecision(String listingId, int revisionNo) {
        return reviews.findOne(listingId, revisionNo).map(entity -> {
            entityManager.detach(entity);
            return mapper.toDecision(entity, revisionNo);
        });
    }

    /**
     * The working set of {@code row} at the statuses given and the target, with their fees and the greatest number, in
     * that statement order: the working set's revisions, their fee rows, the greatest number last.
     */
    private Listing loaded(ListingEntity row, List<RevisionStatus> statuses, int target) {
        List<ListingRevisionEntity> workingSet = revisions.findWorkingSet(row.getId(), statuses, target,
                PageRequest.of(0, WORKING_SET_LIMIT));
        workingSet.forEach(entityManager::detach);
        Map<String, List<ListingRevisionFeeEntity>> feesByRevision = feesOf(workingSet);
        Integer newest = revisions.findNewestNumber(row.getId());
        List<ListingRevision> loaded = workingSet.stream()
                .map(entity -> mapper.toFullRevision(entity, feesByRevision.getOrDefault(entity.getId(), List.of())))
                .toList();
        return Listing.loadForCommand(row.getId(), ListingCode.of(row.getCode()), row.getUnitId(),
                row.getTransactionType(), row.getAgreementId(), row.getState(), row.getCreatedBy(), row.getCreatedAt(),
                row.getUpdatedAt(), row.getRowVersion() == null ? 0L : row.getRowVersion(),
                newest == null ? 0 : newest, loaded);
    }

    /**
     * Inserts the decision by {@code persist}, so no merge SELECT precedes it, and flushes, so the primary key refuses a
     * second decision here: a row written without moving the listing's version, answered as a concurrent change (spec
     * 3.3's backstop). Any other failure is rethrown.
     */
    private void insertDecision(String listingId, RevisionDecision decision) {
        ListingRevisionReviewEntity row = mapper.toReviewEntity(listingId, decision);
        try {
            entityManager.persist(row);
            entityManager.flush();
        } catch (PersistenceException | DataIntegrityViolationException e) {
            if (violates(e, DECISION_KEY)) {
                throw new ListingChangedException();
            }
            throw e;
        }
        entityManager.detach(row);
    }

    private ListingRevisionEntity saveRevision(ListingRevisionEntity entity) {
        try {
            return revisions.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            if (violates(e, OPEN_REVISION_INDEX) || violates(e, REVISION_NUMBER_INDEX)
                    || violates(e, APPROVED_REVISION_INDEX)) {
                throw new ListingChangedException();
            }
            throw e;
        }
    }

    /** One statement for the fee rows of the given revisions, none for no revision; every entity detached. */
    private Map<String, List<ListingRevisionFeeEntity>> feesOf(List<ListingRevisionEntity> revisionRows) {
        if (revisionRows.isEmpty()) {
            return Map.of();
        }
        List<ListingRevisionFeeEntity> rows = fees.findOfRevisions(
                revisionRows.stream().map(ListingRevisionEntity::getId).toList());
        rows.forEach(entityManager::detach);
        return rows.stream().collect(Collectors.groupingBy(row -> row.getId().getRevisionId()));
    }

    private static boolean violates(RuntimeException e, String constraintName) {
        Throwable current = e;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof org.hibernate.exception.ConstraintViolationException violation
                    && constraintName.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
