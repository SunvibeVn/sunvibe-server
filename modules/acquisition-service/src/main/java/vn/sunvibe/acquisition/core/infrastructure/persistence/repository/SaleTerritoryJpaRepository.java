package vn.sunvibe.acquisition.core.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.acquisition.core.infrastructure.persistence.entity.SaleTerritoryEntity;

import java.time.Instant;

/**
 * Spring Data JPA Repository Interface - used by SaleTerritoryRepositoryAdapter
 * (implementation of SaleTerritoryDomainRepository).
 */
@Repository
public interface SaleTerritoryJpaRepository extends JpaRepository<SaleTerritoryEntity, String> {

    /**
     * Insert the ward or replace its person in one statement, so two calls at the same time on a new ward never fail
     * on the primary key (a save would select first, then insert, and lose that race). A modifying query joins the
     * transaction of the caller or, when there is none, runs in a transaction of its own.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO sale_territories (ward_code, user_id, assigned_by, assigned_at)
            VALUES (:wardCode, :userId, :assignedBy, :assignedAt)
            ON CONFLICT (ward_code) DO UPDATE
            SET user_id = EXCLUDED.user_id, assigned_by = EXCLUDED.assigned_by, assigned_at = EXCLUDED.assigned_at""",
            nativeQuery = true)
    void upsert(@Param("wardCode") String wardCode, @Param("userId") String userId,
                @Param("assignedBy") String assignedBy, @Param("assignedAt") Instant assignedAt);
}
