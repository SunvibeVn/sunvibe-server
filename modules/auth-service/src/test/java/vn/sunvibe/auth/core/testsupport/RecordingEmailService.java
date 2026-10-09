package vn.sunvibe.auth.core.testsupport;

import vn.sunvibe.auth.core.application.usecase.external.email.EmailServicePort;

import java.util.ArrayList;
import java.util.List;

/** Keeps "sent" e-mails in memory. */
public class RecordingEmailService implements EmailServicePort {

    public record Mail(String kind, String to, String code) {
    }

    public final List<Mail> sent = new ArrayList<>();

    @Override
    public void sendPasswordResetEmail(String email, String otpCode, String resetToken) {
        sent.add(new Mail("RESET", email, otpCode));
    }

    @Override
    public void sendPasswordResetConfirmationEmail(String email) {
        sent.add(new Mail("RESET_CONFIRMATION", email, null));
    }

    @Override
    public void sendVerificationCodeEmail(String email, String code) {
        sent.add(new Mail("VERIFICATION", email, code));
    }

    public List<Mail> ofKind(String kind) {
        return sent.stream().filter(m -> m.kind().equals(kind)).toList();
    }
}
