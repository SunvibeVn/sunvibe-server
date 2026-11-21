package vn.sunvibe.brokerage.core.infrastructure.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.sunvibe.brokerage.core.infrastructure.persistence.entity.AgreementSuspensionEntity;

import java.time.Instant;
import java.util.List;

/** Spring Data JPA Repository - the suspension records of the agreements (E05-S8 spec 5.3). */
public interface AgreementSuspensionJpaRepository extends JpaRepository<AgreementSuspensionEntity, String> {

    /**
     * Closes the agreement's open record in one guarded statement (E05-S8 spec S8-D6): only a record still open is
     * matched, so the count tells whether one was. The adapter keeps no record entity managed, so no managed copy can
     * go stale; pending writes are flushed first.
     */
    @Modifying(flushAutomatically = true)
    @Query("update AgreementSuspensionEntity s set s.releasedBy = :releasedBy, s.releasedAt = :releasedAt,"
            + " s.releaseReason = :reason where s.agreementId = :agreementId and s.releasedAt is null")
    int release(@Param("agreementId") String agreementId, @Param("releasedBy") String releasedBy,
                @Param("releasedAt") Instant releasedAt, @Param("reason") String reason);

    /** The agreement's records, newest suspension first, then the greater id; one statement on its index. */
    @Query("select s from AgreementSuspensionEntity s where s.agreementId = :agreementId"
            + " order by s.suspendedAt desc, s.id desc")
    List<AgreementSuspensionEntity> findNewest(@Param("agreementId") String agreementId, Pageable page);
}
