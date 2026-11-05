package vn.sunvibe.property.core.infrastructure.persistence.adapter.intake;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.sunvibe.property.core.domain.model.intake.CaseStatus;
import vn.sunvibe.property.core.domain.model.intake.DuplicateCase;
import vn.sunvibe.property.core.domain.repository.DuplicateCaseDomainRepository;
import vn.sunvibe.property.core.infrastructure.persistence.entity.intake.DuplicateCaseEntity;
import vn.sunvibe.property.core.infrastructure.persistence.mapper.manual.IntakeEntityMapper;
import vn.sunvibe.property.core.infrastructure.persistence.repository.intake.DuplicateCaseJpaRepository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/** Repository Implementation - duplicate cases. As for intake rows, no entity stays managed. */
@Service
@RequiredArgsConstructor
public class DuplicateCaseRepositoryAdapter implements DuplicateCaseDomainRepository {

    /** The predicate is spelled as in uq_inventory_duplicate_cases_pair_key (spec 4.10). */
    private static final String INSERT = """
            insert into inventory_duplicate_cases (id, intake_id, candidate_property_id, candidate_intake_id, pair_key,
                scope_hash, candidate_scope_hash, signal, status, version, created_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)
            on conflict (pair_key) where status <> 'CLOSED' do nothing
            """;

    private final DuplicateCaseJpaRepository jpaRepository;
    private final IntakeEntityMapper mapper;
    private final JdbcTemplate jdbc;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public boolean insertIfAbsent(DuplicateCase row) {
        return jdbc.update(INSERT, row.getId(), row.getIntakeId(), row.getCandidatePropertyId(),
                row.getCandidateIntakeId(), row.getPairKey(), row.getScopeHash(), row.getCandidateScopeHash(),
                row.getSignal().name(), row.getStatus().name(), Timestamp.from(row.getCreatedAt())) == 1;
    }

    @Override
    public Optional<DuplicateCase> findLiveByPairKey(String pairKey) {
        return jpaRepository.findByPairKeyAndStatusNot(pairKey, CaseStatus.CLOSED).map(this::read);
    }

    @Override
    public Optional<DuplicateCase> findById(String id) {
        return jpaRepository.findById(id).map(this::read);
    }

    @Override
    public Optional<DuplicateCase> lockById(String id) {
        return jpaRepository.lockForUpdate(id).flatMap(jpaRepository::findById).map(this::read);
    }

    @Override
    public void update(DuplicateCase row) {
        entityManager.detach(jpaRepository.saveAndFlush(mapper.toEntity(row)));
    }

    @Override
    public List<DuplicateCase> findForIntake(String intakeId) {
        return jpaRepository.findForIntake(intakeId).stream().map(this::read).toList();
    }

    @Override
    public List<DuplicateCase> findNamingProperty(String propertyId) {
        return jpaRepository.findByCandidatePropertyIdOrderByCreatedAtAscIdAsc(propertyId).stream().map(this::read).toList();
    }

    @Override
    public List<DuplicateCase> findByStatus(CaseStatus status) {
        return jpaRepository.findByStatusOrderByCreatedAtAscIdAsc(status).stream().map(this::read).toList();
    }

    private DuplicateCase read(DuplicateCaseEntity entity) {
        entityManager.detach(entity);
        return mapper.toDomain(entity);
    }
}
