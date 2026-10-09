package vn.sunvibe.auth.core.application.usecase.auth.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.sunvibe.auth.core.application.exception.auth.AuthenticationFailedException;
import vn.sunvibe.auth.core.application.usecase.external.email.EmailServicePort;
import vn.sunvibe.auth.core.domain.model.auth.RefreshToken;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.*;
import vn.sunvibe.auth.core.domain.repository.auth.PasswordResetTokenDomainRepository;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.testsupport.InMemoryRefreshTokenRepository;
import vn.sunvibe.auth.core.presentation.dto.request.auth.PasswordResetRequest;
import vn.sunvibe.common.time.AppClock;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plain-JUnit test with hand-written fakes (Mockito isn't on this module's
 * classpath): a locked/disabled user must not reset their password through
 * the public OTP reset endpoint, and a successful reset ends every session.
 */
class PasswordResetServiceTest {

    private static final String RESET_TOKEN = "reset-token";
    private static final String OTP = "123456";
    private static final String EMAIL = "a@example.com";
    private static final String ORIGINAL_PASSWORD_HASH = "original-hash";

    private static class FakeUserDomainRepository implements UserDomainRepository {
        private final AppUser user;
        private int saveCount;

        private FakeUserDomainRepository(AppUser user) {
            this.user = user;
        }

        @Override
        public boolean existsByUsername(String userName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean existsByEmail(String email) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean existsByIdCardNumber(IdCardNumber idCardNumber) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AppUser save(AppUser appUser) {
            saveCount++;
            return appUser;
        }

        @Override
        public AppUser findByUsername(String username) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AppUser findByEmail(String email) {
            return user;
        }

        @Override
        public Optional<AppUser> findById(UserId userId) {
            throw new UnsupportedOperationException();
        }
    }

    private static class FakePasswordResetTokenDomainRepository implements PasswordResetTokenDomainRepository {
        private final PasswordResetToken token;
        private boolean markedAsUsed;

        private FakePasswordResetTokenDomainRepository(PasswordResetToken token) {
            this.token = token;
        }

        @Override
        public void save(PasswordResetToken passwordResetToken) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<PasswordResetToken> findByToken(String value) {
            return RESET_TOKEN.equals(value) ? Optional.of(token) : Optional.empty();
        }

        @Override
        public Optional<PasswordResetToken> findByEmailAndNotUsed(String email) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void markAsUsed(String value) {
            markedAsUsed = true;
        }

        @Override
        public void cleanupExpiredTokens() {
            throw new UnsupportedOperationException();
        }
    }

    private static final PasswordEncoder PASSWORD_ENCODER = new PasswordEncoder() {
        @Override
        public String encode(CharSequence rawPassword) {
            return "hashed:" + rawPassword;
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return encode(rawPassword).equals(encodedPassword);
        }
    };

    private static final EmailServicePort NO_EMAIL = new EmailServicePort() {
        @Override
        public void sendPasswordResetEmail(String email, String otpCode, String resetToken) {
            fail("No email expected");
        }

        @Override
        public void sendPasswordResetConfirmationEmail(String email) {
            fail("No confirmation email expected for a blocked user");
        }
    };

    private static AppUser userWithStatus(UserStatus status) {
        AppUser user = AppUser.create(
                UserId.create("01ARZ3NDEKTSV4RRFFQ69G5FAV"),
                "Nguyen Van A",
                Username.create("0912345678"),
                Email.create(EMAIL),
                Password.create(ORIGINAL_PASSWORD_HASH),
                ProfileImage.create(null),
                IdCardNumber.create("123456789012"),
                new HashSet<>(),
                Instant.now());
        user.setUserStatus(status);
        return user;
    }

    private static PasswordResetToken validToken() {
        Instant now = AppClock.currentUTC();
        return PasswordResetToken.restore(1L, RESET_TOKEN, "01ARZ3NDEKTSV4RRFFQ69G5FAV", EMAIL,
                now, now.plusSeconds(900), false, OTP);
    }

    private static PasswordResetRequest request() {
        return PasswordResetRequest.builder()
                .resetToken(RESET_TOKEN)
                .otpCode(OTP)
                .newPassword("N3wP@ssw0rd!")
                .confirmPassword("N3wP@ssw0rd!")
                .build();
    }

    private static final UserId USER_ID = UserId.create("01ARZ3NDEKTSV4RRFFQ69G5FAV");

    private static RefreshToken activeSession() {
        Instant now = AppClock.currentUTC();
        return RefreshToken.startSession(1L, USER_ID, "hash-of-old-session", now,
                Duration.ofDays(7), Duration.ofDays(30), "Mozilla/5.0", "203.0.113.7");
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"LOCK", "TERMINATED", "DELETED"})
    void verifyOtpAndResetPassword_rejectsBlockedUserWithoutChangingAnything(UserStatus status) {
        AppUser user = userWithStatus(status);
        FakeUserDomainRepository userRepository = new FakeUserDomainRepository(user);
        FakePasswordResetTokenDomainRepository tokenRepository = new FakePasswordResetTokenDomainRepository(validToken());
        InMemoryRefreshTokenRepository refreshTokens = new InMemoryRefreshTokenRepository();
        PasswordResetService service = new PasswordResetService(tokenRepository, userRepository, PASSWORD_ENCODER,
                NO_EMAIL, refreshTokens);

        AuthenticationFailedException exception = assertThrows(AuthenticationFailedException.class,
                () -> service.verifyOtpAndResetPassword(request()));

        assertEquals("Account is locked or disabled.", exception.getMessage());
        assertEquals(ORIGINAL_PASSWORD_HASH, user.getPassword().getHashedValue());
        assertEquals(0, userRepository.saveCount);
        assertFalse(tokenRepository.markedAsUsed);
        assertTrue(refreshTokens.revocations.isEmpty());
    }

    /** Whoever knew the old password may hold a session: a reset must end them all. */
    @Test
    void verifyOtpAndResetPassword_revokesEverySessionOfTheUser() {
        AppUser user = userWithStatus(UserStatus.ACTIVE);
        InMemoryRefreshTokenRepository refreshTokens = new InMemoryRefreshTokenRepository();
        refreshTokens.save(activeSession());
        List<String> confirmations = new ArrayList<>();
        EmailServicePort email = new EmailServicePort() {
            @Override
            public void sendPasswordResetEmail(String address, String otpCode, String resetToken) {
                fail("No reset email expected");
            }

            @Override
            public void sendPasswordResetConfirmationEmail(String address) {
                confirmations.add(address);
            }
        };
        PasswordResetService service = new PasswordResetService(
                new FakePasswordResetTokenDomainRepository(validToken()),
                new FakeUserDomainRepository(user), PASSWORD_ENCODER, email, refreshTokens);

        service.verifyOtpAndResetPassword(request());

        assertEquals("hashed:N3wP@ssw0rd!", user.getPassword().getHashedValue());
        assertEquals(List.of("user:" + USER_ID.getValue() + ":PASSWORD_RESET"), refreshTokens.revocations);
        assertTrue(refreshTokens.get(1L).isRevoked());
        assertEquals(List.of(EMAIL), confirmations);
    }
}
