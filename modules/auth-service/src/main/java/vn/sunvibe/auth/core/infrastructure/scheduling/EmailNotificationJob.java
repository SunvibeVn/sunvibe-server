package vn.sunvibe.auth.core.infrastructure.scheduling;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.sunvibe.auth.core.application.usecase.auth.EmailNotificationDeliveryUseCase;
import vn.sunvibe.auth.core.application.shared.external.email.AfterCommitMailPort;

/**
 * Hands the due notices to the mail pool every minute, so a slow SMTP server never holds the default scheduler thread,
 * which the other jobs share (auth-email-access spec 5.6).
 */
@Component
@RequiredArgsConstructor
public class EmailNotificationJob {

    private final EmailNotificationDeliveryUseCase delivery;
    private final AfterCommitMailPort mailPool;

    @Scheduled(fixedDelayString = "${app.auth.mail.notice-delay:60s}", initialDelayString = "${app.auth.mail.notice-delay:60s}")
    public void deliverDueNotices() {
        mailPool.dispatch(delivery::deliverDue);
    }
}
