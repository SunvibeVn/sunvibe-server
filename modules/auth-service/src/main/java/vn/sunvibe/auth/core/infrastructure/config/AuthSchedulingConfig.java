package vn.sunvibe.auth.core.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Scheduled} jobs of the auth module (clean-up of refresh
 * tokens, audit events and never-verified accounts; rate-limiter eviction).
 */
@Configuration
@EnableScheduling
public class AuthSchedulingConfig {
}
