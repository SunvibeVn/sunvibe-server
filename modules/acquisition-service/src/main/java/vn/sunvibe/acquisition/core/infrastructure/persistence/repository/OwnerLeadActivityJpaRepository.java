package vn.sunvibe.acquisition.core.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.sunvibe.acquisition.core.infrastructure.persistence.entity.OwnerLeadActivityEntity;

import java.util.List;

/**
 * Spring Data JPA Repository Interface - used by OwnerLeadActivityRepositoryAdapter
 * (implementation of OwnerLeadActivityDomainRepository).
 */
@Repository
public interface OwnerLeadActivityJpaRepository extends JpaRepository<OwnerLeadActivityEntity, Long> {

    // The Snowflake id grows with time, so it orders activities of the same instant
    List<OwnerLeadActivityEntity> findByLeadIdOrderByOccurredAtAscIdAsc(String leadId);
}
