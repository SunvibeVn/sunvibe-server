package vn.sunvibe.property.core.infrastructure.persistence.repository.intake;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.sunvibe.property.core.infrastructure.persistence.entity.intake.IntakeSuspensionEntity;

import java.util.Collection;
import java.util.List;

/** Spring Data access to suspensions, used by its adapter only. */
@Repository
public interface IntakeSuspensionJpaRepository extends JpaRepository<IntakeSuspensionEntity, String> {

    List<IntakeSuspensionEntity> findByLeadIdOrderByCreatedAtAscIdAsc(String leadId);

    boolean existsByUnitIdInAndReleasedAtIsNull(Collection<String> unitIds);
}
