package vn.sunvibe.property.core.infrastructure.persistence.adapter.intake;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.property.core.domain.model.intake.IntakeSuspension;
import vn.sunvibe.property.core.domain.repository.IntakeSuspensionDomainRepository;
import vn.sunvibe.property.core.infrastructure.persistence.entity.intake.IntakeSuspensionEntity;
import vn.sunvibe.property.core.infrastructure.persistence.mapper.manual.IntakeEntityMapper;
import vn.sunvibe.property.core.infrastructure.persistence.repository.intake.IntakeSuspensionJpaRepository;

import java.util.List;
import java.util.Optional;

/** Repository Implementation - suspensions. As for intake rows, no entity stays managed. */
@Service
@RequiredArgsConstructor
public class IntakeSuspensionRepositoryAdapter implements IntakeSuspensionDomainRepository {

    private final IntakeSuspensionJpaRepository jpaRepository;
    private final IntakeEntityMapper mapper;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void save(IntakeSuspension suspension) {
        entityManager.detach(jpaRepository.saveAndFlush(mapper.toEntity(suspension)));
    }

    @Override
    public Optional<IntakeSuspension> findById(String id) {
        return jpaRepository.findById(id).map(this::read);
    }

    @Override
    public List<IntakeSuspension> findByLeadId(String leadId) {
        return jpaRepository.findByLeadIdOrderByCreatedAtAscIdAsc(leadId).stream().map(this::read).toList();
    }

    @Override
    public boolean anyActiveOnUnits(java.util.Collection<String> unitIds) {
        return !unitIds.isEmpty() && jpaRepository.existsByUnitIdInAndReleasedAtIsNull(unitIds);
    }

    private IntakeSuspension read(IntakeSuspensionEntity entity) {
        entityManager.detach(entity);
        return mapper.toDomain(entity);
    }
}
