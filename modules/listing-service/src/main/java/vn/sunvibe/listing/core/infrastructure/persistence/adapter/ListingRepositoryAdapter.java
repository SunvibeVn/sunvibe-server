package vn.sunvibe.listing.core.infrastructure.persistence.adapter;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import vn.sunvibe.listing.core.domain.exception.ListingCodeTakenException;
import vn.sunvibe.listing.core.domain.exception.OpenListingExistsException;
import vn.sunvibe.listing.core.domain.model.listing.Listing;
import vn.sunvibe.listing.core.domain.model.listing.ListingState;
import vn.sunvibe.listing.core.domain.repository.ListingDomainRepository;
import vn.sunvibe.listing.core.domain.repository.ListingRevisionPage;
import vn.sunvibe.listing.core.domain.repository.ListingRow;
import vn.sunvibe.listing.core.domain.repository.ListingRowPage;
import vn.sunvibe.listing.core.infrastructure.persistence.entity.ListingEntity;
import vn.sunvibe.listing.core.infrastructure.persistence.entity.ListingHistoryEntity;
import vn.sunvibe.listing.core.infrastructure.persistence.entity.ListingRevisionEntity;
import vn.sunvibe.listing.core.infrastructure.persistence.mapper.manual.ListingEntityMapper;
import vn.sunvibe.listing.core.infrastructure.persistence.repository.ListingHistoryJpaRepository;
import vn.sunvibe.listing.core.infrastructure.persistence.repository.ListingJpaRepository;
import vn.sunvibe.listing.core.infrastructure.persistence.repository.ListingRevisionJpaRepository;

import java.util.List;
import java.util.Optional;

/** Repository Implementation - listings, their revisions and history. No entity stays managed after a call. */
@Service
@RequiredArgsConstructor
public class ListingRepositoryAdapter implements ListingDomainRepository {

    /** listing BR-1, stored per agreement (listing-publish E06-S1 spec E06S1-D4): one listing not archived. */
    private static final String OPEN_LISTING_INDEX = "uq_listings_agreement_open";
    /** listing-detail BR-15: a code is unique among every code ever issued. */
    private static final String CODE_INDEX = "uq_listings_code";
    private static final int MAX_CAUSE_DEPTH = 20;

    private final ListingJpaRepository listings;
    private final ListingRevisionJpaRepository revisions;
    private final ListingHistoryJpaRepository history;
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

    private static boolean violates(DataIntegrityViolationException e, String constraintName) {
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
