package vn.sunvibe.brokerage.core.infrastructure.persistence.adapter;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import vn.sunvibe.brokerage.core.domain.exception.AgreementExistsException;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementStatus;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageAgreement;
import vn.sunvibe.brokerage.core.domain.model.agreement.TransactionType;
import vn.sunvibe.brokerage.core.domain.repository.AgreementDomainRepository;
import vn.sunvibe.brokerage.core.infrastructure.persistence.entity.BrokerageAgreementEntity;
import vn.sunvibe.brokerage.core.infrastructure.persistence.mapper.manual.AgreementEntityMapper;
import vn.sunvibe.brokerage.core.infrastructure.persistence.repository.BrokerageAgreementJpaRepository;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
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

    @Override
    public Optional<BrokerageAgreement> findById(String id) {
        return jpaRepository.findWithVersions(id).map(entity -> {
            BrokerageAgreement agreement = mapper.toDomain(entity);
            entityManager.detach(entity);
            return agreement;
        });
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
