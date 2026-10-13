package vn.sunvibe.auth.core.application.usecase.auth.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.sunvibe.auth.core.application.exception.auth.VerificationException;
import vn.sunvibe.auth.core.application.exception.auth.VerificationThrottledException;
import vn.sunvibe.auth.core.application.query.auth.RegistrationTokenResult;
import vn.sunvibe.auth.core.application.query.auth.VerificationSendResult;
import vn.sunvibe.auth.core.application.shared.VerificationPolicy;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditEventType;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;
import vn.sunvibe.auth.core.domain.model.auth.RegistrationPhoneChallenge;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationChannel;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.vo.*;
import vn.sunvibe.auth.core.shared.enums.AuthFieldName;
import vn.sunvibe.auth.core.testsupport.InMemoryRegistrationPhoneChallengeRepository;
import vn.sunvibe.auth.core.testsupport.InMemoryUserRepository;
import vn.sunvibe.auth.core.testsupport.RecordingAuditLog;
import vn.sunvibe.auth.core.testsupport.RecordingSmsSender;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class RegistrationPhoneVerificationServiceTest {

    private static final String PHONE = "0912345678";

    private InMemoryUserRepository users;
    private InMemoryRegistrationPhoneChallengeRepository challenges;
    private final RecordingSmsSender sms = new RecordingSmsSender();
    private final RecordingAuditLog audit = new RecordingAuditLog();
    private RegistrationPhoneVerificationService service;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
        challenges = new InMemoryRegistrationPhoneChallengeRepository();
        AtomicLong ids = new AtomicLong();
        service = new RegistrationPhoneVerificationService(users, challenges, sms, ids::incrementAndGet,
                new VerificationPolicy(Duration.ofMinutes(10), 5, Duration.ofSeconds(60), 3), audit);
    }

    private String sendAndReadCode() {
        service.sendCode(PHONE);
        return sms.lastCode();
    }

    /** Lets the resend cooldown pass for every challenge sent so far. */
    private void passCooldown() {
        challenges.all().forEach(c -> challenges.age(c.getId(), Duration.ofSeconds(61)));
    }

    private static String wrongCodeFor(String rightCode) {
        return rightCode.equals("000000") ? "111111" : "000000";
    }

    @Test
    void sendCode_textsAFreshCodeAndReportsWhereAndWhen() {
        VerificationSendResult result = service.sendCode(PHONE);

        assertEquals(VerificationChannel.PHONE, result.channel());
        assertEquals("091****678", result.maskedDestination());
        assertTrue(result.resendAvailableAt().isAfter(Instant.now()));
        assertEquals(PHONE, sms.sent.getFirst().phoneNumber());
        assertEquals(1, challenges.all().size());
        assertEquals(List.of(AuditEventType.REGISTRATION_PHONE_CODE_SENT), audit.types());
    }

    @Test
    void sendCode_neverStoresTheCodeInThePlain() {
        String code = sendAndReadCode();

        assertNotEquals(code, challenges.all().getFirst().getCodeHash());
        assertTrue(challenges.all().getFirst().matches(code));
    }

    @Test
    void sendCode_rejectsAPhoneNumberThatAlreadyHasAnAccount() {
        users.with(AppUser.create(UserId.create("01ARZ3NDEKTSV4RRFFQ69G5FAV"), "Nguyen Van A", Username.create(PHONE),
                null, Password.create("hashed"), ProfileImage.create(null), null, new HashSet<>(), Instant.now()));

        AuthValidationException exception = assertThrows(AuthValidationException.class, () -> service.sendCode(PHONE));

        assertEquals(AuthFieldName.USER_NAME, exception.getField());
        assertEquals(RegistrationPhoneVerificationService.PHONE_IN_USE, exception.getMessage());
        assertTrue(sms.sent.isEmpty(), "a taken number must not receive a code");
    }

    @Test
    void sendCode_secondRequestInsideTheCooldownIsThrottled() {
        service.sendCode(PHONE);

        VerificationThrottledException exception = assertThrows(VerificationThrottledException.class,
                () -> service.sendCode(PHONE));

        assertEquals(RegistrationPhoneVerificationService.TOO_SOON, exception.getMessage());
        assertTrue(exception.getRetryAfter().toSeconds() > 0);
        assertEquals(1, sms.sent.size());
    }

    @Test
    void sendCode_aNewCodeBurnsTheOldOne() {
        String first = sendAndReadCode();
        passCooldown();
        String second = sendAndReadCode();

        // The old code only fails when it differs from the new one (6-digit collisions are possible)
        if (!first.equals(second)) {
            assertThrows(VerificationException.class, () -> service.verifyCode(PHONE, first));
        }
        assertNotNull(service.verifyCode(PHONE, second).registrationToken());
    }

    @Test
    void sendCode_dailyLimitStopsFurtherCodes() {
        for (int i = 0; i < 3; i++) {
            service.sendCode(PHONE);
            passCooldown();
        }

        VerificationThrottledException exception = assertThrows(VerificationThrottledException.class,
                () -> service.sendCode(PHONE));

        assertEquals(RegistrationPhoneVerificationService.DAILY_LIMIT, exception.getMessage());
    }

    @Test
    void verifyCode_rightCodeIssuesAHashedSingleUseToken() {
        String code = sendAndReadCode();

        RegistrationTokenResult result = service.verifyCode(PHONE, code);

        assertNotNull(result.registrationToken());
        assertTrue(result.registrationToken().length() >= 40, "32 random bytes, base64url");
        assertTrue(result.expiresAt().isAfter(Instant.now()));
        RegistrationPhoneChallenge stored = challenges.all().getFirst();
        assertEquals(RegistrationPhoneChallenge.hashToken(result.registrationToken()), stored.getTokenHash());
        assertNotEquals(result.registrationToken(), stored.getTokenHash());
        assertEquals(List.of(AuditEventType.REGISTRATION_PHONE_CODE_SENT, AuditEventType.REGISTRATION_PHONE_VERIFIED),
                audit.types());
        assertThrows(VerificationException.class, () -> service.verifyCode(PHONE, code), "a code works once");
    }

    @Test
    void verifyCode_wrongCodeIsCountedAndTheFifthBurnsIt() {
        String rightCode = sendAndReadCode();
        String wrongCode = wrongCodeFor(rightCode);

        for (int i = 0; i < 4; i++) {
            VerificationException wrong = assertThrows(VerificationException.class,
                    () -> service.verifyCode(PHONE, wrongCode));
            assertEquals(RegistrationPhoneVerificationService.WRONG_CODE, wrong.getMessage());
        }
        VerificationException burned = assertThrows(VerificationException.class,
                () -> service.verifyCode(PHONE, wrongCode));
        assertEquals(RegistrationPhoneVerificationService.TOO_MANY_ATTEMPTS, burned.getMessage());

        assertThrows(VerificationException.class, () -> service.verifyCode(PHONE, rightCode));
        assertEquals(AuditEventType.REGISTRATION_VERIFICATION_FAILED, audit.last().type());
    }

    @Test
    void verifyCode_withoutAChallengeIsRejected() {
        VerificationException exception = assertThrows(VerificationException.class,
                () -> service.verifyCode(PHONE, "123456"));

        assertEquals(RegistrationPhoneVerificationService.INVALID_CODE, exception.getMessage());
    }

    @Test
    void verifyCode_anExpiredCodeIsRejected() {
        String code = sendAndReadCode();
        challenges.age(challenges.all().getFirst().getId(), Duration.ofMinutes(11));

        VerificationException exception = assertThrows(VerificationException.class,
                () -> service.verifyCode(PHONE, code));

        assertEquals(RegistrationPhoneVerificationService.INVALID_CODE, exception.getMessage());
    }

    @Test
    void verifyCode_aCodeSentToAnotherPhoneDoesNotWork() {
        String code = sendAndReadCode();

        assertThrows(VerificationException.class, () -> service.verifyCode("0987654321", code));
    }

    @Test
    void consumeRegistrationToken_worksOnceForTheVerifiedPhone() {
        String token = service.verifyCode(PHONE, sendAndReadCode()).registrationToken();

        service.consumeRegistrationToken(token, PHONE);

        VerificationException reused = assertThrows(VerificationException.class,
                () -> service.consumeRegistrationToken(token, PHONE));
        assertEquals(RegistrationPhoneVerificationService.INVALID_TOKEN, reused.getMessage());
        assertNotNull(challenges.all().getFirst().getRegisteredAt());
    }

    @Test
    void consumeRegistrationToken_isBoundToThePhoneItWasIssuedTo() {
        String token = service.verifyCode(PHONE, sendAndReadCode()).registrationToken();

        assertThrows(VerificationException.class, () -> service.consumeRegistrationToken(token, "0987654321"));

        service.consumeRegistrationToken(token, PHONE);
    }

    @Test
    void consumeRegistrationToken_rejectsMissingUnknownAndExpiredTokens() {
        String token = service.verifyCode(PHONE, sendAndReadCode()).registrationToken();

        assertThrows(VerificationException.class, () -> service.consumeRegistrationToken(null, PHONE));
        assertThrows(VerificationException.class, () -> service.consumeRegistrationToken("  ", PHONE));
        assertThrows(VerificationException.class, () -> service.consumeRegistrationToken("not-a-token", PHONE));

        challenges.age(challenges.all().getFirst().getId(), Duration.ofMinutes(16));
        assertThrows(VerificationException.class, () -> service.consumeRegistrationToken(token, PHONE));
    }

    @Test
    void consumeRegistrationToken_isRevokedWhenANewCodeIsRequested() {
        String token = service.verifyCode(PHONE, sendAndReadCode()).registrationToken();
        passCooldown();

        service.sendCode(PHONE);

        assertThrows(VerificationException.class, () -> service.consumeRegistrationToken(token, PHONE));
    }

    @Test
    void consumeRegistrationToken_failureIsAudited() {
        assertThrows(VerificationException.class, () -> service.consumeRegistrationToken("nope", PHONE));

        assertEquals(AuditEventType.REGISTRATION_VERIFICATION_FAILED, audit.last().type());
        assertEquals(PHONE, audit.last().subject());
    }
}
