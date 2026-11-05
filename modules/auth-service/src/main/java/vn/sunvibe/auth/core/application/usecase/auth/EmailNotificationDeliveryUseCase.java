package vn.sunvibe.auth.core.application.usecase.auth;

/**
 * Delivers the notices kept in {@code auth_email_notifications} (auth-email-access spec 5.6).
 */
public interface EmailNotificationDeliveryUseCase {

    /** Claims the due notices, commits, and sends them outside any transaction. */
    void deliverDue();
}
