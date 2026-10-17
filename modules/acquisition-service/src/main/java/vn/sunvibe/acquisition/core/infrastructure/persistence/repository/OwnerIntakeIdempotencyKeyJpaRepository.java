package vn.sunvibe.acquisition.core.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.sunvibe.acquisition.core.infrastructure.persistence.entity.OwnerIntakeIdempotencyKeyEntity;

import java.time.Instant;

/**
 * Spring Data JPA Repository Interface - used by IdempotencyKeyRepositoryAdapter
 * (implementation of IdempotencyKeyDomainRepository).
 */
@Repository
public interface OwnerIntakeIdempotencyKeyJpaRepository
        extends JpaRepository<OwnerIntakeIdempotencyKeyEntity, String> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM OwnerIntakeIdempotencyKeyEntity e WHERE e.createdAt < :cutoff")
    int deleteCreatedBefore(@Param("cutoff") Instant cutoff);
}
