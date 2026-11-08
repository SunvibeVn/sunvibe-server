package vn.sunvibe.property.core.infrastructure.persistence.repository.intake;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.sunvibe.property.core.domain.model.intake.IntakeStatus;
import vn.sunvibe.property.core.infrastructure.persistence.entity.intake.InventoryIntakeEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Spring Data access to intake rows, used by its adapter only. */
@Repository
public interface InventoryIntakeJpaRepository extends JpaRepository<InventoryIntakeEntity, String> {

    /**
     * The row lock of an intake command (property-create intake spec 5.3): native and scalar, because PESSIMISTIC_WRITE takes FOR UPDATE, which
     * blocks the key-share lock of an FK insert, and an entity could outlive the lock with an old state.
     */
    @Query(value = "select id from inventory_intakes where id = :id for no key update", nativeQuery = true)
    Optional<String> lockForUpdate(@Param("id") String id);

    List<InventoryIntakeEntity> findByLeadIdOrderByIntakeNoAsc(String leadId);

    List<InventoryIntakeEntity> findByResultPropertyIdAndStatus(String resultPropertyId, IntakeStatus status);

    List<InventoryIntakeEntity> findByWardCodeAndStatusNotIn(String wardCode, Collection<IntakeStatus> statuses);

    @Query("select coalesce(max(i.intakeNo), 0) + 1 from InventoryIntakeEntity i where i.leadId = :leadId")
    int nextIntakeNo(@Param("leadId") String leadId);
}
