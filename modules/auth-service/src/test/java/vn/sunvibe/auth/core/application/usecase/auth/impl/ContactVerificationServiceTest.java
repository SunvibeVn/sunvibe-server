package vn.sunvibe.auth.core.application.usecase.auth.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.sunvibe.auth.core.application.exception.auth.VerificationException;
import vn.sunvibe.auth.core.application.exception.auth.VerificationThrottledException;
import vn.sunvibe.auth.core.application.query.auth.VerificationSendResult;
import vn.sunvibe.auth.core.application.shared.VerificationPolicy;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditEventType;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationChannel;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.*;
import vn.sunvibe.auth.core.testsupport.InMemoryUserRepository;
import vn.sunvibe.auth.core.testsupport.InMemoryVerificationCodeRepository;
import vn.sunvibe.auth.core.testsupport.RecordingAuditLog;
import vn.sunvibe.auth.core.testsupport.RecordingEmailService;
import vn.sunvibe.auth.core.testsupport.RecordingSmsSender;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class ContactVerificationServiceTest {

    private static final String PHONE = "0912345678";
    private static final String EMAIL = "a@example.com";

    private AppUser user;
    private InMemoryVerificationCodeRepository codes;
    private final RecordingSmsSender sms = new RecordingSmsSender();
    private final RecordingEmailService email = new RecordingEmailService();
    private final RecordingAuditLog audit = new RecordingAuditLog();
    private ContactVerificationService service;

    @BeforeEach
    void setUp() {
        user = AppUser.create(UserId.create("01ARZ3NDEKTSV4RRFFQ69G5FAV"), "Nguyen Van A", Username.create(PHONE),
                Email.create(EMAIL), Password.create("hashed"), ProfileImage.create(null), null, new HashSet<>(), Instant.now());
        codes = new InMemoryVerificationCodeRepository();
        AtomicLong ids = new AtomicLong();
        service = new ContactVerificationService(new InMemoryUserRepository().with(user), codes, sms, email,
                ids::incrementAndGet, new VerificationPolicy(Duration.ofMinutes(10), 5, Duration.ofSeconds(60), 3), audit);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(PHONE, null, List.of()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void phone_sendThenConfirmActivatesThePendingAccount() {
        assertEquals(UserStatus.PENDING, user.getUserStatus());

        VerificationSendResult sent = service.sendCode(VerificationChannel.PHONE);
        service.confirm(VerificationChannel.PHONE, sms.lastCode());

        assertEquals("091****678", sent.maskedDestination());
        assertEquals(PHONE, sms.sent.getFirst().phoneNumber());
        assertTrue(user.isPhoneVerified());
        assertEquals(UserStatus.ACTIVE, user.getUserStatus());
        assertEquals(List.of(AuditEventType.PHONE_VERIFICATION_SENT, AuditEventType.PHONE_VERIFIED), audit.types());
    }

    @Test
    void email_sendThenConfirmVerifiesTheEmail() {
        VerificationSendResult sent = service.sendCode(VerificationChannel.EMAIL);
        service.confirm(VerificationChannel.EMAIL, email.ofKind("VERIFICATION").getFirst().code());

        assertEquals("a***@example.com", sent.maskedDestination());
        assertTrue(user.isEmailVerified());
        assertFalse(user.isPhoneVerified(), "the phone is a separate matter");
    }

    @Test
    void confirm_wrongCodeIsCountedAndTheFifthBurnsIt() {
        service.sendCode(VerificationChannel.PHONE);
        String rightCode = sms.lastCode();
        String wrongCode = rightCode.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < 4; i++) {
            VerificationException wrong = assertThrows(VerificationException.class,
                    () -> service.confirm(VerificationChannel.PHONE, wrongCode));
            assertEquals(ContactVerificationService.WRONG_CODE, wrong.getMessage());
        }
        VerificationException burned = assertThrows(VerificationException.class,
                () -> service.confirm(VerificationChannel.PHONE, wrongCode));
        assertEquals(ContactVerificationService.TOO_MANY_ATTEMPTS, burned.getMessage());

        assertThrows(VerificationException.class, () -> service.confirm(VerificationChannel.PHONE, rightCode));
        assertFalse(user.isPhoneVerified());
        assertEquals(AuditEventType.VERIFICATION_FAILED, audit.last().type());
    }

    @Test
    void confirm_withoutACodeIsRejected() {
        VerificationException exception = assertThrows(VerificationException.class,
                () -> service.confirm(VerificationChannel.PHONE, "123456"));

        assertEquals(ContactVerificationService.INVALID_CODE, exception.getMessage());
    }

    @Test
    void confirm_codeSentToAnOldEmailDoesNotVerifyANewOne() {
        service.sendCode(VerificationChannel.EMAIL);
        String code = email.ofKind("VERIFICATION").getFirst().code();
        user.changeEmail(Email.create("new@example.com"));

        assertThrows(VerificationException.class, () -> service.confirm(VerificationChannel.EMAIL, code));
        assertFalse(user.isEmailVerified());
    }

    @Test
    void sendCode_newCodeBurnsThePreviousOne() {
        service.sendCode(VerificationChannel.PHONE);
        String firstCode = sms.lastCode();
        codes.all().forEach(c -> codes.age(c.getId(), Duration.ofSeconds(90)));

        service.sendCode(VerificationChannel.PHONE);

        assertEquals(1, codes.all().stream().filter(c -> c.getConsumedAt() == null).count());
        if (!firstCode.equals(sms.lastCode())) {
            assertThrows(VerificationException.class, () -> service.confirm(VerificationChannel.PHONE, firstCode));
        }
    }

    @Test
    void sendCode_tooSoonIsThrottledWithRetryAfter() {
        service.sendCode(VerificationChannel.PHONE);

        VerificationThrottledException throttled = assertThrows(VerificationThrottledException.class,
                () -> service.sendCode(VerificationChannel.PHONE));

        assertEquals(ContactVerificationService.TOO_SOON, throttled.getMessage());
        assertTrue(throttled.getRetryAfter().toSeconds() > 50);
        assertEquals(1, sms.sent.size());
    }

    /** SMS costs money: a daily cap per account stops abuse. */
    @Test
    void sendCode_dailyCap() {
        for (int i = 0; i < 3; i++) {
            service.sendCode(VerificationChannel.PHONE);
            codes.all().forEach(c -> codes.age(c.getId(), Duration.ofSeconds(90)));
        }

        VerificationThrottledException throttled = assertThrows(VerificationThrottledException.class,
                () -> service.sendCode(VerificationChannel.PHONE));

        assertEquals(ContactVerificationService.DAILY_LIMIT, throttled.getMessage());
        assertEquals(3, sms.sent.size());
    }

    @Test
    void sendCode_nothingToVerify() {
        user.verifyPhone(Instant.now());
        assertEquals(ContactVerificationService.ALREADY_VERIFIED,
                assertThrows(VerificationException.class, () -> service.sendCode(VerificationChannel.PHONE)).getMessage());

        user.setEmail(null);
        assertEquals(ContactVerificationService.NO_EMAIL,
                assertThrows(VerificationException.class, () -> service.sendCode(VerificationChannel.EMAIL)).getMessage());
    }

    @Test
    void sendCodeAfterRegistration_neverThrows() {
        ContactVerificationService failingSms = new ContactVerificationService(new InMemoryUserRepository().with(user),
                codes, (phone, message) -> {
                    throw new IllegalStateException("SMS provider down");
                }, email, new AtomicLong()::incrementAndGet,
                new VerificationPolicy(Duration.ofMinutes(10), 5, Duration.ofSeconds(60), 3), audit);

        assertDoesNotThrow(() -> failingSms.sendCodeAfterRegistration(user, VerificationChannel.PHONE));
    }
}
