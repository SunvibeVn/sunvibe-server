package vn.sunvibe.brokerage.core.infrastructure.persistence.adapter;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import vn.sunvibe.brokerage.core.domain.exception.AgreementExistsException;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementStatus;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageAgreement;
import vn.sunvibe.brokerage.core.domain.model.agreement.TransactionType;
import vn.sunvibe.brokerage.core.domain.repository.AgreementDomainRepository;
import vn.sunvibe.brokerage.core.infrastructure.persistence.entity.BrokerageAgreementEntity;
import vn.sunvibe.brokerage.core.infrastructure.persistence.mapper.manual.AgreementEntityMapper;
import vn.sunvibe.brokerage.core.infrastructure.persistence.repository.BrokerageAgreementJpaRepository;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Repository Implementation - brokerage agreements. No entity stays managed after a call. */
@Service
@RequiredArgsConstructor
public class AgreementRepositoryAdapter implements AgreementDomainRepository {

    /** brokerage-agreement BR-1: one unfinished agreement per unit and transaction type. */
    private static final String UNFINISHED_UNIT_INDEX = "uq_brokerage_agreements_unfinished_unit";
    private static final int MAX_CAUSE_DEPTH = 20;
    private static final List<AgreementStatus> UNFINISHED = Arrays.stream(AgreementStatus.values())
            .filter(AgreementStatus::isUnfinished).toList();

    private final BrokerageAgreementJpaRepository jpaRepository;
    private final AgreementEntityMapper mapper;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Inserts and flushes, so the unfinished-unit index refuses a second unfinished agreement here, where it is
     * translated, and not later at commit.
     */
    @Override
    public void create(BrokerageAgreement agreement) {
        BrokerageAgreementEntity saved;
        try {
            saved = jpaRepository.saveAndFlush(mapper.toEntity(agreement));
        } catch (DataIntegrityViolationException e) {
            if (violates(e, UNFINISHED_UNIT_INDEX)) {
                throw new AgreementExistsException();
            }
            throw e;
        }
        entityManager.detach(saved);
    }

    /**
     * Three statements: the agreement with its versions, then the versions' fee rows and their fee tier rows into the
     * same persistence context (two lists are not fetch-joined in one query).
     */
    @Override
    public Optional<BrokerageAgreement> findById(String id) {
        return jpaRepository.findWithVersions(id).map(entity -> {
            jpaRepository.fetchVersionFees(id);
            jpaRepository.fetchVersionFeeTiers(id);
            BrokerageAgreement agreement = mapper.toDomain(entity);
            entityManager.detach(entity);
            return agreement;
        });
    }

    /**
     * A merge of the mapped graph with the row version it was read with (E05-S3 plan E05S3-P5): a row version that moved
     * fails with Spring's optimistic-locking failure, at the merge or at the guarded UPDATE of the flush. Fee rows are
     * matched by (version, number) and fee tier rows by (version, renewal, number): updated, inserted, or removed as
     * orphans. The changed update time makes the agreement row dirty, so its row version is checked and bumped.
     */
    @Override
    public void update(BrokerageAgreement agreement) {
        if (agreement.getRowVersionOrNull() == null) {
            throw new IllegalStateException("Only a loaded agreement is updated.");
        }
        BrokerageAgreementEntity saved = jpaRepository.saveAndFlush(mapper.toEntity(agreement));
        entityManager.detach(saved);
    }

    @Override
    public List<UnfinishedAgreement> unfinishedOf(Collection<String> unitIds, TransactionType type) {
        if (unitIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findUnfinished(unitIds, type, UNFINISHED).stream()
                .map(row -> new UnfinishedAgreement(row.getAgreementId(), row.getUnitId(), row.getBasisLeadId()))
                .toList();
    }

    /**
     * Four statements: the ids in their order, then the agreements with their versions, the versions' fee rows and their
     * fee tier rows for the whole set, as {@link #findById} reads one.
     */
    @Override
    public List<BrokerageAgreement> sentToOwner(String ownerUserId, int limit) {
        List<String> agreementIds = jpaRepository.findSentToOwnerIds(ownerUserId, PageRequest.of(0, limit));
        if (agreementIds.isEmpty()) {
            return List.of();
        }
        List<BrokerageAgreementEntity> entities = jpaRepository.findWithVersionsIn(agreementIds);
        jpaRepository.fetchVersionFeesIn(agreementIds);
        jpaRepository.fetchVersionFeeTiersIn(agreementIds);
        Map<String, BrokerageAgreement> byId = new HashMap<>();
        for (BrokerageAgreementEntity entity : entities) {
            byId.put(entity.getId(), mapper.toDomain(entity));
            entityManager.detach(entity);
        }
        return agreementIds.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    @Override
    public List<String> dueLapseAgreementIds(Instant sentAtOrBefore, int limit) {
        return jpaRepository.findDueLapseIds(sentAtOrBefore, PageRequest.of(0, limit));
    }

    /** Whether the failed write broke the named constraint, read from the Hibernate cause. */
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
