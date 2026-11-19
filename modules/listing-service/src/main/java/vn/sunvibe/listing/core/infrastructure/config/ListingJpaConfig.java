package vn.sunvibe.listing.core.infrastructure.config;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA Configuration for Listing Service
 * <p>
 * Configures entity scanning and JPA repository base packages for the listing service, so that its JPA settings stay
 * inside the module.
 */
@Configuration
@EntityScan(basePackages = {
    "vn.sunvibe.listing.core.infrastructure.persistence"
})
@EnableJpaRepositories(basePackages = {
    "vn.sunvibe.listing.core.infrastructure.persistence"
})
public class ListingJpaConfig {
}
