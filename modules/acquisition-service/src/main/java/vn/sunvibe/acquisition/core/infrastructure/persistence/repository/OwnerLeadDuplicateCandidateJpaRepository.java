package vn.sunvibe.acquisition.core.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.sunvibe.acquisition.core.infrastructure.persistence.entity.OwnerLeadDuplicateCandidateEntity;
import vn.sunvibe.acquisition.core.infrastructure.persistence.entity.OwnerLeadDuplicateCandidateId;
import vn.sunvibe.acquisition.core.infrastructure.persistence.entity.OwnerLeadEntity;

import java.util.Collection;
import java.util.List;

/**
 * Spring Data JPA Repository Interface - used by DuplicateCandidateRepositoryAdapter
 * (implementation of DuplicateCandidateDomainRepository).
 * <p>
 * In the visibility queries a viewer who sees every lead passes {@code allVisible = true} and any user id; any other
 * viewer passes {@code false} and sees only the leads assigned to {@code userId}.
 */
@Repository
public interface OwnerLeadDuplicateCandidateJpaRepository
        extends JpaRepository<OwnerLeadDuplicateCandidateEntity, OwnerLeadDuplicateCandidateId> {

    /** Leads of the list that point to a visible candidate. */
    @Query("""
            select distinct c.id.leadId from OwnerLeadDuplicateCandidateEntity c, OwnerLeadEntity o
            where c.id.leadId in :leadIds and o.id = c.id.candidateLeadId
              and (:allVisible = true or o.assignedUserId = :userId)""")
    List<String> findLeadIdsPointingToVisibleCandidate(@Param("leadIds") Collection<String> leadIds,
                                                       @Param("allVisible") boolean allVisible,
                                                       @Param("userId") String userId);

    /** Leads of the list that a visible lead points to. */
    @Query("""
            select distinct c.id.candidateLeadId from OwnerLeadDuplicateCandidateEntity c, OwnerLeadEntity o
            where c.id.candidateLeadId in :leadIds and o.id = c.id.leadId
              and (:allVisible = true or o.assignedUserId = :userId)""")
    List<String> findLeadIdsPointedToByVisibleLead(@Param("leadIds") Collection<String> leadIds,
                                                   @Param("allVisible") boolean allVisible,
                                                   @Param("userId") String userId);

    /** The visible leads linked to the lead in either direction, oldest first. */
    @Query("""
            select o from OwnerLeadEntity o
            where (o.id in (select c.id.candidateLeadId from OwnerLeadDuplicateCandidateEntity c
                            where c.id.leadId = :leadId)
                or o.id in (select c.id.leadId from OwnerLeadDuplicateCandidateEntity c
                            where c.id.candidateLeadId = :leadId))
              and (:allVisible = true or o.assignedUserId = :userId)
            order by o.createdAt asc, o.id asc""")
    List<OwnerLeadEntity> findVisibleCandidates(@Param("leadId") String leadId,
                                                @Param("allVisible") boolean allVisible,
                                                @Param("userId") String userId);
}
