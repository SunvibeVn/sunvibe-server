package vn.sunvibe.auth.core.infrastructure.persistence.entity.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Table {@code auth_email_notifications}, created by Flyway migration
 * {@code V202611051010__create_auth_email_notifications.sql}; keep both in sync.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "auth_email_notifications")
public class EmailNotificationEntity {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false, length = 26)
    private String userId;

    @Column(name = "kind", nullable = false, length = 30)
    private String kind;

    @Column(name = "destination", nullable = false, length = 254)
    private String destination;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

}
