package vn.sunvibe.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * JPA mapping of {@code outbox_events} (ADR-014 decision 4; E05-S5b S5b-D2): the adapter persists one per subscribed
 * consumer in the caller's transaction, and H2 test contexts create the table from it. The worker reads and marks the
 * rows with native SQL ({@link OutboxSql}). No {@code toString}: the payload and the aggregate id stay out of logs.
 */
@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEventEntity {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "event_version", nullable = false)
    private int eventVersion;

    @Column(name = "consumer", nullable = false, length = 100)
    private String consumer;

    @Column(name = "source_module", nullable = false, length = 40)
    private String sourceModule;

    @Column(name = "aggregate_type", nullable = false, length = 40)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false)
    private String payload;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "status", nullable = false, length = 10)
    private String status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "lease_token", length = 36)
    private String leaseToken;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_error_code", length = 40)
    private String lastErrorCode;
}
