package vn.sunvibe.auth.core.infrastructure.sms;

import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import vn.sunvibe.auth.core.application.shared.ContactMasker;
import vn.sunvibe.auth.core.application.usecase.external.sms.SmsSenderPort;

/**
 * Development stand-in for an SMS provider: writes the message, including any
 * OTP, to the application log instead of sending it.
 * <p>
 * Active only with {@code app.sms.provider=log} (set in the dev and test
 * profiles). Never enable it in production: OTPs would sit in the logs, and
 * {@code AuthChannelConfigGuard} stops the start-up outside dev, test and lan.
 */
@Log4j2
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "log")
public class LoggingSmsSender implements SmsSenderPort {

    @Override
    public void send(String phoneNumber, String message) {
        log.warn("[DEV SMS - not sent] to {}: {}", ContactMasker.maskPhone(phoneNumber), message);
    }
}
