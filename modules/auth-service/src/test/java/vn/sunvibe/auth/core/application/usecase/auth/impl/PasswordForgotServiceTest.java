package vn.sunvibe.auth.core.application.usecase.auth.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.sunvibe.auth.core.application.command.auth.PasswordForgotCommand;
import vn.sunvibe.auth.core.application.command.auth.PasswordForgotSmsCommand;
import vn.sunvibe.auth.core.application.shared.PasswordResetPolicy;
import vn.sunvibe.auth.core.domain.event.ForgotPasswordInitiatedEvent;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationChannel;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.*;
import vn.sunvibe.auth.core.presentation.dto.response.auth.PasswordForgotResponse;
import vn.sunvibe.auth.core.testsupport.InMemoryPasswordResetTokenRepository;
import vn.sunvibe.auth.core.testsupport.InMemoryUserRepository;
import vn.sunvibe.auth.core.testsupport.RecordingAuditLog;
import vn.sunvibe.auth.core.testsupport.RecordingEmailService;
import vn.sunvibe.auth.core.testsupport.RecordingSmsSender;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class PasswordForgotServiceTest {

    private static final String EMAIL = "a@example.com";
    private static final String PHONE = "0912345678";

    private AppUser user;
    private InMemoryPasswordResetTokenRepository resetTokens;
    private final RecordingEmailService email = new RecordingEmailService();
    private final RecordingSmsSender sms = new RecordingSmsSender();
    private final List<Object> events = new ArrayList<>();
    private final RecordingAuditLog audit = new RecordingAuditLog();
    private PasswordForgotService service;

    @BeforeEach
    void setUp() {
        user = AppUser.create(UserId.create("01ARZ3NDEKTSV4RRFFQ69G5FAV"), "Nguyen Van A", Username.create(PHONE),
                Email.create(EMAIL), Password.create("hashed"), ProfileImage.create(null), null, new HashSet<>(), Instant.now());
        user.verifyEmail(Instant.now());
        resetTokens = new InMemoryPasswordResetTokenRepository();
        AtomicLong ids = new AtomicLong();
        service = new PasswordForgotService(new InMemoryUserRepository().with(user), resetTokens, email, events::add,
                ids::incrementAndGet, new PasswordResetPolicy(5, Duration.ofSeconds(60), 3), audit, sms);
    }

    private PasswordForgotResponse forgot(String address) {
        return service.execute(new PasswordForgotCommand(address));
    }

    private PasswordForgotResponse forgotBySms(String phone) {
        return service.executeBySms(new PasswordForgotSmsCommand(phone));
    }

    private List<RecordingEmailService.Mail> resetMails() {
        return email.ofKind("RESET");
    }

    // ---------- e-mail ----------

    @Test
    void execute_verifiedEmailGetsACode() {
        PasswordForgotResponse response = forgot(EMAIL);

        assertEquals(PasswordForgotService.GENERIC_MESSAGE, response.message());
        assertNull(response.resetToken(), "the e-mail flow never returns the token");
        assertEquals(1, resetMails().size());
        assertEquals(1, resetTokens.all().size());
        assertEquals(VerificationChannel.EMAIL, resetTokens.all().getFirst().getChannel());
        assertEquals(resetMails().getFirst().code(), resetTokens.all().getFirst().getOtpCode());
    }

    /** Someone may have typed another person's address at sign-up: only a verified e-mail can reset. */
    @Test
    void execute_unverifiedEmailGetsNothing() {
        user.changeEmail(Email.create(EMAIL));

        PasswordForgotResponse response = forgot(EMAIL);

        assertEquals(PasswordForgotService.GENERIC_MESSAGE, response.message());
        assertTrue(resetMails().isEmpty());
        assertEquals("EMAIL NO_ELIGIBLE_ACCOUNT", audit.last().details());
    }

    /** The answer must not tell an attacker which e-mails are registered. */
    @Test
    void execute_unknownEmailAnswersExactlyTheSameWithoutSendingAnything() {
        PasswordForgotResponse known = forgot(EMAIL);
        PasswordForgotResponse unknown = forgot("nobody@example.com");

        assertEquals(known.message(), unknown.message());
        assertEquals(known.otpExpirationMinutes(), unknown.otpExpirationMinutes());
        assertNotNull(unknown.expiresAt());
        assertEquals(1, resetMails().size(), "only the known address gets an e-mail");
        assertEquals("EMAIL SENT", audit.events.get(0).details());
        assertEquals("EMAIL NO_ELIGIBLE_ACCOUNT", audit.events.get(1).details());
        assertNull(audit.events.get(1).userId());
    }

    @Test
    void execute_blockedAccountGetsNoCode() {
        user.setUserStatus(UserStatus.LOCK);

        forgot(EMAIL);

        assertTrue(resetMails().isEmpty());
    }

    @Test
    void execute_emailIsMatchedCaseInsensitively() {
        forgot("  A@Example.COM ");

        assertEquals(1, resetMails().size());
    }

    /** Repeated clicks or a script must not flood the inbox. */
    @Test
    void execute_doesNotResendWithinTheCooldown() {
        forgot(EMAIL);
        forgot(EMAIL);

        assertEquals(1, resetMails().size());
        assertEquals(1, resetTokens.all().size());
        assertEquals("EMAIL COOLDOWN_NOT_SENT", audit.last().details());
    }

    @Test
    void execute_newCodeBurnsThePreviousOnes() {
        forgot(EMAIL);
        Long firstId = resetTokens.all().getFirst().getId();
        resetTokens.age(firstId, Duration.ofSeconds(120));

        forgot(EMAIL);

        assertEquals(2, resetMails().size());
        assertFalse(resetTokens.get(firstId).isValid(), "the older code no longer works");
        assertEquals(1, resetTokens.all().stream().filter(t -> t.isValid()).count());
    }

    /** Caps blind OTP guessing: at most N codes per destination per day. */
    @Test
    void execute_stopsSendingAfterTheDailyLimit() {
        for (int i = 0; i < 3; i++) {
            forgot(EMAIL);
            resetTokens.all().forEach(t -> resetTokens.age(t.getId(), Duration.ofSeconds(120)));
        }

        forgot(EMAIL);

        assertEquals(3, resetMails().size());
        assertEquals("EMAIL DAILY_LIMIT", audit.last().details());
    }

    @Test
    void execute_eventCarriesNoSecrets() {
        forgot(EMAIL);

        ForgotPasswordInitiatedEvent event = (ForgotPasswordInitiatedEvent) events.getFirst();
        assertEquals(EMAIL, event.getEmail());
        assertFalse(event.toString().contains(resetMails().getFirst().code()));
        assertFalse(event.toString().contains(resetTokens.all().getFirst().getToken()));
    }

    // ---------- SMS ----------

    @Test
    void executeBySms_textsACodeAndReturnsTheToken() {
        PasswordForgotResponse response = forgotBySms(PHONE);

        assertEquals(PasswordForgotService.GENERIC_SMS_MESSAGE, response.message());
        assertEquals(1, sms.sent.size());
        assertEquals(PHONE, sms.sent.getFirst().phoneNumber());
        var token = resetTokens.all().getFirst();
        assertEquals(VerificationChannel.PHONE, token.getChannel());
        assertEquals(token.getToken(), response.resetToken());
        assertEquals(token.getOtpCode(), sms.lastCode());
        assertFalse(response.toString().contains(token.getOtpCode()), "the OTP only travels by SMS");
    }

    /** Works for an unverified phone too: the SMS reaches whoever owns the number. */
    @Test
    void executeBySms_worksBeforeThePhoneIsVerified() {
        assertFalse(user.isPhoneVerified());

        forgotBySms(PHONE);

        assertEquals(1, sms.sent.size());
    }

    @Test
    void executeBySms_unknownPhoneLooksTheSameButSendsNothing() {
        PasswordForgotResponse unknown = forgotBySms("0999999999");

        assertEquals(PasswordForgotService.GENERIC_SMS_MESSAGE, unknown.message());
        assertNotNull(unknown.resetToken(), "a decoy token keeps the answer indistinguishable");
        assertTrue(sms.sent.isEmpty());
        assertTrue(resetTokens.all().isEmpty());
    }

    @Test
    void executeBySms_withinCooldownReturnsTheSameTokenWithoutTexting() {
        PasswordForgotResponse first = forgotBySms(PHONE);
        PasswordForgotResponse second = forgotBySms(PHONE);

        assertEquals(first.resetToken(), second.resetToken());
        assertEquals(1, sms.sent.size());
    }
}
