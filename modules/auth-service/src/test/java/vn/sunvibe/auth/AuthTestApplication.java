package vn.sunvibe.auth;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;
import vn.sunvibe.auth.core.infrastructure.config.AuthJpaConfig;

/**
 * Minimal test-only bootstrap so @DataJpaTest slices can start in this module.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import(AuthJpaConfig.class)
public class AuthTestApplication {
}
