package vn.sunvibe.property.core.application.service;

import org.junit.jupiter.api.Test;
import vn.sunvibe.property.core.application.command.PropertyCreateCommand;
import vn.sunvibe.property.core.application.exception.PropertyValidationException;
import vn.sunvibe.property.core.application.shared.external.user.UserDirectoryPort;
import vn.sunvibe.property.core.domain.exception.PropertyOwnerNotFoundException;
import vn.sunvibe.property.core.domain.model.amenity.AmenityType;
import vn.sunvibe.property.core.domain.model.enums.PropertyType;
import vn.sunvibe.property.core.domain.model.rule.RuleCategory;
import vn.sunvibe.property.core.domain.repository.AmenityTypeDomainRepository;
import vn.sunvibe.property.core.domain.repository.RuleCategoryDomainRepository;
import vn.sunvibe.property.core.testsupport.InMemoryPropertyDomainRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Owner on property create (wave W6, auth-roles brief BR-6..BR-8): every caller names the owner, the caller never
 * becomes the owner, the owner must be eligible, and gets OWNER through the user directory.
 */
class PropertyCreateServiceOwnerTest {

    private static final String CALLER_ID = "01CALLER000000000000000000";
    private static final String OWNER_ID = "01OWNER0000000000000000000";
    private static final String CALLER_USERNAME = "0912345678";

    private final InMemoryPropertyDomainRepository properties = new InMemoryPropertyDomainRepository();
    private final RecordingUserDirectory users = new RecordingUserDirectory(Set.of(OWNER_ID), true);
    private final PropertyCreateService service = new PropertyCreateService(
            properties, new EmptyRuleCategories(), new EmptyAmenityTypes(), users);

    /** BR-6, BR-8 */
    @Test
    void execute_eligibleOwner_storesThatOwnerAndEnsuresTheOwnerRole() {
        service.execute(command(OWNER_ID));

        assertEquals(OWNER_ID, properties.saved().getFirst().getOwnerId());
        assertEquals(CALLER_USERNAME, properties.saved().getFirst().getCreatedBy());
        assertEquals(List.of("eligible?" + OWNER_ID, "ensureOwner:" + OWNER_ID + " by " + CALLER_ID), users.calls);
    }

    /** BR-7: an owner who became staff between the check and the grant -> 422 (the transaction rolls back). */
    @Test
    void execute_ownerIneligibleAtGrantTime_throwsOwnerNotFound() {
        PropertyCreateService racing = new PropertyCreateService(properties, new EmptyRuleCategories(),
                new EmptyAmenityTypes(), new RecordingUserDirectory(Set.of(OWNER_ID), false));

        assertThrows(PropertyOwnerNotFoundException.class, () -> racing.execute(command(OWNER_ID)));
    }

    /** BR-6, E-2 */
    @Test
    void execute_withoutOwnerId_rejectsOnOwnerIdField() {
        PropertyValidationException e = assertThrows(PropertyValidationException.class,
                () -> service.execute(command(null)));

        assertEquals("ownerId", e.getFieldName());
        assertTrue(users.calls.isEmpty());
        assertTrue(properties.saved().isEmpty());
    }

    /** BR-6, E-2 */
    @Test
    void execute_blankOwnerId_rejectsOnOwnerIdField() {
        PropertyValidationException e = assertThrows(PropertyValidationException.class,
                () -> service.execute(command("  ")));

        assertEquals("ownerId", e.getFieldName());
        assertTrue(users.calls.isEmpty());
    }

    /** BR-7, E-3: unknown, inactive or staff owner; nothing stored, no role granted, id not in the message. */
    @Test
    void execute_ineligibleOwner_throwsOwnerNotFoundAndGrantsNothing() {
        String ineligible = "01STAFFORUNKNOWN0000000000";

        PropertyOwnerNotFoundException e = assertThrows(PropertyOwnerNotFoundException.class,
                () -> service.execute(command(ineligible)));

        assertEquals("Referenced owner was not found.", e.getMessage());
        assertEquals(List.of("eligible?" + ineligible), users.calls);
        assertTrue(properties.saved().isEmpty());
    }

    /** BR-6: naming yourself is checked like anyone else (a staff caller is never eligible). */
    @Test
    void execute_callerNamesThemselves_isCheckedForEligibility() {
        assertThrows(PropertyOwnerNotFoundException.class, () -> service.execute(command(CALLER_ID)));
        assertTrue(properties.saved().isEmpty());
    }

    private static PropertyCreateCommand command(String ownerId) {
        return new PropertyCreateCommand(
                "DA-APT-001", "Title", "Description",
                "1 Bach Dang", "Thach Thang", "Hai Chau", null, null, null, null,
                PropertyType.STUDIO, 1, 1, 35.0,
                new BigDecimal("5000000"), new BigDecimal("5000000"), 6,
                ownerId, CALLER_ID, CALLER_USERNAME,
                List.of(), List.of(), List.of());
    }

    /** Knows a fixed set of eligible owners and records every call. */
    private static final class RecordingUserDirectory implements UserDirectoryPort {
        private final Set<String> eligibleOwners;
        private final boolean stillEligibleAtGrant;
        private final List<String> calls = new ArrayList<>();

        private RecordingUserDirectory(Set<String> eligibleOwners, boolean stillEligibleAtGrant) {
            this.eligibleOwners = eligibleOwners;
            this.stillEligibleAtGrant = stillEligibleAtGrant;
        }

        @Override
        public boolean isEligibleOwner(String userId) {
            calls.add("eligible?" + userId);
            return eligibleOwners.contains(userId);
        }

        @Override
        public boolean ensureOwnerRole(String ownerUserId, String actorUserId) {
            calls.add("ensureOwner:" + ownerUserId + " by " + actorUserId);
            return stillEligibleAtGrant;
        }
    }

    private static final class EmptyRuleCategories implements RuleCategoryDomainRepository {
        @Override
        public Optional<RuleCategory> findById(Integer id) {
            return Optional.empty();
        }

        @Override
        public List<RuleCategory> findAll() {
            return List.of();
        }
    }

    private static final class EmptyAmenityTypes implements AmenityTypeDomainRepository {
        @Override
        public Optional<AmenityType> findById(Integer id) {
            return Optional.empty();
        }

        @Override
        public List<AmenityType> findAll() {
            return List.of();
        }
    }
}
