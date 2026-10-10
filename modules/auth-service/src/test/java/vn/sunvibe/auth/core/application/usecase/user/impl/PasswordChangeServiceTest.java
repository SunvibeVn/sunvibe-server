package vn.sunvibe.auth.core.application.usecase.user.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.sunvibe.auth.core.application.command.auth.ClientInfo;
import vn.sunvibe.auth.core.application.command.auth.LoginCommand;
import vn.sunvibe.auth.core.application.command.auth.PasswordChangeCommand;
import vn.sunvibe.auth.core.application.query.auth.AuthResult;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditEventType;
import vn.sunvibe.auth.core.application.usecase.user.LoginUseCase;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.vo.*;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.testsupport.InMemoryRefreshTokenRepository;
import vn.sunvibe.auth.core.testsupport.RecordingAuditLog;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PasswordChangeServiceTest {

    private static final UserId USER_ID = UserId.create("01ARZ3NDEKTSV4RRFFQ69G5FAV");
    private static final String USERNAME = "0912345678";

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

    private AppUser user;
    private InMemoryRefreshTokenRepository refreshTokens;
    private PasswordChangeService service;
    private final RecordingAuditLog audit = new RecordingAuditLog();
    /** Revocations already recorded when each new session was started. */
    private final List<List<String>> sessionsStartedAfter = new ArrayList<>();

    /** Records sessions started for an already authenticated user; a password login must not happen. */
    private final LoginUseCase sessions = new LoginUseCase() {
        @Override
        public AuthResult execute(LoginCommand loginCommand, ClientInfo clientInfo) {
            throw new AssertionError("The new session must not go through the password login");
        }

        @Override
        public AuthResult loginInternal(AppUser appUser, ClientInfo clientInfo) {
            sessionsStartedAfter.add(List.copyOf(refreshTokens.revocations));
            return AuthResult.of(appUser, "new-access-token", "new-refresh-token");
        }
    };

    @BeforeEach
    void setUp() {
        user = AppUser.create(USER_ID, "Nguyen Van A", Username.create(USERNAME), null,
                Password.create("hashed:Old@Passw0rd"), ProfileImage.create(null), null,
                new HashSet<>(Set.of("TENANT")), Instant.now());
        refreshTokens = new InMemoryRefreshTokenRepository();
        service = new PasswordChangeService(new UserDomainRepository() {
            @Override
            public boolean existsByUsername(String userName) { throw new UnsupportedOperationException(); }
            @Override
            public boolean existsByEmail(String email) { throw new UnsupportedOperationException(); }
            @Override
            public boolean existsByIdCardNumber(IdCardNumber idCardNumber) { throw new UnsupportedOperationException(); }
            @Override
            public AppUser save(AppUser appUser) { return appUser; }
            @Override
            public AppUser findByUsername(String username) { return user; }
            @Override
            public AppUser findByEmail(String email) { throw new UnsupportedOperationException(); }
            @Override
            public Optional<AppUser> findById(UserId userId) { throw new UnsupportedOperationException(); }
        }, PASSWORD_ENCODER, refreshTokens, audit, sessions);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USERNAME, null, List.of()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private AuthResult change(String currentPassword, String newPassword) {
        return service.execute(new PasswordChangeCommand(currentPassword, newPassword, ClientInfo.unknown()));
    }

    @Test
    void execute_signsOutEveryDeviceThenStartsAFreshSessionForThisOne() {
        AuthResult result = change("Old@Passw0rd", "N3w@Passw0rd");

        assertEquals("hashed:N3w@Passw0rd", user.getPassword().getHashedValue());
        assertEquals(List.of("user:" + USER_ID.getValue() + ":PASSWORD_CHANGED"), refreshTokens.revocations);
        assertEquals(List.of(List.of("user:" + USER_ID.getValue() + ":PASSWORD_CHANGED")), sessionsStartedAfter,
                "the new session is started after the old ones were revoked");
        assertEquals("new-refresh-token", result.refreshToken());
        assertEquals(List.of(AuditEventType.PASSWORD_CHANGED), audit.types());
    }

    /** A login lockout (someone guessing) must not leave the real, signed-in user without a session. */
    @Test
    void execute_worksWhileTheLoginIsTemporarilyLocked() {
        user.setLockedUntil(Instant.now().plus(Duration.ofMinutes(15)));

        AuthResult result = change("Old@Passw0rd", "N3w@Passw0rd");

        assertEquals("new-access-token", result.accessToken());
    }

    @Test
    void execute_wrongCurrentPasswordKeepsSessions() {
        assertThrows(AuthValidationException.class, () -> change("Wrong@Passw0rd", "N3w@Passw0rd"));

        assertTrue(refreshTokens.revocations.isEmpty());
        assertTrue(sessionsStartedAfter.isEmpty());
        assertEquals(List.of(AuditEventType.PASSWORD_CHANGE_FAILED), audit.types());
        assertEquals("WRONG_CURRENT_PASSWORD", audit.last().details());
    }

    @Test
    void execute_rejectsTheSamePassword() {
        assertThrows(AuthValidationException.class, () -> change("Old@Passw0rd", "Old@Passw0rd"));

        assertEquals("hashed:Old@Passw0rd", user.getPassword().getHashedValue());
        assertTrue(refreshTokens.revocations.isEmpty());
    }
}
