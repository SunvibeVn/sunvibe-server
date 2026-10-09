package vn.sunvibe.auth.core.application.usecase.auth.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.auth.core.application.command.auth.ClientInfo;
import vn.sunvibe.auth.core.application.command.auth.TokenRefreshCommand;
import vn.sunvibe.auth.core.application.exception.auth.InvalidRefreshTokenException;
import vn.sunvibe.auth.core.application.shared.RefreshTokenPolicy;
import vn.sunvibe.auth.core.application.shared.RefreshTokenSecrets;
import vn.sunvibe.auth.core.application.usecase.auth.AccessTokenGenerateUseCase;
import vn.sunvibe.auth.core.domain.model.auth.RefreshToken;
import vn.sunvibe.auth.core.domain.model.auth.enums.RevokeReason;
import vn.sunvibe.auth.core.domain.model.auth.enums.TokenStatus;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.vo.IdCardNumber;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.repository.auth.RefreshTokenDomainRepository;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.infrastructure.persistence.adapter.auth.RefreshTokenRepositoryAdapter;
import vn.sunvibe.auth.core.infrastructure.persistence.converter.manual.RefreshTokenEntityMapper;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.auth.RefreshTokenEntity;
import vn.sunvibe.auth.core.infrastructure.persistence.repository.auth.RefreshTokenJpaRepository;
import vn.sunvibe.auth.core.testsupport.TestCookies;
import vn.sunvibe.common.crypto.AesGcmCryptoService;
import vn.sunvibe.common.generator.id.IdGenerator;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reuse detection answers 401, yet the revocation of the session must be
 * committed, not rolled back with the failed request. Runs the real service
 * against H2 without a test-managed transaction, so the service's own
 * transaction commits or rolls back for real.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({TokenRefreshService.class, RefreshTokenRepositoryAdapter.class, RefreshTokenEntityMapper.class,
        AesGcmCryptoService.class, TokenRefreshServiceTransactionTest.Fakes.class})
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "app.crypto.id-card-key=bRPKQTY8NxXKMp8utgjOlU9SR5TugGss37yotjTNq64=",
        "app.crypto.id-card-index-key=HM5Eo9BtYz7DTj8eSdV0Dn9CwAEgWoqjDFTxfcLXtvw="
})
class TokenRefreshServiceTransactionTest {

    private static final UserId USER_ID = UserId.create("01ARZ3NDEKTSV4RRFFQ69G5FAV");
    private static final String SECRET = "stolen-refresh-secret";

    @TestConfiguration
    static class Fakes {
        @Bean
        AuthResultAssembler authResultAssembler() {
            return new AuthResultAssembler(TestCookies.cookieService());
        }

        @Bean
        RefreshTokenPolicy refreshTokenPolicy() {
            return new RefreshTokenPolicy(Duration.ofDays(7), Duration.ofDays(30), Duration.ofSeconds(30));
        }

        @Bean
        IdGenerator<Long> snowflakeIdGenerator() {
            AtomicLong ids = new AtomicLong(100);
            return ids::incrementAndGet;
        }

        @Bean
        AccessTokenGenerateUseCase accessTokenGenerateUseCase() {
            return username -> {
                throw new AssertionError("No access token may be issued for a reused refresh token");
            };
        }

        @Bean
        UserDomainRepository userDomainRepository() {
            // Reuse is detected before the owner is looked up
            return new UserDomainRepository() {
                @Override
                public boolean existsByUsername(String userName) { throw new AssertionError(); }
                @Override
                public boolean existsByEmail(String email) { throw new AssertionError(); }
                @Override
                public boolean existsByIdCardNumber(IdCardNumber idCardNumber) { throw new AssertionError(); }
                @Override
                public AppUser save(AppUser user) { throw new AssertionError(); }
                @Override
                public AppUser findByUsername(String username) { throw new AssertionError(); }
                @Override
                public AppUser findByEmail(String email) { throw new AssertionError(); }
                @Override
                public Optional<AppUser> findById(UserId userId) { throw new AssertionError("User lookup not expected"); }
            };
        }
    }

    @Autowired
    private TokenRefreshService service;

    @Autowired
    private RefreshTokenDomainRepository refreshTokenRepository;

    @Autowired
    private RefreshTokenJpaRepository jpaRepository;

    @AfterEach
    void cleanUp() {
        jpaRepository.deleteAll();
    }

    @Test
    void reuseAfterGracePeriod_revocationIsCommittedDespiteTheFailure() {
        UUID familyId = UUID.randomUUID();
        Instant issuedAt = Instant.now().minus(Duration.ofHours(1)).truncatedTo(ChronoUnit.MICROS);
        Instant usedAt = Instant.now().minus(Duration.ofMinutes(10)).truncatedTo(ChronoUnit.MICROS);
        refreshTokenRepository.save(RefreshToken.load(1L, USER_ID, familyId, RefreshTokenSecrets.hash(SECRET),
                issuedAt, issuedAt.plus(Duration.ofDays(7)), issuedAt.plus(Duration.ofDays(30)), null, null,
                TokenStatus.USED, usedAt, null, null));
        refreshTokenRepository.save(RefreshToken.load(2L, USER_ID, familyId, "hash-of-successor",
                usedAt, usedAt.plus(Duration.ofDays(7)), issuedAt.plus(Duration.ofDays(30)), null, null,
                TokenStatus.ACTIVE, null, null, null));

        assertThrows(InvalidRefreshTokenException.class,
                () -> service.execute(new TokenRefreshCommand(SECRET, ClientInfo.unknown())));

        RefreshTokenEntity successor = jpaRepository.findById(2L).orElseThrow();
        assertEquals(TokenStatus.REVOKED, successor.getStatus());
        assertEquals(RevokeReason.REUSE_DETECTED, successor.getRevokeReason());
        assertEquals(TokenStatus.REVOKED, jpaRepository.findById(1L).orElseThrow().getStatus());
    }
}
