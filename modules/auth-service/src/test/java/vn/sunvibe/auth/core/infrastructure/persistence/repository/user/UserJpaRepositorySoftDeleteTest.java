package vn.sunvibe.auth.core.infrastructure.persistence.repository.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import vn.sunvibe.auth.core.domain.model.user.enums.KycStatus;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.user.UserEntity;
import vn.sunvibe.common.crypto.AesGcmCryptoService;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import(AesGcmCryptoService.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "app.crypto.id-card-key=bRPKQTY8NxXKMp8utgjOlU9SR5TugGss37yotjTNq64=",
        "app.crypto.id-card-index-key=HM5Eo9BtYz7DTj8eSdV0Dn9CwAEgWoqjDFTxfcLXtvw="
})
class UserJpaRepositorySoftDeleteTest {

    @Autowired
    private UserJpaRepository userJpaRepository;

    private UserEntity buildUser(String id, String username, String email, Instant deletedAt) {
        UserEntity entity = new UserEntity();
        entity.setId(id);
        entity.setFullName("Nguyen Van A");
        entity.setUsername(username);
        entity.setEmail(email);
        entity.setPassword("hashed-password");
        entity.setIdCardNumber("123456789012");
        entity.setIdCardNumberHash("hash-" + id);
        entity.setKycStatus(KycStatus.UNVERIFIED);
        entity.setUserStatus(UserStatus.ACTIVE);
        entity.setCreatedDate(Instant.now());
        entity.setLastUpdate(Instant.now());
        entity.setDeletedAt(deletedAt);
        return entity;
    }

    @Test
    void existsByUsername_ignoresSoftDeletedRow() {
        userJpaRepository.save(buildUser("id-1", "0912345678", "a@example.com", Instant.now()));

        assertFalse(userJpaRepository.existsByUsername("0912345678"));
    }

    @Test
    void existsByUsername_findsNonDeletedRow() {
        userJpaRepository.save(buildUser("id-2", "0987654321", "b@example.com", null));

        assertTrue(userJpaRepository.existsByUsername("0987654321"));
    }

    @Test
    void existsByEmail_ignoresSoftDeletedRow() {
        userJpaRepository.save(buildUser("id-3", "0911111111", "c@example.com", Instant.now()));

        assertFalse(userJpaRepository.existsByEmail("c@example.com"));
    }

    @Test
    void existsByIdCardNumberHash_ignoresSoftDeletedRow() {
        UserEntity deleted = buildUser("id-4", "0922222222", "d@example.com", Instant.now());
        deleted.setIdCardNumberHash("shared-hash");
        userJpaRepository.save(deleted);

        assertFalse(userJpaRepository.existsByIdCardNumberHash("shared-hash"));
    }
}
