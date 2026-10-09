package vn.sunvibe.auth.core.application.usecase.external.sms;

/**
 * Sends text messages. The provider (eSMS, SpeedSMS, Twilio, ...) is chosen
 * with {@code app.sms.provider}; until a real provider is plugged in only the
 * {@code log} implementation exists, for development.
 */
public interface SmsSenderPort {

    /**
     * Sends {@code message} to {@code phoneNumber} (Vietnamese national format, e.g. 0912345678).
     *
     * @throws RuntimeException if the provider rejects or cannot deliver the message
     */
    void send(String phoneNumber, String message);

}
