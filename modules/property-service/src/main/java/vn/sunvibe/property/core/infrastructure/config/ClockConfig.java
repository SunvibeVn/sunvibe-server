package vn.sunvibe.property.core.infrastructure.config;

import jakarta.validation.ClockProvider;
import org.springframework.context.annotation.Configuration;
import vn.sunvibe.common.time.AppClock;

import jakarta.annotation.PostConstruct;

/**
 * Clock Configuration
 * <p>
 * Initializes the DomainClock implementation for the domain layer.
 * This configuration ensures that ClockProvider is set with the infrastructure
 * implementation (AppClock) during application startup.
 */
@Configuration
public class ClockConfig {

//    private final AppClock appClock;
//
//    public ClockConfig(AppClock appClock) {
//        this.appClock = appClock;
//    }
//
//    /**
//     * Initialize ClockProvider with AppClock implementation.
//     * This runs after the bean is constructed.
//     */
//    @PostConstruct
//    public void initClockProvider() {
//        ClockProvider.setClock(appClock);
//    }
}
