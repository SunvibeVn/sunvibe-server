package vn.sunvibe.property.core.infrastructure.persistence.repository.intake;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.sunvibe.property.core.domain.model.intake.CaseStatus;
import vn.sunvibe.property.core.infrastructure.persistence.entity.intake.DuplicateCaseEntity;

import java.util.List;
import java.util.Optional;

/** Spring Data access to duplicate cases, used by its adapter only. */
@Repository
public interface DuplicateCaseJpaRepository extends JpaRepository<DuplicateCaseEntity, String> {

    @Query(value = "select id from inventory_duplicate_cases where id = :id for no key update", nativeQuery = true)
    Optional<String> lockForUpdate(@Param("id") String id);

    Optional<DuplicateCaseEntity> findByPairKeyAndStatusNot(String pairKey, CaseStatus status);

    @Query("select c from DuplicateCaseEntity c where c.intakeId = :intakeId or c.candidateIntakeId = :intakeId"
            + " order by c.createdAt, c.id")
    List<DuplicateCaseEntity> findForIntake(@Param("intakeId") String intakeId);

    List<DuplicateCaseEntity> findByCandidatePropertyIdOrderByCreatedAtAscIdAsc(String propertyId);

    List<DuplicateCaseEntity> findByStatusOrderByCreatedAtAscIdAsc(CaseStatus status);
}
