package vn.sunvibe.outbox;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;

/**
 * The transactional outbox of ADR-014. Its {@code @EntityScan} adds {@code vn.sunvibe.outbox} to the modules' own
 * scans; the registrations add up.
 */
@Configuration(proxyBeanMethods = false)
@EntityScan("vn.sunvibe.outbox")
public class OutboxConfig {
}
