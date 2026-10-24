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

    /** The lead with a PostgreSQL {@code FOR SHARE} row lock (survey-checklist spec 5.3). */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select l from OwnerLeadEntity l where l.id = :id")
    Optional<OwnerLeadEntity> findForShareById(@Param("id") String id);

    Optional<OwnerLeadEntity> findFirstByPhoneNormalizedAndIntentAndWardCodeAndAddressKeyAndCreatedAtGreaterThanEqualOrderByCreatedAtDescIdDesc(
            String phoneNormalized, OwnerLeadIntent intent, String wardCode, String addressKey,
            Instant createdAtOrAfter);

    @Query("select l.id from OwnerLeadEntity l where l.phoneNormalized = :phoneNormalized and l.status in :statuses")
    List<String> findIdsByPhoneNormalizedAndStatusIn(@Param("phoneNormalized") String phoneNormalized,
                                                     @Param("statuses") Collection<OwnerLeadStatus> statuses);
}
