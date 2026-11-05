package vn.sunvibe.property.core.infrastructure.persistence.adapter.intake;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.property.core.domain.model.intake.UnitMapping;
import vn.sunvibe.property.core.domain.repository.IntakeUnitMappingDomainRepository;
import vn.sunvibe.property.core.infrastructure.persistence.entity.intake.IntakeUnitMappingEntity;
import vn.sunvibe.property.core.infrastructure.persistence.mapper.manual.IntakeEntityMapper;
import vn.sunvibe.property.core.infrastructure.persistence.repository.intake.IntakeUnitMappingJpaRepository;

import java.util.Collection;
import java.util.List;

/** Repository Implementation - the unit mappings. As for intake rows, no entity stays managed. */
@Service
@RequiredArgsConstructor
public class IntakeUnitMappingRepositoryAdapter implements IntakeUnitMappingDomainRepository {

    private final IntakeUnitMappingJpaRepository jpaRepository;
    private final IntakeEntityMapper mapper;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void saveAll(List<UnitMapping> mappings) {
        mappings.stream().map(mapper::toEntity).map(jpaRepository::saveAndFlush).forEach(entityManager::detach);
    }

    @Override
    public List<UnitMapping> findByIntakeId(String intakeId) {
        return jpaRepository.findByIntakeIdOrderBySpaceIdAsc(intakeId).stream().map(this::read).toList();
    }

    @Override
    public List<UnitMapping> findByUnitIds(Collection<String> unitIds) {
        return unitIds.isEmpty() ? List.of()
                : jpaRepository.findByUnitIdIn(unitIds).stream().map(this::read).toList();
    }

    private UnitMapping read(IntakeUnitMappingEntity entity) {
        entityManager.detach(entity);
        return mapper.toDomain(entity);
    }
}
