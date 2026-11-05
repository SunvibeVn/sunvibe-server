package vn.sunvibe.auth.core.infrastructure.persistence.repository.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.auth.EmailNotificationEntity;

import java.time.Instant;
import java.util.List;

/**
 * Spring Data access to {@link EmailNotificationEntity}.
 */
public interface EmailNotificationJpaRepository extends JpaRepository<EmailNotificationEntity, Long> {

    /** The condition sits on the locked row itself, so a row another worker just moved is not claimed again (SV-093). */
    @Query(value = "select * from auth_email_notifications where next_attempt_at <= :now"
            + " order by next_attempt_at limit :limit for update skip locked", nativeQuery = true)
    List<EmailNotificationEntity> findDueForClaim(@Param("now") Instant now, @Param("limit") int limit);

}
