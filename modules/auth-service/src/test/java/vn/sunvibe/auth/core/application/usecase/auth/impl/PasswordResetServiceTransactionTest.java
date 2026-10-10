package vn.sunvibe.auth.core.application.usecase.auth.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.auth.core.application.command.auth.PasswordResetCommand;
import vn.sunvibe.auth.core.application.shared.PasswordResetPolicy;
import vn.sunvibe.auth.core.application.usecase.external.email.EmailServicePort;
import vn.sunvibe.auth.core.application.usecase.external.sms.SmsSenderPort;
import vn.sunvibe.auth.core.domain.exception.auth.InvalidOtpException;
import vn.sunvibe.auth.core.domain.exception.auth.PasswordResetTokenException;
import vn.sunvibe.auth.core.domain.model.user.vo.PasswordResetToken;
import vn.sunvibe.auth.core.domain.repository.auth.PasswordResetTokenDomainRepository;
import vn.sunvibe.auth.core.domain.repository.auth.RefreshTokenDomainRepository;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.infrastructure.persistence.adapter.auth.PasswordResetTokenRepositoryAdapter;
import vn.sunvibe.auth.core.infrastructure.persistence.repository.auth.PasswordResetTokenJpaRepository;
import vn.sunvibe.auth.core.testsupport.InMemoryRefreshTokenRepository;
import vn.sunvibe.auth.core.testsupport.InMemoryUserRepository;
import vn.sunvibe.auth.core.testsupport.RecordingEmailService;
import vn.sunvibe.auth.core.testsupport.RecordingSmsSender;
import vn.sunvibe.common.crypto.AesGcmCryptoService;
import vn.sunvibe.auth.core.testsupport.RecordingAuditLog;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Wrong OTP codes end in an exception, yet their count must be committed;
 * otherwise a 6-digit code could be guessed for free.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({PasswordResetService.class, PasswordResetTokenRepositoryAdapter.class, AesGcmCryptoService.class,
        PasswordResetServiceTransactionTest.Fakes.class})
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "app.crypto.id-card-key=bRPKQTY8NxXKMp8utgjOlU9SR5TugGss37yotjTNq64=",
        "app.crypto.id-card-index-key=HM5Eo9BtYz7DTj8eSdV0Dn9CwAEgWoqjDFTxfcLXtvw="
})
class PasswordResetServiceTransactionTest {

    @TestConfiguration
    static class Fakes {
        @Bean
        UserDomainRepository userDomainRepository() {
            return new InMemoryUserRepository();
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return new PasswordEncoder() {
                @Override
                public String encode(CharSequence rawPassword) {
                    return "hashed:" + rawPassword;
                }

                @Override
                public boolean matches(CharSequence rawPassword, String encodedPassword) {
                    return encode(rawPassword).equals(encodedPassword);
                }
            };
        }

        @Bean
        EmailServicePort emailServicePort() {
            return new RecordingEmailService();
        }

        @Bean
        SmsSenderPort smsSenderPort() {
            return new RecordingSmsSender();
        }

        @Bean
        RefreshTokenDomainRepository refreshTokenDomainRepository() {
            return new InMemoryRefreshTokenRepository();
        }

        @Bean
        RecordingAuditLog auditLog() {
            return new RecordingAuditLog();
        }

        @Bean
        PasswordResetPolicy passwordResetPolicy() {
            return new PasswordResetPolicy(5, Duration.ofSeconds(60));
        }
    }

    @Autowired
    private PasswordResetService service;

    @Autowired
    private PasswordResetTokenDomainRepository resetTokens;

    @Autowired
    private PasswordResetTokenJpaRepository jpaRepository;

    @AfterEach
    void cleanUp() {
        jpaRepository.deleteAll();
    }

    private static PasswordResetCommand wrongCode() {
        return new PasswordResetCommand("reset-token", "000000", "N3wP@ssw0rd!");
    }

    @Test
    void wrongCodesAreCommittedAndTheFifthBurnsTheToken() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        resetTokens.save(PasswordResetToken.restore(1L, "reset-token", "user-1", "a@example.com", now,
                now.plusSeconds(900), false, "123456"));

        for (int i = 1; i <= 4; i++) {
            assertThrows(InvalidOtpException.class, () -> service.verifyOtpAndResetPassword(wrongCode()));
            assertEquals(i, jpaRepository.findById(1L).orElseThrow().getFailedAttempts());
        }
        assertThrows(PasswordResetTokenException.class, () -> service.verifyOtpAndResetPassword(wrongCode()));

        assertTrue(jpaRepository.findById(1L).orElseThrow().getIsUsed());
    }
}
