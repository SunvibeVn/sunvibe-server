package vn.sunvibe.auth.core.infrastructure.persistence.adapter.user;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.RoleName;
import vn.sunvibe.auth.core.domain.model.user.vo.*;
import vn.sunvibe.auth.core.infrastructure.persistence.converter.auto.UserMapperImpl;
import vn.sunvibe.auth.core.infrastructure.persistence.converter.auto.UserVOMapperImpl;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.user.UserRoleEntity;
import vn.sunvibe.shared.crypto.AesGcmCryptoService;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Role changes are single-row writes on {@code user_roles}, so two concurrent changes, or a role change and an
 * unrelated save of a stale aggregate (login, phone verification), cannot undo each other (auth-roles BR-9..BR-11).
 * Only the roles in the {@code roles} table exist; a missing one fails instead of being dropped.
 */
@DataJpaTest
@Import({AesGcmCryptoService.class, UserRepositoryAdapter.class, UserMapperImpl.class, UserVOMapperImpl.class})
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "app.crypto.id-card-key=bRPKQTY8NxXKMp8utgjOlU9SR5TugGss37yotjTNq64=",
        "app.crypto.id-card-index-key=HM5Eo9BtYz7DTj8eSdV0Dn9CwAEgWoqjDFTxfcLXtvw="
})
class UserRepositoryAdapterRoleTest {

    private static final UserId USER_ID = UserId.create("01ARZ3NDEKTSV4RRFFQ69G5FAV");

    @Autowired
    private UserRepositoryAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    /** The roles a Flyway-built database has, minus LISTING_REVIEWER (to test a missing row). */
    @BeforeEach
    void seedRoles() {
        for (RoleName role : RoleName.values()) {
            if (role != RoleName.LISTING_REVIEWER) {
                UserRoleEntity entity = new UserRoleEntity();
                entity.setName(role.name());
                entityManager.persist(entity);
            }
        }
        entityManager.flush();
    }

    private AppUser saveUser(String... roles) {
        AppUser user = AppUser.create(USER_ID, "Nguyen Van A", Username.create("0912345678"), null,
                Password.create("hashed"), ProfileImage.create(null), null, new HashSet<>(Set.of(roles)), Instant.now());
        AppUser saved = adapter.save(user);
        entityManager.flush();
        entityManager.clear();
        return saved;
    }

    private Set<String> storedRoles() {
        entityManager.flush();
        entityManager.clear();
        @SuppressWarnings("unchecked")
        List<String> names = entityManager.createNativeQuery(
                        "SELECT r.name FROM user_roles ur JOIN roles r ON r.id = ur.role_id WHERE ur.user_id = :id")
                .setParameter("id", USER_ID.getValue())
                .getResultList();
        return Set.copyOf(names);
    }

    @Test
    void addRole_missingRole_insertsItAndReportsAChange() {
        saveUser("CUSTOMER");

        assertTrue(adapter.addRole(USER_ID, RoleName.OWNER));
        assertEquals(Set.of("CUSTOMER", "OWNER"), storedRoles());
    }

    @Test
    void addRole_roleAlreadyHeld_changesNothing() {
        saveUser("OWNER");

        assertFalse(adapter.addRole(USER_ID, RoleName.OWNER));
        assertEquals(Set.of("OWNER"), storedRoles());
    }

    @Test
    void removeRole_heldRole_deletesItAndReportsAChange() {
        saveUser("CUSTOMER", "SALE");

        assertTrue(adapter.removeRole(USER_ID, RoleName.SALE));
        assertEquals(Set.of("CUSTOMER"), storedRoles());
    }

    @Test
    void removeRole_roleNotHeld_changesNothing() {
        saveUser("CUSTOMER");

        assertFalse(adapter.removeRole(USER_ID, RoleName.SALE));
    }

    @Test
    void addRole_roleMissingFromRolesTable_failsInsteadOfDroppingIt() {
        saveUser("CUSTOMER");

        assertThrows(IllegalStateException.class, () -> adapter.addRole(USER_ID, RoleName.LISTING_REVIEWER));
    }

    /** A save of an aggregate loaded before a revoke must not bring the revoked role back. */
    @Test
    void save_staleAggregateOfAnExistingUser_keepsTheStoredRoles() {
        saveUser("CUSTOMER", "SALE");
        AppUser loadedBeforeTheRevoke = adapter.findById(USER_ID).orElseThrow();
        adapter.removeRole(USER_ID, RoleName.SALE);
        entityManager.flush();
        entityManager.clear();

        loadedBeforeTheRevoke.recordLoginSuccess(Instant.now());
        adapter.save(loadedBeforeTheRevoke);

        assertEquals(Set.of("CUSTOMER"), storedRoles());
    }

    /** A new account with a role that has no row in roles would silently get no role. */
    @Test
    void save_newUserWithARoleMissingFromRolesTable_fails() {
        AppUser user = AppUser.create(USER_ID, "Nguyen Van A", Username.create("0912345678"), null,
                Password.create("hashed"), ProfileImage.create(null), null,
                new HashSet<>(Set.of("CUSTOMER", "LISTING_REVIEWER")), Instant.now());

        assertThrows(IllegalStateException.class, () -> adapter.save(user));
    }
}
