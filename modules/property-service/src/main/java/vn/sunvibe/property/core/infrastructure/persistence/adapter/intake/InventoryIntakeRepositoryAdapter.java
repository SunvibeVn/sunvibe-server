package vn.sunvibe.property.core.infrastructure.persistence.adapter.intake;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import vn.sunvibe.property.core.domain.exception.IntakeConflictException;
import vn.sunvibe.property.core.domain.model.intake.IntakeStatus;
import vn.sunvibe.property.core.domain.model.intake.InventoryIntake;
import vn.sunvibe.property.core.domain.repository.InventoryIntakeDomainRepository;
import vn.sunvibe.property.core.infrastructure.persistence.entity.intake.InventoryIntakeEntity;
import vn.sunvibe.property.core.infrastructure.persistence.mapper.manual.IntakeEntityMapper;
import vn.sunvibe.property.core.infrastructure.persistence.repository.intake.InventoryIntakeJpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository Implementation - intake rows. No entity stays managed: open-in-view keeps a persistence context for the whole
 * request, and a managed row would answer with its old state after a lock (property-create intake spec 3, 5.3), so every read and write detaches.
 */
@Service
@RequiredArgsConstructor
public class InventoryIntakeRepositoryAdapter implements InventoryIntakeDomainRepository {

    private static final List<IntakeStatus> ENDED = List.of(IntakeStatus.ABANDONED, IntakeStatus.COMPLETED);

    private final InventoryIntakeJpaRepository jpaRepository;
    private final IntakeEntityMapper mapper;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void insert(InventoryIntake intake) {
        try {
            entityManager.detach(jpaRepository.saveAndFlush(mapper.toEntity(intake)));
        } catch (DataIntegrityViolationException e) {
            throw new IntakeConflictException(e);
        }
    }

    @Override
    public void update(InventoryIntake intake) {
        entityManager.detach(jpaRepository.saveAndFlush(mapper.toEntity(intake)));
    }

    @Override
    public Optional<InventoryIntake> findById(String id) {
        return jpaRepository.findById(id).map(this::read);
    }

    @Override
    public Optional<InventoryIntake> lockById(String id) {
        return jpaRepository.lockForUpdate(id).flatMap(jpaRepository::findById).map(this::read);
    }

    @Override
    public List<InventoryIntake> findByLeadId(String leadId) {
        return jpaRepository.findByLeadIdOrderByIntakeNoAsc(leadId).stream().map(this::read).toList();
    }

    @Override
    public List<InventoryIntake> findCompletedByResult(String propertyId) {
        return jpaRepository.findByResultPropertyIdAndStatus(propertyId, IntakeStatus.COMPLETED).stream().map(this::read).toList();
    }

    @Override
    public List<InventoryIntake> findOpenOfWard(String wardCode) {
        return jpaRepository.findByWardCodeAndStatusNotIn(wardCode, ENDED).stream().map(this::read).toList();
    }

    @Override
    public int nextIntakeNo(String leadId) {
        return jpaRepository.nextIntakeNo(leadId);
    }

    private InventoryIntake read(InventoryIntakeEntity entity) {
        entityManager.detach(entity);
        return mapper.toDomain(entity);
    }
}
