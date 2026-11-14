package vn.sunvibe.acquisition.core.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadIntent;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;
import vn.sunvibe.acquisition.core.infrastructure.persistence.entity.OwnerLeadEntity;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA Repository Interface - used by OwnerLeadRepositoryAdapter
 * (implementation of OwnerLeadDomainRepository).
 */
@Repository
public interface OwnerLeadJpaRepository
        extends JpaRepository<OwnerLeadEntity, String>, JpaSpecificationExecutor<OwnerLeadEntity> {

    boolean existsByRequestCode(String requestCode);

    /** Whether any lead names this one as its original (index idx_owner_leads_duplicate_of). */
    boolean existsByDuplicateOfLeadId(String duplicateOfLeadId);

    /** The lead with a PostgreSQL {@code FOR SHARE} row lock (survey-checklist spec 5.3). */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select l from OwnerLeadEntity l where l.id = :id")
    Optional<OwnerLeadEntity> findForShareById(@Param("id") String id);

    /**
     * The lead with a PostgreSQL {@code FOR NO KEY UPDATE} row lock (survey-checklist submission spec 5.4): native,
     * because {@code PESSIMISTIC_WRITE} takes {@code FOR UPDATE}, which blocks the key-share locks of activity inserts.
     */
    @Query(value = "select * from owner_leads where id = :id for no key update", nativeQuery = true)
    Optional<OwnerLeadEntity> findForNoKeyUpdateById(@Param("id") String id);

    /** The id and intent cycle of a lead, without loading it (survey-checklist submission plan PD-4). */
    interface CycleRow {
        String getId();

        int getIntentCycle();

        OwnerLeadStatus getStatus();

        String getAssignedUserId();

        String getRequestCode();
    }

    List<CycleRow> findByStatusIn(Collection<OwnerLeadStatus> statuses);

    List<CycleRow> findByStatusInAndAssignedUserId(Collection<OwnerLeadStatus> statuses, String assignedUserId);

    Optional<OwnerLeadEntity> findFirstByPhoneNormalizedAndIntentAndWardCodeAndAddressKeyAndCreatedAtGreaterThanEqualOrderByCreatedAtDescIdDesc(
            String phoneNormalized, OwnerLeadIntent intent, String wardCode, String addressKey,
            Instant createdAtOrAfter);

    @Query("select l.id from OwnerLeadEntity l where l.phoneNormalized = :phoneNormalized and l.status in :statuses")
    List<String> findIdsByPhoneNormalizedAndStatusIn(@Param("phoneNormalized") String phoneNormalized,
                                                     @Param("statuses") Collection<OwnerLeadStatus> statuses);
}
