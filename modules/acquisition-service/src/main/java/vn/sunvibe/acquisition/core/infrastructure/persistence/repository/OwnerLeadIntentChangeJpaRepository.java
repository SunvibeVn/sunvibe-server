package vn.sunvibe.acquisition.core.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.sunvibe.acquisition.core.infrastructure.persistence.entity.OwnerLeadIntentChangeEntity;

import java.util.List;

/**
 * Spring Data JPA Repository Interface - used by OwnerLeadIntentChangeRepositoryAdapter
 * (implementation of OwnerLeadIntentChangeDomainRepository).
 */
@Repository
public interface OwnerLeadIntentChangeJpaRepository extends JpaRepository<OwnerLeadIntentChangeEntity, Long> {

    // The Snowflake id grows with time, so it orders changes of the same instant
    List<OwnerLeadIntentChangeEntity> findByLeadIdOrderByOccurredAtAscIdAsc(String leadId);
}
