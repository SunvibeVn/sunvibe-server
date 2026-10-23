package vn.sunvibe.acquisition.core.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;
import vn.sunvibe.acquisition.core.infrastructure.persistence.entity.OwnerLeadActivityEntity;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Spring Data JPA Repository Interface - used by OwnerLeadActivityRepositoryAdapter
 * (implementation of OwnerLeadActivityDomainRepository).
 */
@Repository
public interface OwnerLeadActivityJpaRepository extends JpaRepository<OwnerLeadActivityEntity, Long> {

    // The Snowflake id grows with time, so it orders activities of the same instant
    List<OwnerLeadActivityEntity> findByLeadIdOrderByOccurredAtAscIdAsc(String leadId);

    /** One row per lead that has activities: the lead id and its largest occurredAt. */
    @Query("select a.leadId as leadId, max(a.occurredAt) as latest from OwnerLeadActivityEntity a"
            + " where a.leadId in :leadIds group by a.leadId")
    List<LatestActivity> findLatestOccurredAtPerLead(@Param("leadIds") Collection<String> leadIds);

    /** The largest occurredAt of the lead's entries that moved it to the status, or null when none did. */
    @Query("select max(a.occurredAt) from OwnerLeadActivityEntity a"
            + " where a.leadId = :leadId and a.toStatus = :toStatus")
    Instant findLatestOccurredAtOfStatus(@Param("leadId") String leadId,
                                         @Param("toStatus") OwnerLeadStatus toStatus);

    interface LatestActivity {
        String getLeadId();

        Instant getLatest();
    }
}
