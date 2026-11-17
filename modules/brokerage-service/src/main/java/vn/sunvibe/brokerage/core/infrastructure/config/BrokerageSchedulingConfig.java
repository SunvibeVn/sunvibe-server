package vn.sunvibe.brokerage.core.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables the {@code @Scheduled} jobs of the brokerage module (the lapse job of E05-S5 spec S5-D8) here, so they do not
 * rely on another module enabling scheduling; enabling it twice is harmless. No scheduler bean: Boot's default
 * scheduler stays (performance-concurrency rules).
 */
@Configuration
@EnableScheduling
public class BrokerageSchedulingConfig {
}
