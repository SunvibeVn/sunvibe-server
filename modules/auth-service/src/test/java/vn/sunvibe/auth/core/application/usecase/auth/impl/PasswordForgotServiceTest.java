package vn.sunvibe.auth.core.application.usecase.auth.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.sunvibe.auth.core.application.command.auth.PasswordForgotCommand;
import vn.sunvibe.auth.core.application.shared.PasswordResetPolicy;
import vn.sunvibe.auth.core.application.usecase.external.email.EmailServicePort;
import vn.sunvibe.auth.core.domain.event.ForgotPasswordInitiatedEvent;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.*;
import vn.sunvibe.auth.core.presentation.dto.response.auth.PasswordForgotResponse;
import vn.sunvibe.auth.core.testsupport.InMemoryPasswordResetTokenRepository;
import vn.sunvibe.auth.core.testsupport.InMemoryUserRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class PasswordForgotServiceTest {

    private static final String EMAIL = "a@example.com";

    private AppUser user;
    private InMemoryPasswordResetTokenRepository resetTokens;
    private final List<String> sentOtps = new ArrayList<>();
    private final List<Object> events = new ArrayList<>();
    private PasswordForgotService service;

    @BeforeEach
    void setUp() {
        user = AppUser.create(UserId.create("01ARZ3NDEKTSV4RRFFQ69G5FAV"), "Nguyen Van A", Username.create("0912345678"),
                Email.create(EMAIL), Password.create("hashed"), ProfileImage.create(null), null, new HashSet<>(), Instant.now());
        resetTokens = new InMemoryPasswordResetTokenRepository();
        EmailServicePort email = new EmailServicePort() {
            @Override
            public void sendPasswordResetEmail(String address, String otpCode, String resetToken) {
                assertEquals(EMAIL, address);
                sentOtps.add(otpCode);
            }

            @Override
            public void sendPasswordResetConfirmationEmail(String address) {
                fail("No confirmation email expected");
            }
        };
        AtomicLong ids = new AtomicLong();
        service = new PasswordForgotService(new InMemoryUserRepository().with(user), resetTokens, email, events::add,
                ids::incrementAndGet, new PasswordResetPolicy(5, Duration.ofSeconds(60)));
    }

    private PasswordForgotResponse forgot(String email) {
        return service.execute(new PasswordForgotCommand(email));
    }

    @Test
    void execute_knownEmailSendsACodeAndStoresTheToken() {
        PasswordForgotResponse response = forgot(EMAIL);

        assertEquals(PasswordForgotService.GENERIC_MESSAGE, response.message());
        assertEquals(1, sentOtps.size());
        assertEquals(1, resetTokens.all().size());
        assertTrue(resetTokens.all().getFirst().isValid());
        assertEquals(sentOtps.getFirst(), resetTokens.all().getFirst().getOtpCode());
    }

    /** The answer must not tell an attacker which e-mails are registered. */
    @Test
    void execute_unknownEmailAnswersExactlyTheSameWithoutSendingAnything() {
        PasswordForgotResponse known = forgot(EMAIL);
        PasswordForgotResponse unknown = forgot("nobody@example.com");

        assertEquals(known.message(), unknown.message());
        assertEquals(known.otpExpirationMinutes(), unknown.otpExpirationMinutes());
        assertNotNull(unknown.expiresAt());
        assertEquals(1, sentOtps.size(), "only the known address gets an e-mail");
    }

    @Test
    void execute_blockedAccountGetsNoCode() {
        user.setUserStatus(UserStatus.LOCK);

        PasswordForgotResponse response = forgot(EMAIL);

        assertEquals(PasswordForgotService.GENERIC_MESSAGE, response.message());
        assertTrue(sentOtps.isEmpty());
    }

    @Test
    void execute_emailIsMatchedCaseInsensitively() {
        forgot("  A@Example.COM ");

        assertEquals(1, sentOtps.size());
    }

    /** Repeated clicks or a script must not flood the inbox. */
    @Test
    void execute_doesNotResendWithinTheCooldown() {
        forgot(EMAIL);
        forgot(EMAIL);

        assertEquals(1, sentOtps.size());
        assertEquals(1, resetTokens.all().size());
    }

    @Test
    void execute_newCodeBurnsThePreviousOnes() {
        forgot(EMAIL);
        // Pretend the first code was requested before the cooldown
        var first = resetTokens.all().getFirst();
        resetTokens.save(vn.sunvibe.auth.core.domain.model.user.vo.PasswordResetToken.restore(first.getId(),
                first.getToken(), first.getUserId(), first.getEmail(), first.getCreatedAt().minusSeconds(120),
                first.getExpiresAt(), false, first.getOtpCode()));

        forgot(EMAIL);

        assertEquals(2, sentOtps.size());
        assertFalse(resetTokens.get(first.getId()).isValid(), "the older code no longer works");
        assertEquals(1, resetTokens.all().stream().filter(t -> t.isValid()).count());
    }

    @Test
    void execute_eventCarriesNoSecrets() {
        forgot(EMAIL);

        ForgotPasswordInitiatedEvent event = (ForgotPasswordInitiatedEvent) events.getFirst();
        assertEquals(EMAIL, event.getEmail());
        assertFalse(event.toString().contains(sentOtps.getFirst()));
        assertFalse(event.toString().contains(resetTokens.all().getFirst().getToken()));
    }
}
