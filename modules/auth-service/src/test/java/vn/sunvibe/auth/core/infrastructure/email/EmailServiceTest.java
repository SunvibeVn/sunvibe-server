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

    /** property-create intake A-12: fixed text with the kind, the time and what to do; no value from any request. */
    @Test
    void notify_textHoldsNoCodeLeadOrAddress() throws Exception {
        java.time.Instant at = java.time.Instant.parse("2026-11-06T02:30:00Z");
        for (String kind : List.of("OWNER_CONFIRMED", "OWNER_WITHDRAWN", "OWNER_CHECK_REFUSED")) {
            outbox.sent.clear();

            service.sendOwnerNotice(ADDRESS, kind, at);

            String body = (String) outbox.sent.getFirst().getContent();
            assertTrue(body.contains("2026-11-06 09:30"), "the time, in Vietnam: " + body);
            assertTrue(body.contains("If this was not you"), body);
            assertFalse(body.contains(ADDRESS), body);
            assertFalse(body.toLowerCase(java.util.Locale.ROOT).contains("code"), body);
            assertFalse(body.toLowerCase(java.util.Locale.ROOT).contains("lead"), body);
            assertFalse(body.toLowerCase(java.util.Locale.ROOT).contains("address"), body);
        }
    }

    /**
     * brokerage-agreement E05-S4 pin (U-16): the three property notices keep their exact subjects and bodies when a
     * kind with its own closing line is added.
     */
    @Test
    void notify_existingKinds_exactTexts() throws Exception {
        java.time.Instant at = java.time.Instant.parse("2026-11-06T02:30:00Z");
        String closing = " Time: 2026-11-06 09:30 (Vietnam time). If this was not you, contact Sunvibe and change your"
                + " password.";
        List<List<String>> expected = List.of(
                List.of("OWNER_CONFIRMED", "You confirmed a property link",
                        "Your Sunvibe account confirmed that you own a property." + closing),
                List.of("OWNER_WITHDRAWN", "You withdrew a property link",
                        "Your Sunvibe account withdrew a property confirmation." + closing),
                List.of("OWNER_CHECK_REFUSED", "A property link could not be confirmed",
                        "Sunvibe could not confirm a property link for your account." + closing));
        for (List<String> kind : expected) {
            outbox.sent.clear();

            service.sendOwnerNotice(ADDRESS, kind.get(0), at);

            MimeMessage message = outbox.sent.getFirst();
            assertEquals(kind.get(1), message.getSubject(), kind.get(0));
            assertEquals(kind.get(2), message.getContent(), kind.get(0));
        }
    }

    /**
     * brokerage-agreement BR-17 (E05-S4 spec S4-D7, SD-8): a version waiting for the owner gets a neutral notice that only
     * invites a sign-in: no term, code, address, amount, agreement id or link, and its own closing line.
     */
    @Test
    void notify_agreementPending_neutralTextAndOwnClosing() throws Exception {
        service.sendOwnerNotice(ADDRESS, "AGREEMENT_PENDING", java.time.Instant.parse("2026-11-06T02:30:00Z"));

        MimeMessage message = outbox.sent.getFirst();
        String body = (String) message.getContent();
        assertEquals("A brokerage agreement is waiting for you", message.getSubject());
        assertEquals("Sunvibe sent you a brokerage agreement to read and answer. Sign in to your Sunvibe account to read"
                + " it. Time: 2026-11-06 09:30 (Vietnam time). If you did not expect this, contact Sunvibe.", body);
        assertTrue(body.contains("Sign in"), body);
        assertEquals(List.of("2026", "11", "06", "09", "30"),
                java.util.regex.Pattern.compile("\\d+").matcher(body).results().map(java.util.regex.MatchResult::group)
                        .toList(), body);
        String folded = (message.getSubject() + " " + body).toLowerCase(java.util.Locale.ROOT);
        for (String word : List.of("code", "lead", "address", "unit", "vnd", "%", "http")) {
            assertFalse(folded.contains(word), word + " in " + body);
        }
        assertFalse(body.contains(ADDRESS), body);
    }

    /**
     * brokerage-agreement E05-S6 spec S6-D13, NT-1 (BR-17): the activation notice is neutral and only invites a sign-in:
     * no term, code, address, amount, contract number or link, and its own closing line.
     */
    @Test
    void notify_agreementActivated_neutralTextAndOwnClosing() throws Exception {
        service.sendOwnerNotice(ADDRESS, "AGREEMENT_ACTIVATED", java.time.Instant.parse("2026-11-06T02:30:00Z"));

        MimeMessage message = outbox.sent.getFirst();
        String body = (String) message.getContent();
        assertEquals("A brokerage agreement is in effect", message.getSubject());
        assertEquals("A brokerage agreement you confirmed was signed and activated by Sunvibe. Sign in to your Sunvibe"
                + " account to read it. Time: 2026-11-06 09:30 (Vietnam time). If you did not expect this, contact"
                + " Sunvibe.", body);
        assertEquals(List.of("2026", "11", "06", "09", "30"),
                java.util.regex.Pattern.compile("\\d+").matcher(body).results().map(java.util.regex.MatchResult::group)
                        .toList(), body);
        String folded = (message.getSubject() + " " + body).toLowerCase(java.util.Locale.ROOT);
        for (String word : List.of("code", "lead", "address", "unit", "vnd", "%", "http", "number", "document")) {
            assertFalse(folded.contains(word), word + " in " + body);
        }
        assertFalse(body.contains(ADDRESS), body);
    }

    /** D5-7: a refusal is neutral; the account never reads it as a withdrawal of its own. */
    @Test
    void notify_refusalIsNeverAWithdrawal() throws Exception {
        service.sendOwnerNotice(ADDRESS, "OWNER_CHECK_REFUSED", java.time.Instant.parse("2026-11-06T02:30:00Z"));

        String body = (String) outbox.sent.getFirst().getContent();
        assertFalse(body.toLowerCase(java.util.Locale.ROOT).contains("withdrew"), body);
    }

    @Test
    void service_failure_exceptionQuotesNoAddress() {
        outbox.failing = true;

        SendingMailFailedException failed = assertThrows(SendingMailFailedException.class,
                () -> service.sendRegistrationCodeEmail(ADDRESS, "123456"));

        assertFalse(failed.getMessage().contains(ADDRESS));
    }
}
