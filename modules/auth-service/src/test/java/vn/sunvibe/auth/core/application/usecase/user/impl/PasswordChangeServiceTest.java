package vn.sunvibe.auth.core.application.usecase.user.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.sunvibe.auth.core.application.command.auth.PasswordChangeCommand;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditEventType;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.vo.*;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.testsupport.InMemoryRefreshTokenRepository;
import vn.sunvibe.auth.core.testsupport.RecordingAuditLog;

import java.time.Instant;
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

    private InMemoryRefreshTokenRepository refreshTokens;
    private PasswordChangeService service;
    private final RecordingAuditLog audit = new RecordingAuditLog();

    @BeforeEach
    void setUp() {
        AppUser user = AppUser.create(USER_ID, "Nguyen Van A", Username.create(USERNAME), null,
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
        }, PASSWORD_ENCODER, refreshTokens, audit);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USERNAME, null, List.of()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void execute_signsOutEveryDevice() {
        service.execute(new PasswordChangeCommand("Old@Passw0rd", "N3w@Passw0rd"));

        assertEquals(List.of("user:" + USER_ID.getValue() + ":PASSWORD_CHANGED"), refreshTokens.revocations);
        assertEquals(List.of(AuditEventType.PASSWORD_CHANGED), audit.types());
    }

    @Test
    void execute_wrongCurrentPasswordKeepsSessions() {
        assertThrows(AuthValidationException.class,
                () -> service.execute(new PasswordChangeCommand("Wrong@Passw0rd", "N3w@Passw0rd")));

        assertTrue(refreshTokens.revocations.isEmpty());
        assertEquals(List.of(AuditEventType.PASSWORD_CHANGE_FAILED), audit.types());
        assertEquals("WRONG_CURRENT_PASSWORD", audit.last().details());
    }
}
