package vn.sunvibe.auth.core.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Scheduled} jobs of the auth module (refresh-token clean-up).
 */
@Configuration
@EnableScheduling
public class AuthSchedulingConfig {
}
