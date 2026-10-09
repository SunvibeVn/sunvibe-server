package vn.sunvibe.auth.core.infrastructure.persistence.adapter.user;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.vo.*;
import vn.sunvibe.auth.core.infrastructure.persistence.converter.auto.UserMapperImpl;
import vn.sunvibe.auth.core.infrastructure.persistence.converter.auto.UserVOMapperImpl;
import vn.sunvibe.common.crypto.AesGcmCryptoService;
import vn.sunvibe.common.crypto.CryptoService;

import java.time.Instant;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves the ID card number is encrypted at rest and its blind index is
 * written, by going through the real {@link UserRepositoryAdapter#save}
 * and reading the raw columns back with a native query.
 */
@DataJpaTest
@Import({AesGcmCryptoService.class, UserRepositoryAdapter.class, UserMapperImpl.class, UserVOMapperImpl.class})
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "app.crypto.id-card-key=bRPKQTY8NxXKMp8utgjOlU9SR5TugGss37yotjTNq64=",
        "app.crypto.id-card-index-key=HM5Eo9BtYz7DTj8eSdV0Dn9CwAEgWoqjDFTxfcLXtvw="
})
class UserRepositoryAdapterEncryptionTest {

    private static final String ID_CARD_NUMBER = "123456789012";

    @Autowired
    private UserRepositoryAdapter userRepositoryAdapter;

    @Autowired
    private CryptoService cryptoService;

    @Autowired
    private EntityManager entityManager;

    private static AppUser newUser() {
        return newUser("01ARZ3NDEKTSV4RRFFQ69G5FAV", "0912345678", Email.create("a@example.com"), IdCardNumber.create(ID_CARD_NUMBER));
    }

    private static AppUser newUser(String id, String username, Email email, IdCardNumber idCardNumber) {
        return AppUser.create(
                UserId.create(id),
                "Nguyen Van A",
                Username.create(username),
                email,
                Password.create("hashed-password"),
                ProfileImage.create(null),
                idCardNumber,
                new HashSet<>(),
                Instant.now());
    }

    @Test
    void save_userWithoutEmailOrIdCardNumber_storesNullsAndReloads() {
        userRepositoryAdapter.save(newUser("01ARZ3NDEKTSV4RRFFQ69G5FAW", "0911111111", null, null));
        userRepositoryAdapter.save(newUser("01ARZ3NDEKTSV4RRFFQ69G5FAX", "0922222222", null, null));
        entityManager.flush();
        entityManager.clear();

        Object[] row = (Object[]) entityManager.createNativeQuery(
                        "SELECT email, id_card_number, id_card_number_hash FROM users WHERE id = :id")
                .setParameter("id", "01ARZ3NDEKTSV4RRFFQ69G5FAW")
                .getSingleResult();
        assertNull(row[0]);
        assertNull(row[1]);
        assertNull(row[2]);

        AppUser reloaded = userRepositoryAdapter.findByUsername("0922222222");
        assertNull(reloaded.getEmail());
        assertNull(reloaded.getIdCardNumber());
    }

    @Test
    void existsByIdCardNumber_matchesTheBlindIndexWrittenBySave() {
        userRepositoryAdapter.save(newUser());
        entityManager.flush();
        entityManager.clear();

        assertTrue(userRepositoryAdapter.existsByIdCardNumber(IdCardNumber.create(ID_CARD_NUMBER)));
        assertFalse(userRepositoryAdapter.existsByIdCardNumber(IdCardNumber.create("987654321098")));
    }

    @Test
    void save_storesIdCardNumberEncryptedWithBlindIndex() {
        AppUser user = newUser();

        userRepositoryAdapter.save(user);
        entityManager.flush();
        entityManager.clear();

        Object[] row = (Object[]) entityManager.createNativeQuery(
                        "SELECT id_card_number, id_card_number_hash FROM users WHERE id = :id")
                .setParameter("id", "01ARZ3NDEKTSV4RRFFQ69G5FAV")
                .getSingleResult();
        String storedIdCardNumber = (String) row[0];
        String storedHash = (String) row[1];

        assertNotEquals(ID_CARD_NUMBER, storedIdCardNumber);
        assertFalse(storedIdCardNumber.contains(ID_CARD_NUMBER));
        assertEquals(ID_CARD_NUMBER, cryptoService.decrypt(storedIdCardNumber));
        assertEquals(cryptoService.blindIndex(ID_CARD_NUMBER), storedHash);
    }
}
