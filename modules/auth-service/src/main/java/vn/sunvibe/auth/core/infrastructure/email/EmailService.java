package vn.sunvibe.auth.core.infrastructure.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import vn.sunvibe.auth.core.application.exception.auth.SendingMailFailedException;
import vn.sunvibe.auth.core.application.usecase.external.email.EmailServicePort;

import java.time.Instant;

/**
 * {@link EmailServicePort} over SMTP ({@code spring.mail.*}); the sender is {@code app.mail.from}. No line names a
 * recipient, and a failure logs the exception class only: a mail exception can quote the address (SV-009).
 */
@Service
@Log4j2
public class EmailService implements EmailServicePort {

    private static final String FAILED = "The mail could not be handed over.";

    private final JavaMailSender mailSender;
    private final String fromEmail;
    private final String frontendBaseUrl;

    public EmailService(JavaMailSender mailSender, @Value("${app.mail.from}") String fromEmail,
                        @Value("${app.auth.frontend-base-url}") String frontendBaseUrl) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
        this.frontendBaseUrl = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1) : frontendBaseUrl;
    }

    /** The token goes in the fragment, which never reaches a server or a Referer (auth-email-access A-17). */
    @Override
    public void sendPasswordResetEmail(String email, String otpCode, String resetToken) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);
            helper.setFrom(fromEmail);
            helper.setTo(email);
            helper.setSubject("Password Reset Request");
            helper.setText(EmailTemplate.getPasswordResetTemplate(otpCode,
                    frontendBaseUrl + "/reset-password#token=" + resetToken), true);

            mailSender.send(mimeMessage);

            log.info("auth.mail.sent kind=password-reset");
        } catch (MailException | MessagingException e) {
            throw failed("password-reset", e);
        }
    }

    @Override
    public void sendPasswordResetConfirmationEmail(String email) {
        send("password-reset-confirmation", email, "Password Changed Successfully",
                "Your password has been reset successfully. If you didn't request this, please contact support.");
    }

    @Override
    public void sendRegistrationCodeEmail(String to, String code) {
        send("registration", to, "Your Sunvibe sign-up code", "Your Sunvibe sign-up code is " + code
                + ". It expires in 10 minutes. If you did not ask to sign up, ignore this message.");
    }

    @Override
    public void sendEmailBindCodeEmail(String to, String code) {
        send("email-bind", to, "Confirm your email for Sunvibe", "Your code to use this email on your Sunvibe account is "
                + code + ". It expires in 10 minutes. If you did not ask for this, ignore this message.");
    }

    @Override
    public void sendEmailChangedNotice(String to) {
        send("email-changed", to, "Your Sunvibe email was changed", "The email of your Sunvibe account was changed, and "
                + "this address no longer signs in. If you did not do this, contact Sunvibe.");
    }

    @Override
    public void sendOwnerNotice(String to, String kind, Instant occurredAt) {
        OwnerNoticeText notice = OwnerNoticeText.of(kind);
        send("owner-notice", to, notice.subject(), notice.body(occurredAt));
    }

    private void send(String kind, String to, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);

            mailSender.send(message);

            log.info("auth.mail.sent kind={}", kind);
        } catch (MailException e) {
            throw failed(kind, e);
        }
    }

    private static SendingMailFailedException failed(String kind, Exception cause) {
        log.error("auth.mail.failed kind={} class={}", kind, cause.getClass().getSimpleName());
        return new SendingMailFailedException(FAILED);
    }
}
