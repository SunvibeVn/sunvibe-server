package vn.sunvibe.brokerage.core.infrastructure.persistence.adapter;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementSuspension;
import vn.sunvibe.brokerage.core.domain.repository.AgreementSuspensionDomainRepository;
import vn.sunvibe.brokerage.core.infrastructure.persistence.entity.AgreementSuspensionEntity;
import vn.sunvibe.brokerage.core.infrastructure.persistence.repository.AgreementSuspensionJpaRepository;

import java.time.Instant;
import java.util.List;

/**
 * Repository Implementation - the suspension records (E05-S8 spec S8-D6, 5.3): the only writer of their table, in the
 * agreement's transaction. No entity stays managed after a call.
 */
@Service
@RequiredArgsConstructor
public class AgreementSuspensionRepositoryAdapter implements AgreementSuspensionDomainRepository {

    /** E05-S8 spec S8-D6: at most one open record per agreement. */
    private static final String OPEN_INDEX = "uq_brokerage_agreement_suspensions_open";
    private static final int MAX_CAUSE_DEPTH = 20;

    private final AgreementSuspensionJpaRepository jpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Inserts and flushes, so the partial unique index refuses a second open record here, in the command, where it is
     * translated: the rule of the records is broken, a fault of the caller, never a request error.
     */
    @Override
    public void open(AgreementSuspension suspension) {
        AgreementSuspensionEntity saved;
        try {
            saved = jpaRepository.saveAndFlush(toEntity(suspension));
        } catch (DataIntegrityViolationException e) {
            if (violates(e, OPEN_INDEX)) {
                throw new IllegalStateException("The agreement already holds an open suspension.", e);
            }
            throw e;
        }
        entityManager.detach(saved);
    }

    @Override
    public boolean release(String agreementId, String releasedBy, Instant releasedAt, String reason) {
        return jpaRepository.release(agreementId, releasedBy, releasedAt, reason) > 0;
    }

    @Override
    public List<AgreementSuspension> newestOf(String agreementId, int limit) {
        List<AgreementSuspensionEntity> entities = jpaRepository.findNewest(agreementId, PageRequest.of(0, limit));
        List<AgreementSuspension> records = entities.stream().map(AgreementSuspensionRepositoryAdapter::toDomain)
                .toList();
        entities.forEach(entityManager::detach);
        return records;
    }

    private static AgreementSuspensionEntity toEntity(AgreementSuspension suspension) {
        return AgreementSuspensionEntity.builder()
                .id(suspension.id())
                .agreementId(suspension.agreementId())
                .reasonType(suspension.reasonType())
                .note(suspension.noteOrNull())
                .suspendedBy(suspension.suspendedBy())
                .suspendedAt(suspension.suspendedAt())
                .releasedBy(suspension.releasedByOrNull())
                .releasedAt(suspension.releasedAtOrNull())
                .releaseReason(suspension.releaseReasonOrNull())
                .build();
    }

    /** A stored record outside the rules is a data fault; the message names no stored value (BR-17). */
    private static AgreementSuspension toDomain(AgreementSuspensionEntity entity) {
        try {
            return new AgreementSuspension(entity.getId(), entity.getAgreementId(), entity.getReasonType(),
                    entity.getNote(), entity.getSuspendedBy(), entity.getSuspendedAt(), entity.getReleasedBy(),
                    entity.getReleasedAt(), entity.getReleaseReason());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("A stored suspension breaks its rules.", e);
        }
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
