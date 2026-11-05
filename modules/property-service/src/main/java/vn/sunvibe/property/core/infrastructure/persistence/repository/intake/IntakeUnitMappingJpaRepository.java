package vn.sunvibe.property.core.infrastructure.persistence.repository.intake;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.sunvibe.property.core.infrastructure.persistence.entity.intake.IntakeUnitMappingEntity;

import java.util.Collection;
import java.util.List;

/** Spring Data access to the unit mappings, used by its adapter only. */
@Repository
public interface IntakeUnitMappingJpaRepository
        extends JpaRepository<IntakeUnitMappingEntity, IntakeUnitMappingEntity.Key> {

    List<IntakeUnitMappingEntity> findByIntakeIdOrderBySpaceIdAsc(String intakeId);

    List<IntakeUnitMappingEntity> findByUnitIdIn(Collection<String> unitIds);
}
