package vn.sunvibe.auth.core.application.usecase.auth.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.sunvibe.auth.core.application.exception.auth.AuthenticationFailedException;
import vn.sunvibe.auth.core.application.usecase.external.email.EmailServicePort;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.*;
import vn.sunvibe.auth.core.domain.repository.auth.PasswordResetTokenDomainRepository;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.infrastructure.security.provider.JWTProvider;
import vn.sunvibe.auth.core.presentation.dto.request.auth.PasswordResetRequest;
import vn.sunvibe.common.time.AppClock;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plain-JUnit test with hand-written fakes (Mockito isn't on this module's
 * classpath): a locked/disabled user must not reset their password or get
 * tokens through the public OTP reset endpoint.
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
        public boolean existsByIdCardNumberHash(String idCardNumberHash) {
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

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"LOCK", "TERMINATED", "DELETED"})
    void verifyOtpAndResetPassword_rejectsBlockedUserWithoutChangingPassword(UserStatus status) {
        AppUser user = userWithStatus(status);
        FakeUserDomainRepository userRepository = new FakeUserDomainRepository(user);
        FakePasswordResetTokenDomainRepository tokenRepository = new FakePasswordResetTokenDomainRepository(validToken());
        JWTProvider jwtProvider = new JWTProvider(username -> {
            throw new AssertionError("No access token may be generated for a blocked user");
        });
        PasswordResetService service = new PasswordResetService(tokenRepository, userRepository, PASSWORD_ENCODER,
                NO_EMAIL, jwtProvider, (userId, fingerprint) -> {
                    throw new AssertionError("No refresh token may be generated for a blocked user");
                });

        AuthenticationFailedException exception = assertThrows(AuthenticationFailedException.class,
                () -> service.verifyOtpAndResetPassword(request()));

        assertEquals("Account is locked or disabled.", exception.getMessage());
        assertEquals(ORIGINAL_PASSWORD_HASH, user.getPassword().getHashedValue());
        assertEquals(0, userRepository.saveCount);
        assertFalse(tokenRepository.markedAsUsed);
    }

    @Test
    void verifyOtpAndResetPassword_lockedUserGetsNoTokens() {
        AppUser user = userWithStatus(UserStatus.LOCK);
        boolean[] refreshTokenIssued = {false};
        PasswordResetService service = new PasswordResetService(
                new FakePasswordResetTokenDomainRepository(validToken()),
                new FakeUserDomainRepository(user), PASSWORD_ENCODER, NO_EMAIL,
                new JWTProvider(username -> {
                    throw new AssertionError("No access token may be generated for a locked user");
                }),
                (userId, fingerprint) -> {
                    refreshTokenIssued[0] = true;
                    return "refresh";
                });

        assertThrows(AuthenticationFailedException.class, () -> service.verifyOtpAndResetPassword(request()));
        assertFalse(refreshTokenIssued[0]);
    }
}
