package vn.sunvibe.auth.core.application.usecase.user.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import vn.sunvibe.auth.core.application.shared.LoginLockoutPolicy;
import vn.sunvibe.auth.core.application.usecase.user.ReauthenticationResult;
import vn.sunvibe.auth.core.application.usecase.user.ReauthenticationResult.Outcome;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.vo.Password;
import vn.sunvibe.auth.core.domain.model.user.vo.ProfileImage;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.testsupport.AuthTestUsers;
import vn.sunvibe.auth.core.testsupport.InMemoryUserRepository;
import vn.sunvibe.auth.core.testsupport.NoDatabaseTransactions;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

/** auth-email-access D4-12, S-21, S-44: its own counter, with the policy of the login lock. */
class PasswordReauthenticationServiceTest {

    private static final String USER_ID = "01ARZ3NDEKTSV4RRFFQ69G5FAV";
    private static final String PASSWORD = "Right@Pass1";

    private int hashes;
    private final PasswordEncoder encoder = new PasswordEncoder() {
        @Override
        public String encode(CharSequence rawPassword) {
            return "hashed:" + rawPassword;
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            hashes++;
            return encode(rawPassword).equals(encodedPassword);
        }
    };

    private AppUser user;
    private InMemoryUserRepository users;
    private PasswordReauthenticationService service;

    @BeforeEach
    void setUp() {
        user = AuthTestUsers.withPhone(UserId.create(USER_ID), "Nguyen Van A", "0912345678", null,
                Password.create("hashed:" + PASSWORD), ProfileImage.create(null), null, new HashSet<>(), Instant.now());
        user.setCredentialVersion(3);
        users = new InMemoryUserRepository().with(user);
        service = new PasswordReauthenticationService(users, users, encoder,
                new LoginLockoutPolicy(5, Duration.ofMinutes(15), Duration.ofHours(24)),
                new TransactionTemplate(new NoDatabaseTransactions()));
    }

    @Test
    void reauth_returnsResult_throwsNothing() {
        assertEquals(new ReauthenticationResult(Outcome.OK, 3, null), service.check(USER_ID, PASSWORD));
        assertEquals(Outcome.WRONG, service.check(USER_ID, "wrong").outcome());
        assertEquals(Outcome.WRONG, service.check("01UNKNOWN00000000000000000", PASSWORD).outcome());
    }

    @Test
    void reauth_wrongPassword_counted_onItsOwnCounter() {
        service.check(USER_ID, "wrong");

        assertEquals(1, user.getReauthFailedAttempts());
        assertEquals(0, user.getFailedLoginAttempts(), "the login counter is another one");
    }

    @Test
    void reauth_fifthWrong_locks() {
        for (int i = 0; i < 4; i++) {
            assertEquals(Outcome.WRONG, service.check(USER_ID, "wrong").outcome());
        }

        ReauthenticationResult fifth = service.check(USER_ID, "wrong");

        assertEquals(Outcome.LOCKED, fifth.outcome());
        assertEquals(Duration.ofMinutes(15), fifth.lockedFor());
        assertNotNull(user.getReauthLockedUntil());
    }

    @Test
    void reauth_whileLocked_refusedWithoutAHash() {
        user.setReauthLockedUntil(Instant.now().plus(Duration.ofMinutes(10)));

        ReauthenticationResult result = service.check(USER_ID, PASSWORD);

        assertEquals(Outcome.LOCKED, result.outcome());
        assertTrue(result.lockedFor().compareTo(Duration.ZERO) > 0);
        assertEquals(0, hashes);
    }

    @Test
    void reauth_rightPassword_clearsTheCounter() {
        service.check(USER_ID, "wrong");
        user.setReauthLockedUntil(Instant.now().minus(Duration.ofMinutes(1)));

        assertEquals(Outcome.OK, service.check(USER_ID, PASSWORD).outcome());

        assertEquals(0, user.getReauthFailedAttempts());
        assertNull(user.getReauthLockedUntil());
    }

    /** D4-12: a stranger's login lock cannot stop a bind from an open session. */
    @Test
    void reauth_loginLock_doesNotStopIt() {
        user.setLockedUntil(Instant.now().plus(Duration.ofHours(1)));
        user.setFailedLoginAttempts(5);

        assertEquals(Outcome.OK, service.check(USER_ID, PASSWORD).outcome());
    }
}
