package vn.sunvibe.auth.core.infrastructure.sms;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import vn.sunvibe.auth.core.application.usecase.external.sms.SmsSenderPort;

/**
 * No SMS provider: the phone channel is closed, so nothing may reach this sender (auth-email-access spec 5.7). A call
 * means a phone path passed its channel check, so it fails loudly instead of pretending to send.
 */
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "none")
public class NoSmsSender implements SmsSenderPort {

    @Override
    public void send(String phoneNumber, String message) {
        throw new IllegalStateException("No SMS provider is configured.");
    }
}
