package vn.sunvibe.auth.core.application.usecase.auth.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.auth.core.application.exception.auth.VerificationException;
import vn.sunvibe.auth.core.application.shared.VerificationPolicy;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationChannel;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.vo.*;
import vn.sunvibe.auth.core.infrastructure.persistence.adapter.auth.VerificationCodeRepositoryAdapter;
import vn.sunvibe.auth.core.infrastructure.persistence.repository.auth.VerificationCodeJpaRepository;
import vn.sunvibe.auth.core.testsupport.InMemoryUserRepository;
import vn.sunvibe.auth.core.testsupport.RecordingAuditLog;
import vn.sunvibe.auth.core.testsupport.RecordingEmailService;
import vn.sunvibe.auth.core.testsupport.RecordingSmsSender;
import vn.sunvibe.shared.crypto.AesGcmCryptoService;
import vn.sunvibe.shared.generator.id.IdGenerator;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Wrong verification codes end in an exception, yet their count must be
 * committed; otherwise a 6-digit code could be guessed for free.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ContactVerificationService.class, VerificationCodeRepositoryAdapter.class, AesGcmCryptoService.class,
        ContactVerificationServiceTransactionTest.Fakes.class})
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "app.crypto.id-card-key=bRPKQTY8NxXKMp8utgjOlU9SR5TugGss37yotjTNq64=",
        "app.crypto.id-card-index-key=HM5Eo9BtYz7DTj8eSdV0Dn9CwAEgWoqjDFTxfcLXtvw="
})
class ContactVerificationServiceTransactionTest {

    private static final String PHONE = "0912345678";

    @TestConfiguration
    static class Fakes {
        @Bean
        InMemoryUserRepository userRepository() {
            return new InMemoryUserRepository().with(AppUser.create(UserId.create("01ARZ3NDEKTSV4RRFFQ69G5FAV"),
                    "Nguyen Van A", Username.create(PHONE), null, Password.create("hashed"), ProfileImage.create(null),
                    null, new HashSet<>(), Instant.now()));
        }

        @Bean
        RecordingSmsSender smsSender() {
            return new RecordingSmsSender();
        }

        @Bean
        RecordingEmailService emailService() {
            return new RecordingEmailService();
        }

        @Bean
        IdGenerator<Long> snowflakeIdGenerator() {
            AtomicLong ids = new AtomicLong();
            return ids::incrementAndGet;
        }

        @Bean
        VerificationPolicy verificationPolicy() {
            return new VerificationPolicy(Duration.ofMinutes(10), 5, Duration.ofSeconds(60), 5);
        }

        @Bean
        RecordingAuditLog auditLog() {
            return new RecordingAuditLog();
        }
    }

    @Autowired
    private ContactVerificationService service;

    @Autowired
    private RecordingSmsSender sms;

    @Autowired
    private VerificationCodeJpaRepository jpaRepository;

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        jpaRepository.deleteAll();
    }

    @Test
    void wrongCodesAreCommittedAndTheFifthBurnsTheCode() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(PHONE, null, List.of()));
        service.sendCode(VerificationChannel.PHONE);
        String wrongCode = sms.lastCode().equals("000000") ? "111111" : "000000";

        for (int i = 1; i <= 5; i++) {
            assertThrows(VerificationException.class, () -> service.confirm(VerificationChannel.PHONE, wrongCode));
            assertEquals(i, jpaRepository.findAll().getFirst().getFailedAttempts());
        }

        assertNotNull(jpaRepository.findAll().getFirst().getConsumedAt(), "burned after the 5th wrong code");
    }
}
