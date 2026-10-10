package vn.sunvibe.auth.core.infrastructure.persistence.entity.audit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditEventType;

import java.time.Instant;

/**
 * Table {@code auth_audit_events}, created by Flyway migration
 * {@code V202610091600__create_auth_audit_events_table.sql}; keep both in sync.
 * Append-only: rows are never updated, only purged after the retention period.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "auth_audit_events", indexes = {
        @Index(name = "idx_auth_audit_events_user_id", columnList = "user_id, occurred_at"),
        @Index(name = "idx_auth_audit_events_type", columnList = "event_type, occurred_at"),
        @Index(name = "idx_auth_audit_events_ip", columnList = "ip_address, occurred_at"),
        @Index(name = "idx_auth_audit_events_occurred_at", columnList = "occurred_at")
})
public class AuthAuditEventEntity {

    @Id
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private AuditEventType eventType;

    @Column(name = "user_id", length = 26)
    private String userId;

    /** Phone number or e-mail the client used. */
    @Column(name = "subject", length = 100)
    private String subject;

    @Column(name = "details", length = 255)
    private String details;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

}
