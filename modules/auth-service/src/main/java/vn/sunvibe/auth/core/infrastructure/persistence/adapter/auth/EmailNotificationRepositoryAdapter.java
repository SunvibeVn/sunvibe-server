package vn.sunvibe.auth.core.infrastructure.persistence.adapter.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.auth.core.domain.model.auth.EmailNotification;
import vn.sunvibe.auth.core.domain.repository.auth.EmailNotificationRepository;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.auth.EmailNotificationEntity;
import vn.sunvibe.auth.core.infrastructure.persistence.repository.auth.EmailNotificationJpaRepository;

import java.time.Instant;
import java.util.List;

/**
 * JPA implementation of {@link EmailNotificationRepository}.
 */
@Service
@RequiredArgsConstructor
public class EmailNotificationRepositoryAdapter implements EmailNotificationRepository {

    private final EmailNotificationJpaRepository jpaRepository;

    @Override
    public void save(EmailNotification notification) {
        EmailNotificationEntity entity = new EmailNotificationEntity();
        entity.setId(notification.getId());
        entity.setUserId(notification.getUserId());
        entity.setKind(notification.getKind());
        entity.setDestination(notification.getDestination());
        entity.setCreatedAt(notification.getCreatedAt());
        entity.setAttempts(notification.getAttempts());
        entity.setNextAttemptAt(notification.getNextAttemptAt());
        jpaRepository.save(entity);
    }

    @Override
    public List<EmailNotification> claimDue(Instant now, int limit, Instant claimedUntil) {
        return jpaRepository.findDueForClaim(now, limit).stream().map(entity -> {
            entity.setNextAttemptAt(claimedUntil);
            return toDomain(entity);
        }).toList();
    }

    @Override
    public void delete(Long id) {
        jpaRepository.deleteById(id);
    }

    private static EmailNotification toDomain(EmailNotificationEntity e) {
        return EmailNotification.load(e.getId(), e.getUserId(), e.getKind(), e.getDestination(), e.getCreatedAt(),
                e.getAttempts(), e.getNextAttemptAt());
    }
}
