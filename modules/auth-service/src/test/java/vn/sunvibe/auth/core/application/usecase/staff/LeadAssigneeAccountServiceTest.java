package vn.sunvibe.auth.core.application.usecase.staff;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import vn.sunvibe.auth.core.application.usecase.staff.impl.LeadAssigneeAccountService;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.RoleName;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.*;
import vn.sunvibe.auth.core.testsupport.InMemoryUserRepository;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Who may be given an owner lead (acquisition-owner-lead-queue spec 5.4, Q9-16): an active SALE or SALE_LEADER. */
class LeadAssigneeAccountServiceTest {

    private static final String USER_ID = "01SALE00000000000000000000";

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final LeadAssigneeAccountService service = new LeadAssigneeAccountService(users);

    private AppUser store(UserStatus status, RoleName... roles) {
        Set<String> names = new HashSet<>();
        for (RoleName role : roles) {
            names.add(role.name());
        }
        AppUser user = AppUser.create(UserId.create(USER_ID), "Staff", Username.create("0900000009"), null,
                Password.create("hashed"), ProfileImage.create(null), null, names, Instant.parse("2026-01-01T00:00:00Z"));
        user.setUserStatus(status);
        users.with(user);
        return user;
    }

    @ParameterizedTest
    @EnumSource(value = RoleName.class, names = {"SALE", "SALE_LEADER"})
    void isEligibleOwnerLeadAssignee_activeSaleOrSaleLeader_isTrue(RoleName role) {
        store(UserStatus.ACTIVE, role);

        assertTrue(service.isEligibleOwnerLeadAssignee(USER_ID));
    }

    @Test
    void isEligibleOwnerLeadAssignee_saleLeaderAlongsideCustomer_isTrue() {
        store(UserStatus.ACTIVE, RoleName.CUSTOMER, RoleName.SALE_LEADER);

        assertTrue(service.isEligibleOwnerLeadAssignee(USER_ID));
    }

    @ParameterizedTest
    @EnumSource(value = RoleName.class, names = {"CUSTOMER", "OWNER", "ADMIN"})
    void isEligibleOwnerLeadAssignee_withoutSaleRole_isFalse(RoleName role) {
        store(UserStatus.ACTIVE, role);

        assertFalse(service.isEligibleOwnerLeadAssignee(USER_ID));
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"LOCK", "TERMINATED", "DELETED"})
    void isEligibleOwnerLeadAssignee_saleWhoMayNotSignIn_isFalse(UserStatus status) {
        store(status, RoleName.SALE);

        assertFalse(service.isEligibleOwnerLeadAssignee(USER_ID));
    }

    @Test
    void isEligibleOwnerLeadAssignee_softDeletedSale_isFalse() {
        store(UserStatus.ACTIVE, RoleName.SALE).deactivate();

        assertFalse(service.isEligibleOwnerLeadAssignee(USER_ID));
    }

    @Test
    void isEligibleOwnerLeadAssignee_unknownId_isFalseAndDoesNotThrow() {
        assertFalse(service.isEligibleOwnerLeadAssignee(USER_ID));
    }

    @Test
    void isEligibleOwnerLeadAssignee_blankOrNullId_isFalse() {
        store(UserStatus.ACTIVE, RoleName.SALE);

        assertFalse(service.isEligibleOwnerLeadAssignee(" "));
        assertFalse(service.isEligibleOwnerLeadAssignee(""));
        assertFalse(service.isEligibleOwnerLeadAssignee(null));
    }
}
