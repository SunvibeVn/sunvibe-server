package vn.sunvibe.outbox;

import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import vn.sunvibe.shared.generator.id.IdGenerator;

import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The transactional outbox of ADR-014. Its {@code @EntityScan} adds {@code vn.sunvibe.outbox} to the modules' own
 * scans; the registrations add up. It binds {@code app.outbox}, builds the adapter of the port, the worker, its three
 * jobs and its threads, and schedules the jobs once the application is ready when {@code app.outbox.worker.enabled}.
 * Time comes from the system UTC clock, which is no bean (E05-S5b S5b-D11). Start-up fails when the subscription list
 * and the consumer beans disagree, the list names more than 8 consumers, or a limit leaves its bounds (S5b-D5).
 */
@Configuration(proxyBeanMethods = false)
@EntityScan("vn.sunvibe.outbox")
@EnableConfigurationProperties(OutboxConfig.Properties.class)
public class OutboxConfig {

    @Bean
    OutboxSubscriptions outboxSubscriptions() {
        return OutboxSubscriptions.production();
    }

    @Bean
    EventOutboxAdapter eventOutboxAdapter(EntityManager entityManager,
                                          @Qualifier("Snowflake") IdGenerator<Long> snowflake,
                                          OutboxSubscriptions subscriptions, ObjectMapper objectMapper) {
        return new EventOutboxAdapter(entityManager, snowflake, subscriptions, objectMapper, Clock.systemUTC());
    }

    @Bean
    OutboxScheduling outboxScheduling(OutboxSubscriptions subscriptions) {
        return new OutboxScheduling(subscriptions.consumers());
    }

    @Bean
    OutboxWorker outboxWorker(JdbcTemplate jdbc, ObjectMapper objectMapper, OutboxSubscriptions subscriptions,
                              ObjectProvider<OutboxConsumer> consumers, OutboxScheduling scheduling,
                              Properties properties) {
        return new OutboxWorker(jdbc, objectMapper, subscriptions,
                checkWiring(subscriptions, consumers.orderedStream().toList()), scheduling, Clock.systemUTC(),
                properties.limits());
    }

    @Bean
    OutboxDeliveryJob outboxDeliveryJob(OutboxWorker worker) {
        return new OutboxDeliveryJob(worker);
    }

    @Bean
    OutboxCleanupJob outboxCleanupJob(OutboxWorker worker) {
        return new OutboxCleanupJob(worker);
    }

    @Bean
    OutboxStatusJob outboxStatusJob(OutboxWorker worker) {
        return new OutboxStatusJob(worker);
    }

    /** Each job at a fixed delay, its first run one interval after the application is ready; nothing when disabled. */
    @Bean
    ApplicationListener<ApplicationReadyEvent> outboxJobsStarter(Properties properties, OutboxScheduling scheduling,
                                                                 OutboxDeliveryJob delivery, OutboxCleanupJob cleanUp,
                                                                 OutboxStatusJob status) {
        return new ApplicationListener<>() {
            @Override
            public void onApplicationEvent(ApplicationReadyEvent event) {
                if (properties.worker().enabled()) {
                    scheduling.scheduleJobs(delivery, properties.delivery().pollInterval(), cleanUp,
                            properties.cleanup().interval(), status, properties.status().interval());
                }
            }
        };
    }

    /**
     * Every subscribed consumer has exactly one bean and every bean is subscribed; at most 8 consumers.
     *
     * @return the consumer beans by name
     */
    static Map<String, OutboxConsumer> checkWiring(OutboxSubscriptions subscriptions, List<OutboxConsumer> beans) {
        if (subscriptions.consumers().size() > OutboxScheduling.MAX_CONSUMERS) {
            throw new IllegalStateException(
                    "the outbox subscription list names more than " + OutboxScheduling.MAX_CONSUMERS + " consumers");
        }
        Map<String, OutboxConsumer> byName = new TreeMap<>();
        for (OutboxConsumer bean : beans) {
            if (byName.put(bean.name(), bean) != null) {
                throw new IllegalStateException("two outbox consumer beans are named " + bean.name());
            }
        }
        Set<String> subscribed = new HashSet<>(subscriptions.consumers());
        for (String name : subscribed) {
            if (!byName.containsKey(name)) {
                throw new IllegalStateException("the outbox consumer " + name + " is subscribed but has no bean");
            }
        }
        for (String name : byName.keySet()) {
            if (!subscribed.contains(name)) {
                throw new IllegalStateException("the outbox consumer bean " + name + " is subscribed to nothing");
            }
        }
        return byName;
    }

    /**
     * {@code app.outbox} (E05-S5b spec 4.10, S5b-D5). The bounds are checked here, so a value out of bounds stops the
     * start-up.
     */
    @ConfigurationProperties(prefix = "app.outbox")
    public record Properties(Worker worker, Delivery delivery, Cleanup cleanup, Status status) {

        public Properties {
            require(worker, "app.outbox.worker");
            require(delivery, "app.outbox.delivery");
            require(cleanup, "app.outbox.cleanup");
            require(status, "app.outbox.status");
            // The lease covers one consumer's whole batch, so a row claimed first is marked before its lease ends
            Duration batchTime = delivery.timeout().multipliedBy(delivery.batchSize());
            if (limits(delivery, cleanup, status).lease().compareTo(batchTime) <= 0) {
                throw new IllegalArgumentException("the outbox lease must be longer than batch-size x timeout");
            }
        }

        OutboxWorker.Limits limits() {
            return limits(delivery, cleanup, status);
        }

        private static OutboxWorker.Limits limits(Delivery delivery, Cleanup cleanup, Status status) {
            return new OutboxWorker.Limits(delivery.batchSize(), delivery.timeout(), delivery.leaseMargin(),
                    cleanup.doneRetention(), status.queueAgeAlert());
        }

        /** @param enabled false in the test profile: tests drive the worker's runs */
        public record Worker(boolean enabled) {
        }

        public record Delivery(Duration pollInterval, int batchSize, Duration timeout, Duration leaseMargin) {

            public Delivery {
                within(pollInterval, Duration.ofSeconds(1), Duration.ofSeconds(60), "app.outbox.delivery.poll-interval");
                if (batchSize < 1 || batchSize > 50) {
                    throw new IllegalArgumentException("app.outbox.delivery.batch-size must be 1 to 50");
                }
                within(timeout, Duration.ofSeconds(1), Duration.ofSeconds(60), "app.outbox.delivery.timeout");
                within(leaseMargin, Duration.ofSeconds(5), Duration.ofMinutes(5), "app.outbox.delivery.lease-margin");
            }
        }

        public record Cleanup(Duration interval, Duration doneRetention) {

            public Cleanup {
                positive(interval, "app.outbox.cleanup.interval");
                positive(doneRetention, "app.outbox.cleanup.done-retention");
            }
        }

        public record Status(Duration interval, Duration queueAgeAlert) {

            public Status {
                positive(interval, "app.outbox.status.interval");
                positive(queueAgeAlert, "app.outbox.status.queue-age-alert");
            }
        }

        private static void require(Object value, String key) {
            if (value == null) {
                throw new IllegalArgumentException(key + " is required");
            }
        }

        private static void within(Duration value, Duration min, Duration max, String key) {
            if (value == null || value.compareTo(min) < 0 || value.compareTo(max) > 0) {
                throw new IllegalArgumentException(key + " must be " + min + " to " + max);
            }
        }

        private static void positive(Duration value, String key) {
            if (value == null || value.isNegative() || value.isZero()) {
                throw new IllegalArgumentException(key + " must be positive");
            }
        }
    }
}
