package vn.sunvibe.auth.core.infrastructure.config;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA configuration of the auth module: scans only this module's entities and
 * Spring Data repositories, keeping the module independent of the others.
 */
@Configuration
@EntityScan(basePackages = {
    "vn.sunvibe.auth.core.infrastructure.persistence.entity"
})
@EnableJpaRepositories(basePackages = {
    "vn.sunvibe.auth.core.infrastructure.persistence.repository"
})
public class AuthJpaConfig {
}
