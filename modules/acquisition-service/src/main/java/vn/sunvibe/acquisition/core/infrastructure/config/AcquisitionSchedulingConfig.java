package vn.sunvibe.acquisition.core.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables the {@code @Scheduled} jobs of the acquisition module (daily clean-up of the owner intake idempotency
 * keys) here, so they do not rely on another module enabling scheduling; enabling it twice is harmless.
 */
@Configuration
@EnableScheduling
public class AcquisitionSchedulingConfig {
}
