package vn.sunvibe.auth.core.infrastructure.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import vn.sunvibe.auth.core.application.exception.auth.SendingMailFailedException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** auth-email-access spec 5.6, 4.10: the sender is configured, the link points home, and no line names an address. */
@ExtendWith(OutputCaptureExtension.class)
class EmailServiceTest {

    private static final String ADDRESS = "someone.private@example.com";
    private static final String FROM = "noreply@sunvibe.test";
    private static final String BASE_URL = "https://www.sunvibe.vn";

    /** Keeps the messages instead of opening a connection. */
    private static final class Outbox extends JavaMailSenderImpl {
        final List<MimeMessage> sent = new ArrayList<>();
        boolean failing;

        @Override
        protected void doSend(MimeMessage[] mimeMessages, Object[] originalMessages) {
            if (failing) {
                throw new MailSendException("Could not reach the server for " + ADDRESS);
            }
            sent.addAll(List.of(mimeMessages));
        }
    }

    private final Outbox outbox = new Outbox();
    private final EmailService service = new EmailService(outbox, FROM, BASE_URL);

    private static String raw(MimeMessage message) throws IOException, MessagingException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        message.writeTo(out);
        return out.toString(java.nio.charset.StandardCharsets.UTF_8).replace("=\r\n", "").replace("=3D", "=");
    }

    @Test
    void template_linkHasConfiguredOriginAndFragment() throws Exception {
        service.sendPasswordResetEmail(ADDRESS, "123456", "reset-token");

        String body = raw(outbox.sent.getFirst());
        assertTrue(body.contains(BASE_URL + "/reset-password#token=reset-token"), "the token travels in the fragment");
        assertFalse(body.contains("?token="), "never in a query string");
    }

    @Test
    void service_baseUrlWithTrailingSlash_makesOneSlash() throws Exception {
        new EmailService(outbox, FROM, BASE_URL + "/").sendPasswordResetEmail(ADDRESS, "123456", "reset-token");

        assertTrue(raw(outbox.sent.getFirst()).contains(BASE_URL + "/reset-password#token=reset-token"));
    }

    @Test
    void service_usesConfiguredFrom() throws Exception {
        service.sendRegistrationCodeEmail(ADDRESS, "123456");
        service.sendEmailBindCodeEmail(ADDRESS, "123456");
        service.sendEmailChangedNotice(ADDRESS);
        service.sendPasswordResetEmail(ADDRESS, "123456", "reset-token");

        for (MimeMessage message : outbox.sent) {
            assertEquals(FROM, message.getFrom()[0].toString());
        }
    }

    /** A subject shows on a lock screen, so a code never goes there. */
    @Test
    void service_codeNeverInTheSubject() throws Exception {
        service.sendRegistrationCodeEmail(ADDRESS, "123456");
        service.sendEmailBindCodeEmail(ADDRESS, "654321");

        assertFalse(outbox.sent.get(0).getSubject().contains("123456"));
        assertTrue(raw(outbox.sent.get(0)).contains("123456"));
        assertFalse(outbox.sent.get(1).getSubject().contains("654321"));
        assertTrue(raw(outbox.sent.get(1)).contains("654321"));
    }

    @Test
    void service_noticeNamesNoAddress() throws Exception {
        service.sendEmailChangedNotice(ADDRESS);

        assertFalse(outbox.sent.getFirst().getContent().toString().contains("@"));
    }

    @Test
    void service_logsNoRecipient(CapturedOutput output) {
        service.sendRegistrationCodeEmail(ADDRESS, "123456");
        service.sendEmailBindCodeEmail(ADDRESS, "123456");
        service.sendEmailChangedNotice(ADDRESS);
        service.sendPasswordResetEmail(ADDRESS, "123456", "reset-token");
        service.sendPasswordResetConfirmationEmail(ADDRESS);

        assertFalse(output.getOut().contains(ADDRESS));
    }

    /** A mail exception can quote the address: only its class is logged. */
    @Test
    void service_failureLogsClassOnly_noAddress(CapturedOutput output) {
        outbox.failing = true;

        assertThrows(SendingMailFailedException.class, () -> service.sendRegistrationCodeEmail(ADDRESS, "123456"));
        assertThrows(SendingMailFailedException.class, () -> service.sendEmailBindCodeEmail(ADDRESS, "123456"));
        assertThrows(SendingMailFailedException.class, () -> service.sendEmailChangedNotice(ADDRESS));
        assertThrows(SendingMailFailedException.class, () -> service.sendPasswordResetEmail(ADDRESS, "123456", "t"));
        assertThrows(SendingMailFailedException.class, () -> service.sendPasswordResetConfirmationEmail(ADDRESS));

        assertFalse(output.getOut().contains(ADDRESS));
        assertTrue(output.getOut().contains("MailSendException"));
    }

    @Test
    void service_failure_exceptionQuotesNoAddress() {
        outbox.failing = true;

        SendingMailFailedException failed = assertThrows(SendingMailFailedException.class,
                () -> service.sendRegistrationCodeEmail(ADDRESS, "123456"));

        assertFalse(failed.getMessage().contains(ADDRESS));
    }
}
