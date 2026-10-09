package vn.sunvibe.auth.core.infrastructure.persistence.repository.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditEventType;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.audit.AuthAuditEventEntity;

import java.time.Instant;
import java.util.List;

public interface AuthAuditEventJpaRepository extends JpaRepository<AuthAuditEventEntity, Long> {

    List<AuthAuditEventEntity> findByUserIdOrderByOccurredAtDesc(String userId);

    List<AuthAuditEventEntity> findByEventTypeOrderByOccurredAtDesc(AuditEventType eventType);

    @Modifying
    @Query("DELETE FROM AuthAuditEventEntity e WHERE e.occurredAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);

}
