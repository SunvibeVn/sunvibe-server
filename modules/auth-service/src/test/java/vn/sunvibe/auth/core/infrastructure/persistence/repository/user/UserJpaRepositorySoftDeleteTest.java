package vn.sunvibe.auth.core.infrastructure.persistence.repository.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import vn.sunvibe.auth.core.domain.model.user.enums.KycStatus;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.user.UserEntity;
import vn.sunvibe.shared.crypto.AesGcmCryptoService;

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

    @Test
    void existsByEmail_findsNonDeletedRow() {
        userJpaRepository.save(buildUser("id-5", "0933333333", "e@example.com", null));

        assertTrue(userJpaRepository.existsByEmail("e@example.com"));
    }

    @Test
    void existsByIdCardNumberHash_findsNonDeletedRow() {
        UserEntity active = buildUser("id-6", "0944444444", "f@example.com", null);
        active.setIdCardNumberHash("active-hash");
        userJpaRepository.save(active);

        assertTrue(userJpaRepository.existsByIdCardNumberHash("active-hash"));
    }

    @Test
    void findByUsername_ignoresSoftDeletedRow() {
        userJpaRepository.save(buildUser("id-7", "0955555555", "g@example.com", Instant.now()));

        assertTrue(userJpaRepository.findByUsername("0955555555").isEmpty());
    }

    @Test
    void findByEmail_ignoresSoftDeletedRow() {
        userJpaRepository.save(buildUser("id-8", "0966666666", "h@example.com", Instant.now()));

        assertTrue(userJpaRepository.findByEmail("h@example.com").isEmpty());
    }

    /**
     * Soft-deleted users must not block re-use of their username, email or
     * ID card. H2 has no partial indexes, so this only pins that the entity
     * no longer declares full (all-rows) uniqueness; the Postgres partial
     * unique indexes in the Flyway migration enforce it for live rows.
     */
    @Test
    void save_allowsActiveUserReusingSoftDeletedUsersIdentifiers() {
        UserEntity deleted = buildUser("id-9", "0977777777", "i@example.com", Instant.now());
        deleted.setIdCardNumberHash("reused-hash");
        userJpaRepository.saveAndFlush(deleted);

        UserEntity active = buildUser("id-10", "0977777777", "i@example.com", null);
        active.setIdCardNumberHash("reused-hash");
        userJpaRepository.saveAndFlush(active);

        assertTrue(userJpaRepository.findById("id-9").isPresent());
        assertTrue(userJpaRepository.findById("id-10").isPresent());
    }
}
