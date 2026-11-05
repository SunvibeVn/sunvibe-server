package vn.sunvibe.property.core.infrastructure.persistence.repository.intake;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.sunvibe.property.core.infrastructure.persistence.entity.intake.OwnerProofEntity;

import java.util.List;
import java.util.Optional;

/** Spring Data access to owner proofs, used by its adapter only. */
@Repository
public interface OwnerProofJpaRepository extends JpaRepository<OwnerProofEntity, String> {

    @Query(value = "select id from inventory_owner_proofs where id = :id for no key update", nativeQuery = true)
    Optional<String> lockForUpdate(@Param("id") String id);

    Optional<OwnerProofEntity> findByIntakeIdAndWithdrawnAtIsNullAndVoidedAtIsNull(String intakeId);

    Optional<OwnerProofEntity> findByRequestId(String requestId);

    List<OwnerProofEntity> findByLeadIdOrderByConfirmedAtAscIdAsc(String leadId);

    List<OwnerProofEntity> findByUserIdOrderByConfirmedAtAscIdAsc(String userId);

    @Query("select p from OwnerProofEntity p, OwnerRequestEntity r where r.id = p.requestId"
            + " and r.createdByUserId = :creator order by p.confirmedAt, p.id")
    List<OwnerProofEntity> findByRequestCreator(@Param("creator") String creator);
}
