package vn.sunvibe.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.LongAdder;

/**
 * The outbox worker (ADR-014 decisions 5, 8; E05-S5b spec 4.5 to 4.8). Each consumer's run claims at most a batch of
 * that consumer's due rows under a lease, delivers them one after another on the consumer's delivery thread with no
 * transaction and a timeout, and marks each by id and lease token; a mark that changes no row is a lost lease, logged
 * and counted (SV-122). A run ends at its first time-out and gives back the rest of its claim. Every statement is its
 * own auto-committed unit; time comes from the outbox's clock, truncated to microseconds.
 *
 * <p>Log lines carry fixed codes and ids only: never a payload, an aggregate id, an exception message or a stack trace.
 */
public class OutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);
    private static final TypeReference<Map<String, String>> PAYLOAD = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final OutboxSubscriptions subscriptions;
    private final Map<String, OutboxConsumer> consumers;
    private final OutboxScheduling scheduling;
    private final Clock clock;
    private final Limits limits;

    // Several consumers' runs add to these at once (spec 4.7)
    private final LongAdder delivered = new LongAdder();
    private final LongAdder retried = new LongAdder();
    private final LongAdder dead = new LongAdder();
    private final LongAdder lostLeases = new LongAdder();
    private final LongAdder timeouts = new LongAdder();
    private final LongAdder released = new LongAdder();
    private final LongAdder removalFailures = new LongAdder();

    /**
     * The worker's limits (S5b-D5).
     *
     * @param batchSize     rows per consumer and run
     * @param timeout       the wait for one delivery
     * @param leaseMargin   added to batch x timeout to give the lease
     * @param doneRetention how long a {@code DONE} row is kept
     * @param queueAgeAlert the age of the oldest due row that raises an alert, and of an unknown consumer's row that
     *                      goes {@code DEAD}
     */
    public record Limits(int batchSize, Duration timeout, Duration leaseMargin, Duration doneRetention,
                         Duration queueAgeAlert) {

        /** batch x timeout + margin: one consumer's run delivers its whole claim inside it. */
        public Duration lease() {
            return timeout.multipliedBy(batchSize).plus(leaseMargin);
        }
    }

    /** One claimed row as the claim's {@code RETURNING} gives it; {@code attempts} after the claim's increment. */
    record ClaimedRow(long id, String token, long eventId, String eventType, int eventVersion, String consumer,
                      String aggregateType, String aggregateId, Map<String, String> payload, String correlationId,
                      Instant occurredAt, int attempts, String lastErrorCode) {
    }

    public OutboxWorker(JdbcTemplate jdbc, ObjectMapper objectMapper, OutboxSubscriptions subscriptions,
                        Map<String, OutboxConsumer> consumers, OutboxScheduling scheduling, Clock clock, Limits limits) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.subscriptions = subscriptions;
        this.consumers = Map.copyOf(consumers);
        this.scheduling = scheduling;
        this.clock = clock;
        this.limits = limits;
    }

    /**
     * One delivery run (spec 4.5 step 1): for each consumer of the list, in name order, starts its run on its own run
     * thread when both its threads are idle; a consumer whose run or delivery still goes on is left as it is, claimed by
     * none. Waits for no run, so one consumer never delays another (S5b-D15).
     *
     * @return the runs started, by consumer name
     */
    public Map<String, Future<?>> deliverDue() {
        Map<String, Future<?>> started = new LinkedHashMap<>();
        for (String name : subscriptions.consumers()) {
            if (!scheduling.idle(name)) {
                continue;
            }
            try {
                started.put(name, scheduling.submitRun(name, () -> runConsumer(name)));
            } catch (RejectedExecutionException e) {
                // Counts as busy: nothing was claimed
            }
        }
        return started;
    }

    /**
     * One consumer's run (spec 4.5 steps 2 to 8): claim, then deliver each row and mark it. A time-out or a rejected
     * delivery submit ends the run and releases the rows not yet delivered; an interrupt (shutdown) ends it with the
     * rows left under their lease.
     */
    public void deliverConsumer(String consumerName) {
        OutboxConsumer consumer = consumers.get(consumerName);
        if (consumer == null) {
            throw new IllegalStateException("no outbox consumer bean for " + consumerName);
        }
        List<ClaimedRow> rows = claim(consumerName, now());
        for (int i = 0; i < rows.size(); i++) {
            ClaimedRow row = rows.get(i);
            Future<Object> delivery;
            try {
                delivery = scheduling.submitDelivery(consumerName, () -> {
                    consumer.deliver(toDelivery(row));
                    return null;
                });
            } catch (RejectedExecutionException e) {
                // Not attempted: given back with the rest of the claim, no line of its own (S5b-D15 (2))
                release(rows.subList(i, rows.size()));
                return;
            }
            try {
                delivery.get(limits.timeout().toNanos(), TimeUnit.NANOSECONDS);
                markDone(row);
            } catch (ExecutionException e) {
                failed(consumer, row, OutboxErrorCode.CONSUMER_ERROR, e.getCause());
            } catch (TimeoutException e) {
                // The cancel interrupts the delivery; it may still run, so its thread stays busy until it ends
                delivery.cancel(true);
                timeouts.increment();
                failed(consumer, row, OutboxErrorCode.DELIVERY_TIMEOUT, null);
                release(rows.subList(i + 1, rows.size()));
                return;
            } catch (InterruptedException e) {
                // Shutdown: no mark, no release; the rows are claimed again once their lease ends
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** Claims at most a batch of the consumer's rows due at {@code now}, oldest first, under one fresh token. */
    List<ClaimedRow> claim(String consumer, Instant now) {
        Instant at = now.truncatedTo(ChronoUnit.MICROS);
        String token = UUID.randomUUID().toString();
        List<ClaimedRow> rows = jdbc.query(OutboxSql.CLAIM_DUE, (rs, n) -> claimedRow(rs, token), token,
                time(at.plus(limits.lease())), consumer, time(at), limits.batchSize(), consumer, time(at));
        return rows.stream().sorted(Comparator.comparingLong(ClaimedRow::id)).toList();
    }

    long deliveredCount() {
        return delivered.sum();
    }

    long retriedCount() {
        return retried.sum();
    }

    long deadCount() {
        return dead.sum();
    }

    long lostLeaseCount() {
        return lostLeases.sum();
    }

    long timeoutCount() {
        return timeouts.sum();
    }

    long releasedCount() {
        return released.sum();
    }

    long removalFailureCount() {
        return removalFailures.sum();
    }

    /** A run that throws (a database error, say) leaves its unmarked rows under their lease; the next poll goes on. */
    private void runConsumer(String name) {
        try {
            deliverConsumer(name);
        } catch (RuntimeException e) {
            log.error("OUTBOX_RUN_FAILED consumer={} exception={}", name, e.getClass().getName());
        }
    }

    private void markDone(ClaimedRow row) {
        if (jdbc.update(OutboxSql.MARK_DONE, time(now()), row.id(), row.token()) == 1) {
            delivered.increment();
        } else {
            leaseLost(row);
        }
    }

    /**
     * A failed attempt under the consumer's policy. A removal consumer's first recorded failure (the claim returned no
     * {@code last_error_code}) alerts before the mark, whether or not the mark then finds the lease lost (S5b-D13).
     */
    private void failed(OutboxConsumer consumer, ClaimedRow row, OutboxErrorCode code, Throwable cause) {
        boolean removalAlert = consumer.retryPolicy() == OutboxRetryPolicy.REMOVAL && row.lastErrorCode() == null;
        if (removalAlert) {
            removalFailures.increment();
            log.error("OUTBOX_REMOVAL_FAILED rowId={} eventId={} eventType={} consumer={} attempt={} errorCode={}{}",
                    row.id(), row.eventId(), row.eventType(), row.consumer(), row.attempts(), code, exception(cause));
        }
        Instant now = now();
        Optional<Duration> retryAfter = consumer.retryPolicy().retryAfter(row.attempts());
        if (retryAfter.isPresent()) {
            if (jdbc.update(OutboxSql.MARK_RETRY, time(now.plus(retryAfter.get())), code.name(), row.id(),
                    row.token()) != 1) {
                leaseLost(row);
                return;
            }
            retried.increment();
            if (!removalAlert) {
                log.warn("OUTBOX_DELIVERY_RETRY rowId={} eventId={} eventType={} consumer={} attempt={} errorCode={}{}",
                        row.id(), row.eventId(), row.eventType(), row.consumer(), row.attempts(), code,
                        exception(cause));
            }
        } else {
            if (jdbc.update(OutboxSql.MARK_DEAD, code.name(), row.id(), row.token()) != 1) {
                leaseLost(row);
                return;
            }
            dead.increment();
            log.error("OUTBOX_DEAD rowId={} eventId={} eventType={} consumer={} attempt={} errorCode={}{}",
                    row.id(), row.eventId(), row.eventType(), row.consumer(), row.attempts(), code, exception(cause));
        }
    }

    /** Gives back rows of one claim not yet attempted; each id the statement does not return is a lost lease. */
    private void release(List<ClaimedRow> rows) {
        if (rows.isEmpty()) {
            return;
        }
        Long[] ids = rows.stream().map(ClaimedRow::id).toArray(Long[]::new);
        OffsetDateTime now = time(now());
        String token = rows.get(0).token();
        List<Long> back = jdbc.query(connection -> {
            var statement = connection.prepareStatement(OutboxSql.RELEASE);
            statement.setObject(1, now);
            statement.setArray(2, connection.createArrayOf("bigint", ids));
            statement.setString(3, token);
            return statement;
        }, (rs, n) -> rs.getLong("id"));
        released.add(back.size());
        Set<Long> returned = new HashSet<>(back);
        for (ClaimedRow row : rows) {
            if (!returned.contains(row.id())) {
                leaseLost(row);
            }
        }
    }

    private void leaseLost(ClaimedRow row) {
        lostLeases.increment();
        log.warn("OUTBOX_LEASE_LOST rowId={} eventId={} eventType={} consumer={} attempt={}",
                row.id(), row.eventId(), row.eventType(), row.consumer(), row.attempts());
    }

    private ClaimedRow claimedRow(ResultSet rs, String token) throws SQLException {
        return new ClaimedRow(rs.getLong("id"), token, rs.getLong("event_id"), rs.getString("event_type"),
                rs.getInt("event_version"), rs.getString("consumer"), rs.getString("aggregate_type"),
                rs.getString("aggregate_id"), objectMapper.readValue(rs.getString("payload"), PAYLOAD),
                rs.getString("correlation_id"), rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                rs.getInt("attempts"), rs.getString("last_error_code"));
    }

    private static OutboxConsumer.Delivery toDelivery(ClaimedRow row) {
        return new OutboxConsumer.Delivery(row.eventId(), row.eventType(), row.eventVersion(), row.aggregateType(),
                row.aggregateId(), Map.copyOf(row.payload()), row.occurredAt(), row.correlationId(), row.attempts());
    }

    /** The exception by its class name only: its message may carry a consumer's data. */
    private static String exception(Throwable cause) {
        return cause == null ? "" : " exception=" + cause.getClass().getName();
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private static OffsetDateTime time(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
