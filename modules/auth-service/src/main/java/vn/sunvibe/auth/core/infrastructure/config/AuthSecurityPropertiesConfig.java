package vn.sunvibe.auth.core.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import vn.sunvibe.auth.core.infrastructure.security.ratelimit.RateLimitProperties;
import vn.sunvibe.auth.core.shared.service.RefreshCookieProperties;

/**
 * Binds the auth module's typed security properties.
 */
@Configuration
@EnableConfigurationProperties({RateLimitProperties.class, RefreshCookieProperties.class})
public class AuthSecurityPropertiesConfig {
}
