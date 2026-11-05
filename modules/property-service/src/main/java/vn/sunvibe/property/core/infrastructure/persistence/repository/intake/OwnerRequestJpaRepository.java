package vn.sunvibe.property.core.infrastructure.persistence.repository.intake;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.sunvibe.property.core.infrastructure.persistence.entity.intake.OwnerRequestEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Spring Data access to owner requests, used by its adapter only. */
@Repository
public interface OwnerRequestJpaRepository extends JpaRepository<OwnerRequestEntity, String> {

    @Query(value = "select id from inventory_owner_requests where id = :id for no key update", nativeQuery = true)
    Optional<String> lockForUpdate(@Param("id") String id);

    List<OwnerRequestEntity> findByIntakeIdOrderByCreatedAtAscIdAsc(String intakeId);

    List<OwnerRequestEntity> findByTargetUserIdOrderByCreatedAtDescIdDesc(String targetUserId);

    long countByIntakeIdAndCreatedAtGreaterThanEqual(String intakeId, Instant since);
}
