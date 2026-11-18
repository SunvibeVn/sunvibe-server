package vn.sunvibe.outbox;

import jakarta.persistence.EntityManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.logging.CorrelationId;
import vn.sunvibe.shared.outbox.EventOutboxPort;
import vn.sunvibe.shared.outbox.OutboxEvent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.TreeMap;

/**
 * The adapter of {@link EventOutboxPort} (ADR-014 decisions 2, 3; E05-S5b S5b-D2): persists one
 * {@link OutboxEventEntity} per subscribed consumer through the caller's {@code EntityManager}, so the rows use the
 * transaction-bound connection and commit or roll back with the caller's change. No flush and no catch: a failure
 * belongs to the caller's transaction.
 */
public class EventOutboxAdapter implements EventOutboxPort {

    /** The furthest due time an event may carry (ADR-014 decision 2). */
    static final Duration MAX_DUE_AHEAD = Duration.ofDays(800);

    private final EntityManager entityManager;
    private final IdGenerator<Long> snowflake;
    private final OutboxSubscriptions subscriptions;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    EventOutboxAdapter(EntityManager entityManager, IdGenerator<Long> snowflake, OutboxSubscriptions subscriptions,
                       ObjectMapper objectMapper, Clock clock) {
        this.entityManager = entityManager;
        this.snowflake = snowflake;
        this.subscriptions = subscriptions;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void append(OutboxEvent event) {
        // Without a transaction the write would auto-commit; in a read-only one JPA would drop it silently
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("an outbox event is appended only inside a read-write transaction");
        }
        subscriptions.check(event);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Instant due = now;
        if (event.dueAt() != null) {
            Instant dueAt = event.dueAt().truncatedTo(ChronoUnit.MICROS);
            if (dueAt.isAfter(now.plus(MAX_DUE_AHEAD))) {
                throw new IllegalArgumentException("an outbox event is due at most 800 days ahead");
            }
            if (dueAt.isAfter(now)) {
                due = dueAt;
            }
        }
        List<String> consumers = subscriptions.consumersOf(event.type());
        if (consumers.isEmpty()) {
            return;
        }
        String payload = objectMapper.writeValueAsString(new TreeMap<>(event.payload()));
        String correlationId = CorrelationId.current();
        if (!CorrelationId.isValid(correlationId)) {
            correlationId = null;
        }
        long eventId = snowflake.generateId();
        for (String consumer : consumers) {
            entityManager.persist(OutboxEventEntity.builder()
                    .id(snowflake.generateId())
                    .eventId(eventId)
                    .eventType(event.type())
                    .eventVersion(event.version())
                    .consumer(consumer)
                    .sourceModule(event.sourceModule())
                    .aggregateType(event.aggregateType())
                    .aggregateId(event.aggregateId())
                    .payload(payload)
                    .correlationId(correlationId)
                    .occurredAt(now)
                    .status("PENDING")
                    .attempts(0)
                    .nextAttemptAt(due)
                    .build());
        }
    }
}
