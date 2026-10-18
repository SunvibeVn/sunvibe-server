package vn.sunvibe.auth.core.application.usecase.staff;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;
import vn.sunvibe.auth.core.application.usecase.staff.impl.LeadAssigneeAccountService;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.RoleName;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.*;
import vn.sunvibe.auth.core.domain.repository.user.StaffAccountRow;
import vn.sunvibe.auth.core.testsupport.InMemoryUserRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    // ---------- eligible assignee list and display names (owner-lead-queue-ui W11-1, W11-3, C-35) ----------

    private int accountCounter;

    private AppUser account(String name, UserStatus status, RoleName... roles) {
        return account(String.format("01ACCT%020d", accountCounter + 1), name, status, roles);
    }

    private AppUser account(String id, String name, UserStatus status, RoleName... roles) {
        Set<String> names = new HashSet<>();
        for (RoleName role : roles) {
            names.add(role.name());
        }
        AppUser user = AppUser.create(UserId.create(id), name,
                Username.create(String.format("09%08d", ++accountCounter)), null, Password.create("hashed"),
                ProfileImage.create(null), null, names, Instant.parse("2026-01-01T00:00:00Z"));
        user.setUserStatus(status);
        users.with(user);
        return user;
    }

    @Test
    void listsEligibleAssigneesGroupedAndSortedByVietnameseName() {
        // stored out of order on purpose; Ánh holds both roles (two rows for one account)
        account("01ACCT00000000000000000005", "Đạt", UserStatus.ACTIVE, RoleName.SALE);
        account("01ACCT00000000000000000004", "Dũng", UserStatus.ACTIVE, RoleName.SALE);
        account("01ACCT00000000000000000003", "Bình", UserStatus.ACTIVE, RoleName.SALE_LEADER);
        account("01ACCT00000000000000000002", "Ánh", UserStatus.ACTIVE, RoleName.SALE, RoleName.SALE_LEADER);
        account("01ACCT00000000000000000001", "An", UserStatus.ACTIVE, RoleName.SALE);

        List<LeadAssigneeAccount> listed = service.listEligibleOwnerLeadAssignees();

        assertEquals(List.of("An", "Ánh", "Bình", "Dũng", "Đạt"),
                listed.stream().map(LeadAssigneeAccount::fullName).toList());
        assertEquals(Set.of("SALE", "SALE_LEADER"), listed.get(1).roles());
        assertEquals(Set.of("SALE"), listed.get(0).roles());
        assertEquals(Set.of("SALE_LEADER"), listed.get(2).roles());
    }

    @Test
    void list_sameNameTiesBreakByUserId() {
        account("01ACCT0000000000000000000B", "Lan", UserStatus.ACTIVE, RoleName.SALE);
        account("01ACCT0000000000000000000A", "Lan", UserStatus.ACTIVE, RoleName.SALE);

        assertEquals(List.of("01ACCT0000000000000000000A", "01ACCT0000000000000000000B"),
                service.listEligibleOwnerLeadAssignees().stream().map(LeadAssigneeAccount::userId).toList());
    }

    /** The roles reach the JSON as an array, so their order is stable: SALE before SALE_LEADER. */
    @Test
    void list_rolesOfAccountWithBothRolesKeepStableOrder() {
        account("Ánh", UserStatus.ACTIVE, RoleName.SALE_LEADER, RoleName.SALE);

        assertEquals(List.of("SALE", "SALE_LEADER"),
                new ArrayList<>(service.listEligibleOwnerLeadAssignees().get(0).roles()));
    }

    @Test
    void list_onlyAssigneeRolesAreReported() {
        account("Mai", UserStatus.ACTIVE, RoleName.SALE, RoleName.CUSTOMER, RoleName.LISTING_REVIEWER);

        assertEquals(Set.of("SALE"), service.listEligibleOwnerLeadAssignees().get(0).roles());
    }

    /** W11-3: the list and the check share one rule. */
    @Test
    void listAndCheckAgree() {
        AppUser sale = account("Sale", UserStatus.ACTIVE, RoleName.SALE);
        AppUser leader = account("Leader", UserStatus.PENDING, RoleName.SALE_LEADER);
        AppUser locked = account("Locked", UserStatus.LOCK, RoleName.SALE);
        AppUser terminated = account("Terminated", UserStatus.TERMINATED, RoleName.SALE_LEADER);
        AppUser deleted = account("Deleted", UserStatus.ACTIVE, RoleName.SALE);
        deleted.deactivate();
        AppUser roleless = account("Roleless", UserStatus.ACTIVE);
        AppUser reviewer = account("Reviewer", UserStatus.ACTIVE, RoleName.LISTING_REVIEWER);

        List<String> listed = service.listEligibleOwnerLeadAssignees().stream()
                .map(LeadAssigneeAccount::userId).toList();

        assertEquals(Set.of(sale.getId().getValue(), leader.getId().getValue()), Set.copyOf(listed));
        for (String id : listed) {
            assertTrue(service.isEligibleOwnerLeadAssignee(id), id);
        }
        for (AppUser notListed : List.of(locked, terminated, deleted, roleless, reviewer)) {
            assertFalse(listed.contains(notListed.getId().getValue()));
            assertFalse(service.isEligibleOwnerLeadAssignee(notListed.getId().getValue()), notListed.getFullName());
        }
    }

    /** Exactly 500 accounts fit the list: all of them come back and nothing is warned about. */
    @Test
    void list_exactly500Accounts_returnsAllAndDoesNotWarn() {
        for (int i = 0; i < 500; i++) {
            account(String.format("01ACCT%020d", 1000 + i), String.format("Nhan vien %03d", i), UserStatus.ACTIVE,
                    RoleName.SALE);
        }

        List<LeadAssigneeAccount> listed;
        try (WarningCapture warnings = new WarningCapture()) {
            listed = service.listEligibleOwnerLeadAssignees();

            assertEquals(List.of(), warnings.messages());
        }

        assertEquals(500, listed.size());
        assertEquals("Nhan vien 499", listed.get(499).fullName());
    }

    /** More than 500: the first 500 by name come back and one warning (a count, no names) tells the list was cut. */
    @Test
    void list_501Accounts_returnsFirst500AndWarnsOnce() {
        for (int i = 0; i < 501; i++) {
            account(String.format("01ACCT%020d", 1000 + i), String.format("Nhan vien %03d", i), UserStatus.ACTIVE,
                    RoleName.SALE);
        }

        List<LeadAssigneeAccount> listed;
        try (WarningCapture warnings = new WarningCapture()) {
            listed = service.listEligibleOwnerLeadAssignees();

            assertEquals(1, warnings.messages().size(), warnings.messages().toString());
            assertTrue(warnings.messages().get(0).contains("500"));
            assertFalse(warnings.messages().get(0).contains("Nhan vien"), "no name in the log");
        }

        assertEquals(500, listed.size());
        assertEquals("Nhan vien 000", listed.get(0).fullName());
        assertEquals("Nhan vien 499", listed.get(499).fullName());
    }

    /** The repository is asked for one more than the cap, so the service can tell "exactly 500" from "more". */
    @Test
    void list_asksTheRepositoryForOneMoreThanTheCap() {
        int[] asked = new int[1];
        LeadAssigneeAccountService recording = new LeadAssigneeAccountService(new InMemoryUserRepository() {
            @Override
            public List<StaffAccountRow> findSignInAbleByAnyRole(Set<String> roleNames, int limit) {
                asked[0] = limit;
                return List.of();
            }
        });

        recording.listEligibleOwnerLeadAssignees();

        assertEquals(501, asked[0]);
    }

    @Test
    void capsListAt500() {
        for (int i = 0; i < 501; i++) {
            account(String.format("01ACCT%020d", 1000 + i), String.format("Nhan vien %03d", i), UserStatus.ACTIVE,
                    RoleName.SALE);
        }
        // a fake that ignores the limit, like a repository returning more than asked
        LeadAssigneeAccountService unlimited = new LeadAssigneeAccountService(new InMemoryUserRepository() {
            @Override
            public List<StaffAccountRow> findSignInAbleByAnyRole(Set<String> roleNames, int limit) {
                return users.findSignInAbleByAnyRole(roleNames, Integer.MAX_VALUE);
            }
        });

        List<LeadAssigneeAccount> listed = unlimited.listEligibleOwnerLeadAssignees();

        assertEquals(500, listed.size());
        assertEquals("Nhan vien 000", listed.get(0).fullName());
        assertEquals("Nhan vien 499", listed.get(499).fullName());
    }

    /** Collects the WARN lines the service logs while the capture is open. */
    private static final class WarningCapture implements AutoCloseable {

        private final Logger logger = (Logger) LoggerFactory.getLogger(LeadAssigneeAccountService.class);
        private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

        WarningCapture() {
            appender.start();
            logger.addAppender(appender);
        }

        List<String> messages() {
            return appender.list.stream().filter(event -> event.getLevel() == Level.WARN)
                    .map(ILoggingEvent::getFormattedMessage).toList();
        }

        @Override
        public void close() {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void displayNamesEmptyInputMakesNoQuery() {
        assertEquals(Map.of(), service.findStaffDisplayNames(List.of()));
        assertEquals(Map.of(), service.findStaffDisplayNames(null));
        assertEquals(Map.of(), service.findStaffDisplayNames(Arrays.asList(null, " ", "")));
        assertEquals(0, users.findFullNamesByIdsCalls);
    }

    @Test
    void displayNamesSkipsUnknownIds() {
        AppUser known = account("Hoa", UserStatus.ACTIVE, RoleName.SALE);

        Map<String, String> names = service.findStaffDisplayNames(
                List.of(known.getId().getValue(), known.getId().getValue(), "01UNKNOWN0000000000000000"));

        assertEquals(Map.of(known.getId().getValue(), "Hoa"), names);
        assertEquals(1, users.findFullNamesByIdsCalls);
    }

    /** C-35: history keeps the name of staff who were locked, deleted or lost their role. */
    @Test
    void displayNames_includeLockedDeletedAndRolelessAccounts() {
        AppUser locked = account("Khoa", UserStatus.LOCK, RoleName.SALE);
        AppUser deleted = account("Xoa", UserStatus.ACTIVE, RoleName.SALE);
        deleted.deactivate();
        AppUser roleless = account("Trong", UserStatus.ACTIVE);

        Map<String, String> names = service.findStaffDisplayNames(
                List.of(locked.getId().getValue(), deleted.getId().getValue(), roleless.getId().getValue()));

        assertEquals(3, names.size());
        assertEquals("Xoa", names.get(deleted.getId().getValue()));
    }
}
