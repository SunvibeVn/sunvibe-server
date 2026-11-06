package vn.sunvibe.auth.core.application.service.auth;

import vn.sunvibe.auth.core.testsupport.AuthTestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.sunvibe.auth.core.application.exception.auth.VerificationException;
import vn.sunvibe.auth.core.application.exception.auth.VerificationThrottledException;
import vn.sunvibe.auth.core.application.query.auth.VerificationSendResult;
import vn.sunvibe.auth.core.application.shared.VerificationPolicy;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditEventType;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.*;
import vn.sunvibe.auth.core.testsupport.InMemoryUserRepository;
import vn.sunvibe.auth.core.testsupport.InMemoryVerificationCodeRepository;
import vn.sunvibe.auth.core.testsupport.RecordingAuditLog;
import vn.sunvibe.auth.core.testsupport.RecordingSmsSender;
import vn.sunvibe.auth.core.testsupport.SignedIn;
import vn.sunvibe.auth.core.testsupport.TestOtpHasher;

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
    private final RecordingAuditLog audit = new RecordingAuditLog();
    private ContactVerificationService service;

    @BeforeEach
    void setUp() {
        user = AuthTestUsers.withPhone(UserId.create("01ARZ3NDEKTSV4RRFFQ69G5FAV"), "Nguyen Van A", PHONE,
                Email.create(EMAIL), Password.create("hashed"), ProfileImage.create(null), null, new HashSet<>(), Instant.now());
        codes = new InMemoryVerificationCodeRepository();
        AtomicLong ids = new AtomicLong();
        service = new ContactVerificationService(new InMemoryUserRepository().with(user), codes, sms,
                ids::incrementAndGet, new VerificationPolicy(Duration.ofMinutes(10), 5, Duration.ofSeconds(60), 3), audit, AuthTestUsers.phoneOn(), new TestOtpHasher());
        SignedIn.as(user.getId().getValue());
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void phone_sendThenConfirmActivatesThePendingAccount() {
        assertEquals(UserStatus.PENDING, user.getUserStatus());

        VerificationSendResult sent = service.sendCode();
        service.confirm(sms.lastCode());

        assertEquals("091****678", sent.maskedDestination());
        assertEquals("+84912345678", sms.sent.getFirst().phoneNumber());
        assertTrue(user.isPhoneVerified());
        assertEquals(UserStatus.ACTIVE, user.getUserStatus());
        assertEquals(List.of(AuditEventType.PHONE_VERIFICATION_SENT, AuditEventType.PHONE_VERIFIED), audit.types());
    }

    @Test
    void confirm_wrongCodeIsCountedAndTheFifthBurnsIt() {
        service.sendCode();
        String rightCode = sms.lastCode();
        String wrongCode = rightCode.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < 4; i++) {
            VerificationException wrong = assertThrows(VerificationException.class,
                    () -> service.confirm(wrongCode));
            assertEquals(ContactVerificationService.WRONG_CODE, wrong.getMessage());
        }
        VerificationException burned = assertThrows(VerificationException.class,
                () -> service.confirm(wrongCode));
        assertEquals(ContactVerificationService.TOO_MANY_ATTEMPTS, burned.getMessage());

        assertThrows(VerificationException.class, () -> service.confirm(rightCode));
        assertFalse(user.isPhoneVerified());
        assertEquals(AuditEventType.VERIFICATION_FAILED, audit.last().type());
    }

    @Test
    void confirm_withoutACodeIsRejected() {
        VerificationException exception = assertThrows(VerificationException.class,
                () -> service.confirm("123456"));

        assertEquals(ContactVerificationService.INVALID_CODE, exception.getMessage());
    }

    @Test
    void confirm_codeSentToAnOldNumberDoesNotVerifyANewOne() {
        service.sendCode();
        String code = sms.lastCode();
        user.setPhoneKey("+84987654321");

        assertThrows(VerificationException.class, () -> service.confirm(code));
        assertFalse(user.isPhoneVerified());
    }

    @Test
    void sendCode_newCodeBurnsThePreviousOne() {
        service.sendCode();
        String firstCode = sms.lastCode();
        codes.all().forEach(c -> codes.age(c.getId(), Duration.ofSeconds(90)));

        service.sendCode();

        assertEquals(1, codes.all().stream().filter(c -> c.getConsumedAt() == null).count());
        if (!firstCode.equals(sms.lastCode())) {
            assertThrows(VerificationException.class, () -> service.confirm(firstCode));
        }
    }

    @Test
    void sendCode_tooSoonIsThrottledWithRetryAfter() {
        service.sendCode();

        VerificationThrottledException throttled = assertThrows(VerificationThrottledException.class,
                () -> service.sendCode());

        assertEquals(ContactVerificationService.TOO_SOON, throttled.getMessage());
        assertTrue(throttled.getRetryAfter().toSeconds() > 50);
        assertEquals(1, sms.sent.size());
    }

    /** SMS costs money: a daily cap per account stops abuse. */
    @Test
    void sendCode_dailyCap() {
        for (int i = 0; i < 3; i++) {
            service.sendCode();
            codes.all().forEach(c -> codes.age(c.getId(), Duration.ofSeconds(90)));
        }

        VerificationThrottledException throttled = assertThrows(VerificationThrottledException.class,
                () -> service.sendCode());

        assertEquals(ContactVerificationService.DAILY_LIMIT, throttled.getMessage());
        assertEquals(3, sms.sent.size());
    }

    @Test
    void sendCode_nothingToVerify() {
        user.verifyPhone(Instant.now());
        assertEquals(ContactVerificationService.ALREADY_VERIFIED,
                assertThrows(VerificationException.class, () -> service.sendCode()).getMessage());
    }

    @Test
    void sendCodeAfterRegistration_neverThrows() {
        ContactVerificationService failingSms = new ContactVerificationService(new InMemoryUserRepository().with(user),
                codes, (phone, message) -> {
                    throw new IllegalStateException("SMS provider down");
                }, new AtomicLong()::incrementAndGet,
                new VerificationPolicy(Duration.ofMinutes(10), 5, Duration.ofSeconds(60), 3), audit, AuthTestUsers.phoneOn(), new TestOtpHasher());

        assertDoesNotThrow(() -> failingSms.sendCodeAfterRegistration(user));
    }

    @Test
    void confirm_wrongCode_carriesReasonCodeIncorrect() {
        service.sendCode();
        String wrongCode = sms.lastCode().equals("000000") ? "111111" : "000000";

        VerificationException wrong = assertThrows(VerificationException.class,
                () -> service.confirm(wrongCode));

        assertEquals(VerificationException.Reason.CODE_INCORRECT, wrong.getReason());
        assertEquals(ContactVerificationService.WRONG_CODE, wrong.getMessage());
    }

    @Test
    void confirm_expiredCode_carriesReasonCodeExpired() {
        service.sendCode();
        String code = sms.lastCode();
        codes.all().forEach(c -> codes.age(c.getId(), Duration.ofMinutes(11)));

        VerificationException expired = assertThrows(VerificationException.class,
                () -> service.confirm(code));

        assertEquals(VerificationException.Reason.CODE_EXPIRED, expired.getReason());
        assertEquals(ContactVerificationService.INVALID_CODE, expired.getMessage());
    }

    @Test
    void confirm_fifthWrongCode_carriesReasonAttemptsExceeded() {
        service.sendCode();
        String wrongCode = sms.lastCode().equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 4; i++) {
            assertThrows(VerificationException.class, () -> service.confirm(wrongCode));
        }

        VerificationException burned = assertThrows(VerificationException.class,
                () -> service.confirm(wrongCode));

        assertEquals(VerificationException.Reason.CODE_ATTEMPTS_EXCEEDED, burned.getReason());
        assertEquals(ContactVerificationService.TOO_MANY_ATTEMPTS, burned.getMessage());
    }

    @Test
    void confirm_alreadyVerified_carriesReasonAlreadyVerified() {
        user.verifyPhone(Instant.now());

        VerificationException exception = assertThrows(VerificationException.class,
                () -> service.confirm("123456"));

        assertEquals(VerificationException.Reason.ALREADY_VERIFIED, exception.getReason());
        assertEquals(ContactVerificationService.ALREADY_VERIFIED, exception.getMessage());
    }

    @Test
    void sendCode_alreadyVerified_carriesReasonAlreadyVerified() {
        user.verifyPhone(Instant.now());

        VerificationException exception = assertThrows(VerificationException.class,
                () -> service.sendCode());

        assertEquals(VerificationException.Reason.ALREADY_VERIFIED, exception.getReason());
        assertEquals(ContactVerificationService.ALREADY_VERIFIED, exception.getMessage());
    }

    @Test
    void sendCode_withinCooldown_carriesReasonResendTooSoon() {
        service.sendCode();

        VerificationThrottledException throttled = assertThrows(VerificationThrottledException.class,
                () -> service.sendCode());

        assertEquals(VerificationThrottledException.Reason.RESEND_TOO_SOON, throttled.getReason());
        assertEquals(ContactVerificationService.TOO_SOON, throttled.getMessage());
    }

    /** W10-3: the fifth code in 24 h was already sent, so the sixth request is refused. */
    @Test
    void sendCode_sixthToday_carriesReasonDailyLimit() {
        ContactVerificationService fivePerDay = new ContactVerificationService(new InMemoryUserRepository().with(user),
                codes, sms, new AtomicLong()::incrementAndGet,
                new VerificationPolicy(Duration.ofMinutes(10), 5, Duration.ofSeconds(60), 5), audit, AuthTestUsers.phoneOn(), new TestOtpHasher());
        for (int i = 0; i < 5; i++) {
            fivePerDay.sendCode();
            codes.all().forEach(c -> codes.age(c.getId(), Duration.ofSeconds(90)));
        }

        VerificationThrottledException throttled = assertThrows(VerificationThrottledException.class,
                () -> fivePerDay.sendCode());

        assertEquals(VerificationThrottledException.Reason.DAILY_LIMIT, throttled.getReason());
        assertEquals(ContactVerificationService.DAILY_LIMIT, throttled.getMessage());
    }
}
