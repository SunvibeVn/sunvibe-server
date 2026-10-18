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
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.*;
import vn.sunvibe.auth.core.domain.repository.user.StaffAccountRow;
import vn.sunvibe.auth.core.infrastructure.persistence.converter.auto.UserMapperImpl;
import vn.sunvibe.auth.core.infrastructure.persistence.converter.auto.UserVOMapperImpl;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.user.UserRoleEntity;
import vn.sunvibe.shared.crypto.AesGcmCryptoService;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The staff queries behind the owner lead assignee list and the staff names (acquisition-owner-lead-queue-ui
 * W11-1, W11-3, C-35): a scalar projection that never loads or rewrites a user's role collection, and a name lookup
 * that does not filter soft-deleted, locked or role-less accounts. Runs on the slice database like the other
 * adapter tests, with one row per role of the {@code roles} table seed.
 */
@DataJpaTest
@Import({AesGcmCryptoService.class, UserRepositoryAdapter.class, UserMapperImpl.class, UserVOMapperImpl.class})
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "app.crypto.id-card-key=bRPKQTY8NxXKMp8utgjOlU9SR5TugGss37yotjTNq64=",
        "app.crypto.id-card-index-key=HM5Eo9BtYz7DTj8eSdV0Dn9CwAEgWoqjDFTxfcLXtvw="
})
class UserRepositoryAdapterStaffQueryTest {

    private static final Set<String> ASSIGNEE_ROLES = Set.of("SALE", "SALE_LEADER");

    @Autowired
    private UserRepositoryAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    private int counter;

    @BeforeEach
    void seedRoles() {
        for (RoleName role : RoleName.values()) {
            UserRoleEntity entity = new UserRoleEntity();
            entity.setName(role.name());
            entityManager.persist(entity);
        }
        entityManager.flush();
    }

    private String saveUser(String fullName, UserStatus status, Instant deletedAt, String... roles) {
        int n = ++counter;
        String id = String.format("01STAF%020d", n);
        AppUser user = AppUser.create(UserId.create(id), fullName, Username.create(String.format("09%08d", n)), null,
                Password.create("hashed"), ProfileImage.create(null), null, new HashSet<>(Set.of(roles)),
                Instant.parse("2026-01-01T00:00:00Z"));
        user.setUserStatus(status);
        user.setDeletedAt(deletedAt);
        adapter.save(user);
        entityManager.flush();
        entityManager.clear();
        return id;
    }

    private Set<String> storedRoles(String userId) {
        entityManager.flush();
        entityManager.clear();
        @SuppressWarnings("unchecked")
        List<String> names = entityManager.createNativeQuery(
                        "SELECT r.name FROM user_roles ur JOIN roles r ON r.id = ur.role_id WHERE ur.user_id = :id")
                .setParameter("id", userId)
                .getResultList();
        return Set.copyOf(names);
    }

    @Test
    void signInAbleByAnyRoleExcludesBlockedAndDeleted() {
        String active = saveUser("Active", UserStatus.ACTIVE, null, "SALE");
        String pending = saveUser("Pending", UserStatus.PENDING, null, "SALE_LEADER");
        saveUser("Locked", UserStatus.LOCK, null, "SALE");
        saveUser("Terminated", UserStatus.TERMINATED, null, "SALE");
        saveUser("StatusDeleted", UserStatus.DELETED, null, "SALE");
        saveUser("SoftDeleted", UserStatus.ACTIVE, Instant.parse("2026-02-01T00:00:00Z"), "SALE");
        saveUser("CustomerOnly", UserStatus.ACTIVE, null, "CUSTOMER");
        saveUser("Reviewer", UserStatus.ACTIVE, null, "LISTING_REVIEWER");
        saveUser("Roleless", UserStatus.ACTIVE, null);

        List<StaffAccountRow> rows = adapter.findSignInAbleByAnyRole(ASSIGNEE_ROLES, 500);

        assertEquals(Set.of(active, pending), rows.stream().map(StaffAccountRow::userId).collect(java.util.stream.Collectors.toSet()));
        assertEquals(2, rows.size());
    }

    @Test
    void signInAbleByAnyRole_oneRowPerMatchingRoleAndOtherRolesNotListed() {
        String both = saveUser("Both", UserStatus.ACTIVE, null, "SALE", "SALE_LEADER", "CUSTOMER", "LISTING_REVIEWER");

        List<StaffAccountRow> rows = adapter.findSignInAbleByAnyRole(ASSIGNEE_ROLES, 500);

        assertEquals(Set.of(new StaffAccountRow(both, "Both", "SALE"), new StaffAccountRow(both, "Both", "SALE_LEADER")),
                Set.copyOf(rows));
        assertEquals(2, rows.size());
    }

    /** The cut counts accounts, not rows, and uses the same order as the service (Vietnamese name, then id). */
    @Test
    void signInAbleByAnyRole_cutsToTheFirstAccountsByVietnameseNameOrder() {
        saveUser("Đạt", UserStatus.ACTIVE, null, "SALE");
        saveUser("Dũng", UserStatus.ACTIVE, null, "SALE");
        saveUser("Bình", UserStatus.ACTIVE, null, "SALE");
        saveUser("Ánh", UserStatus.ACTIVE, null, "SALE", "SALE_LEADER");
        saveUser("An", UserStatus.ACTIVE, null, "SALE");

        List<StaffAccountRow> rows = adapter.findSignInAbleByAnyRole(ASSIGNEE_ROLES, 3);

        assertEquals(List.of("An", "Ánh", "Ánh", "Bình"), rows.stream().map(StaffAccountRow::fullName).toList());
    }

    @Test
    void fullNamesIncludeSoftDeletedAndLocked() {
        String active = saveUser("Active", UserStatus.ACTIVE, null, "SALE");
        String locked = saveUser("Locked", UserStatus.LOCK, null, "SALE");
        String softDeleted = saveUser("SoftDeleted", UserStatus.DELETED, Instant.parse("2026-02-01T00:00:00Z"), "SALE");
        String roleless = saveUser("Roleless", UserStatus.ACTIVE, null);

        Map<String, String> names = adapter.findFullNamesByIds(
                List.of(active, locked, softDeleted, roleless, active, "01UNKNOWN0000000000000000"));

        assertEquals(Map.of(active, "Active", locked, "Locked", softDeleted, "SoftDeleted", roleless, "Roleless"), names);
    }

    @Test
    void fullNamesEmptyInput_returnsEmptyMap() {
        assertEquals(Map.of(), adapter.findFullNamesByIds(List.of()));
    }

    /** The projection never loads the role collection, so a later load and save keeps every role. */
    @Test
    void roleCollectionUntouched() {
        String id = saveUser("Many", UserStatus.ACTIVE, null, "SALE", "SALE_LEADER", "CUSTOMER", "LISTING_REVIEWER");
        adapter.findSignInAbleByAnyRole(ASSIGNEE_ROLES, 500);

        AppUser loaded = adapter.findById(UserId.from(id)).orElseThrow();
        loaded.recordLoginSuccess(Instant.now());
        adapter.save(loaded);

        assertEquals(Set.of("SALE", "SALE_LEADER", "CUSTOMER", "LISTING_REVIEWER"), storedRoles(id));
    }
}
