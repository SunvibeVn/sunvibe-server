package vn.sunvibe.survey.core.infrastructure.config;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA Configuration for Survey Service
 * <p>
 * Configures entity scanning and JPA repository base packages for the survey service, so that its JPA settings stay
 * inside the module.
 */
@Configuration
@EntityScan(basePackages = {
    "vn.sunvibe.survey.core.infrastructure.persistence"
})
@EnableJpaRepositories(basePackages = {
    "vn.sunvibe.survey.core.infrastructure.persistence"
})
public class SurveyJpaConfig {
}
