package vn.sunvibe.auth.core.infrastructure.persistence.adapter.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import vn.sunvibe.auth.core.domain.model.user.enums.KycStatus;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.user.UserEntity;
import vn.sunvibe.auth.core.infrastructure.persistence.repository.user.UserJpaRepository;
import vn.sunvibe.common.crypto.AesGcmCryptoService;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({PendingAccountRepositoryAdapter.class, AesGcmCryptoService.class})
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "app.crypto.id-card-key=bRPKQTY8NxXKMp8utgjOlU9SR5TugGss37yotjTNq64=",
        "app.crypto.id-card-index-key=HM5Eo9BtYz7DTj8eSdV0Dn9CwAEgWoqjDFTxfcLXtvw="
})
class PendingAccountRepositoryAdapterTest {

    private static final Instant NOW = Instant.now();

    @Autowired
    private PendingAccountRepositoryAdapter adapter;

    @Autowired
    private UserJpaRepository userJpaRepository;

    private UserEntity user(String id, UserStatus status, Duration age) {
        UserEntity entity = new UserEntity();
        entity.setId(id);
        entity.setFullName("Nguyen Van A");
        entity.setUsername("09" + id.substring(id.length() - 8));
        entity.setPassword("hashed-password");
        entity.setKycStatus(KycStatus.UNVERIFIED);
        entity.setUserStatus(status);
        entity.setCreatedDate(NOW.minus(age));
        entity.setLastUpdate(NOW);
        return entity;
    }

    @Test
    void findsOnlyUnverifiedPendingAccountsOlderThanTheCutoff_oldestFirst() {
        userJpaRepository.save(user("id-00000001", UserStatus.PENDING, Duration.ofDays(8)));
        userJpaRepository.save(user("id-00000002", UserStatus.PENDING, Duration.ofDays(20)));
        userJpaRepository.save(user("id-00000003", UserStatus.PENDING, Duration.ofDays(3)));      // too recent
        userJpaRepository.save(user("id-00000004", UserStatus.ACTIVE, Duration.ofDays(30)));      // not pending
        UserEntity verified = user("id-00000005", UserStatus.PENDING, Duration.ofDays(30));
        verified.setPhoneVerifiedAt(NOW);                                                        // verified
        userJpaRepository.save(verified);
        UserEntity deleted = user("id-00000006", UserStatus.PENDING, Duration.ofDays(30));
        deleted.setDeletedAt(NOW);                                                               // already gone
        userJpaRepository.save(deleted);

        List<UserId> expired = adapter.findUnverifiedPendingCreatedBefore(NOW.minus(Duration.ofDays(7)), 10);

        assertEquals(List.of(UserId.from("id-00000002"), UserId.from("id-00000001")), expired);
    }

    @Test
    void respectsTheLimit() {
        userJpaRepository.save(user("id-00000001", UserStatus.PENDING, Duration.ofDays(8)));
        userJpaRepository.save(user("id-00000002", UserStatus.PENDING, Duration.ofDays(9)));

        assertEquals(1, adapter.findUnverifiedPendingCreatedBefore(NOW.minus(Duration.ofDays(7)), 1).size());
    }
}
