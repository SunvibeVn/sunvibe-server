package vn.sunvibe.auth.core.infrastructure.config;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA Configuration for Property Service
 * <p>
 * Configures entity scanning and JPA repository base packages for the property service.
 * This configuration isolates JPA settings to the property-service module, following
 * the principle of module independence.
 */
@Configuration
@EntityScan(basePackages = {
    "vn.sunvibe.auth.core.infrastructure.persistence.jpa.entity"
})
@EnableJpaRepositories(basePackages = {
    "vn.sunvibe.auth.core.infrastructure.persistence.jpa.repository"
})
public class AuthJpaConfig {
}
