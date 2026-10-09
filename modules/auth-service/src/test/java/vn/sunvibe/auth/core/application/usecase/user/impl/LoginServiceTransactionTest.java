package vn.sunvibe.auth.core.application.usecase.user.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.auth.core.application.command.auth.ClientInfo;
import vn.sunvibe.auth.core.application.command.auth.LoginCommand;
import vn.sunvibe.auth.core.application.exception.auth.AccountTemporarilyLockedException;
import vn.sunvibe.auth.core.application.exception.auth.AuthenticationFailedException;
import vn.sunvibe.auth.core.application.shared.LoginLockoutPolicy;
import vn.sunvibe.auth.core.application.usecase.auth.AccessTokenGenerateUseCase;
import vn.sunvibe.auth.core.application.usecase.auth.RefreshTokenGenerateUseCase;
import vn.sunvibe.auth.core.application.usecase.auth.impl.AuthResultAssembler;
import vn.sunvibe.auth.core.domain.model.user.enums.KycStatus;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.infrastructure.persistence.adapter.user.UserLoginAttemptRepositoryAdapter;
import vn.sunvibe.auth.core.infrastructure.persistence.adapter.user.UserRepositoryAdapter;
import vn.sunvibe.auth.core.infrastructure.persistence.converter.auto.UserMapperImpl;
import vn.sunvibe.auth.core.infrastructure.persistence.converter.auto.UserVOMapperImpl;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.user.UserEntity;
import vn.sunvibe.auth.core.infrastructure.persistence.repository.user.UserJpaRepository;
import vn.sunvibe.auth.core.testsupport.TestCookies;
import vn.sunvibe.common.crypto.AesGcmCryptoService;
import vn.sunvibe.auth.core.testsupport.RecordingAuditLog;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Failed logins end in an exception, yet their count must be committed;
 * otherwise the lockout never triggers. Runs the real service and adapters
 * against H2 without a test-managed transaction.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({LoginService.class, UserRepositoryAdapter.class, UserLoginAttemptRepositoryAdapter.class, UserMapperImpl.class,
        UserVOMapperImpl.class, AesGcmCryptoService.class, LoginServiceTransactionTest.Fakes.class})
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "app.crypto.id-card-key=bRPKQTY8NxXKMp8utgjOlU9SR5TugGss37yotjTNq64=",
        "app.crypto.id-card-index-key=HM5Eo9BtYz7DTj8eSdV0Dn9CwAEgWoqjDFTxfcLXtvw="
})
class LoginServiceTransactionTest {

    private static final String USER_ID = "01ARZ3NDEKTSV4RRFFQ69G5FAV";
    private static final String USERNAME = "0912345678";

    @TestConfiguration
    static class Fakes {
        @Bean
        AuthenticationManager authenticationManager() {
            return authentication -> {
                throw new BadCredentialsException("Bad credentials");
            };
        }

        @Bean
        AccessTokenGenerateUseCase accessTokenGenerateUseCase() {
            return username -> {
                throw new AssertionError("No token for a failed login");
            };
        }

        @Bean
        RefreshTokenGenerateUseCase refreshTokenGenerateUseCase() {
            return (userId, clientInfo) -> {
                throw new AssertionError("No session for a failed login");
            };
        }

        @Bean
        AuthResultAssembler authResultAssembler() {
            return new AuthResultAssembler(TestCookies.cookieService());
        }

        @Bean
        RecordingAuditLog auditLog() {
            return new RecordingAuditLog();
        }

        @Bean
        LoginLockoutPolicy loginLockoutPolicy() {
            return new LoginLockoutPolicy(5, Duration.ofMinutes(15), Duration.ofHours(24));
        }
    }

    @Autowired
    private LoginService loginService;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @BeforeEach
    void insertUser() {
        UserEntity entity = new UserEntity();
        entity.setId(USER_ID);
        entity.setFullName("Nguyen Van A");
        entity.setUsername(USERNAME);
        entity.setPassword("hashed-password");
        entity.setKycStatus(KycStatus.UNVERIFIED);
        entity.setUserStatus(UserStatus.ACTIVE);
        entity.setCreatedDate(Instant.now());
        entity.setLastUpdate(Instant.now());
        userJpaRepository.save(entity);
    }

    @AfterEach
    void cleanUp() {
        userJpaRepository.deleteAll();
    }

    private void loginWithWrongPassword() {
        loginService.execute(new LoginCommand(USERNAME, "wrong"), ClientInfo.unknown());
    }

    @Test
    void failedAttemptsAreCommittedAndTheFifthLocksTheAccount() {
        for (int i = 1; i <= 4; i++) {
            assertThrows(AuthenticationFailedException.class, this::loginWithWrongPassword);
            assertEquals(i, userJpaRepository.findById(USER_ID).orElseThrow().getFailedLoginAttempts());
        }

        assertThrows(AccountTemporarilyLockedException.class, this::loginWithWrongPassword);

        UserEntity stored = userJpaRepository.findById(USER_ID).orElseThrow();
        assertEquals(5, stored.getFailedLoginAttempts());
        assertNotNull(stored.getLockedUntil());
        assertTrue(stored.getLockedUntil().isAfter(Instant.now().plus(Duration.ofMinutes(14))));

        // Locked now: refused up front, and the refusal itself is not counted
        assertThrows(AccountTemporarilyLockedException.class, this::loginWithWrongPassword);
        assertEquals(5, userJpaRepository.findById(USER_ID).orElseThrow().getFailedLoginAttempts());
    }
}
