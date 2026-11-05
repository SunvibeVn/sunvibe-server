package vn.sunvibe.auth.core.testsupport;

import vn.sunvibe.auth.core.application.usecase.external.email.EmailServicePort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Keeps "sent" e-mails in memory. */
public class RecordingEmailService implements EmailServicePort {

    public record Mail(String kind, String to, String code) {
    }

    public final List<Mail> sent = new ArrayList<>();
    /** The value a reset mail carried. */
    public String lastResetToken;

    @Override
    public void sendPasswordResetEmail(String email, String otpCode, String resetToken) {
        lastResetToken = resetToken;
        sent.add(new Mail("RESET", email, otpCode));
    }

    @Override
    public void sendPasswordResetConfirmationEmail(String email) {
        sent.add(new Mail("RESET_CONFIRMATION", email, null));
    }

    @Override
    public void sendRegistrationCodeEmail(String to, String code) {
        sent.add(new Mail("REGISTRATION", to, code));
    }

    @Override
    public void sendEmailBindCodeEmail(String to, String code) {
        sent.add(new Mail("EMAIL_BIND", to, code));
    }

    @Override
    public void sendEmailChangedNotice(String to) {
        sent.add(new Mail("EMAIL_CHANGED", to, null));
    }

    @Override
    public void sendOwnerNotice(String to, String kind, Instant occurredAt) {
        sent.add(new Mail(kind, to, null));
    }

    public List<Mail> ofKind(String kind) {
        return sent.stream().filter(m -> m.kind().equals(kind)).toList();
    }
}
