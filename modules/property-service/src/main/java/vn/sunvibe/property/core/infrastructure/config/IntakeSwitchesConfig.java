package vn.sunvibe.property.core.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * The start-up check of property-create intake A-11, D5-17: the intake and the legacy create turn over together (T-3). Both on
 * would let a property skip the gate, so the application refuses to start.
 */
@Configuration
public class IntakeSwitchesConfig {

    private static final String INTAKE = "app.property.intake.enabled";
    private static final String DIRECT_CREATE = "app.property.direct-create.enabled";

    public IntakeSwitchesConfig(Environment environment) {
        boolean intake = environment.getProperty(INTAKE, Boolean.class, false);
        boolean directCreate = environment.getProperty(DIRECT_CREATE, Boolean.class, true);
        if (intake && directCreate) {
            throw new IllegalStateException(INTAKE + " and " + DIRECT_CREATE + " cannot both be on.");
        }
    }
}
