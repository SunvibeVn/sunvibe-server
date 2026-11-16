package vn.sunvibe.brokerage.core.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.sunvibe.brokerage.core.application.shared.AgreementSwitches;

/**
 * The agreement switches as configured (E05-S4 spec S4-D8): sending to owners is off unless the environment turns it on
 * (brokerage-agreement BR-19).
 */
@Component
public class RuntimeAgreementSwitches implements AgreementSwitches {

    private final boolean ownerSendingEnabled;

    public RuntimeAgreementSwitches(
            @Value("${app.brokerage.owner-sending.enabled:false}") boolean ownerSendingEnabled) {
        this.ownerSendingEnabled = ownerSendingEnabled;
    }

    @Override
    public boolean ownerSendingEnabled() {
        return ownerSendingEnabled;
    }
}
