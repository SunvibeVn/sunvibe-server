package vn.sunvibe.acquisition.core.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.sunvibe.acquisition.core.infrastructure.persistence.entity.OwnerLeadAccessLogEntity;

import java.util.List;

/**
 * Spring Data JPA Repository Interface - used by OwnerLeadAccessLogRepositoryAdapter
 * (implementation of OwnerLeadAccessLogDomainRepository).
 */
@Repository
public interface OwnerLeadAccessLogJpaRepository extends JpaRepository<OwnerLeadAccessLogEntity, Long> {

    // The Snowflake id grows with time, so it orders accesses of the same instant
    List<OwnerLeadAccessLogEntity> findByLeadIdOrderByAccessedAtAscIdAsc(String leadId);
}
