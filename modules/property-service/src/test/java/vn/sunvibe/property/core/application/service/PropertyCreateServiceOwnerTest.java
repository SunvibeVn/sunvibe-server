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
 * Owner resolution (wave W5, audit R3): a non-admin caller is the owner whatever the body says (W5-1);
 * an ADMIN must name an owner (W5-2) who is an existing, active user (W5-3).
 */
class PropertyCreateServiceOwnerTest {

    private static final String CALLER_ID = "01CALLER000000000000000000";
    private static final String OTHER_USER_ID = "01OTHERUSER000000000000000";
    private static final String CALLER_USERNAME = "0912345678";

    private final InMemoryPropertyDomainRepository properties = new InMemoryPropertyDomainRepository();
    private final RecordingUserDirectory users = new RecordingUserDirectory(Set.of(CALLER_ID, OTHER_USER_ID));
    private final PropertyCreateService service = new PropertyCreateService(
            properties, new EmptyRuleCategories(), new EmptyAmenityTypes(), users);

    @Test
    void execute_nonAdminNamingAnotherOwner_storesCallerAsOwner() {
        service.execute(command(OTHER_USER_ID, false));

        assertEquals(CALLER_ID, properties.saved().getFirst().getOwnerId());
        assertEquals(CALLER_USERNAME, properties.saved().getFirst().getCreatedBy());
    }

    @Test
    void execute_nonAdminWithoutOwnerId_storesCallerAsOwner() {
        service.execute(command(null, false));

        assertEquals(CALLER_ID, properties.saved().getFirst().getOwnerId());
    }

    @Test
    void execute_nonAdmin_neverLooksUpTheRequestedOwner() {
        service.execute(command("01NOSUCHUSER00000000000000", false));

        assertTrue(users.lookups.isEmpty(), "the directory is called only for ADMIN");
    }

    @Test
    void execute_adminNamingAnActiveUser_storesThatUserAsOwner() {
        service.execute(command(OTHER_USER_ID, true));

        assertEquals(OTHER_USER_ID, properties.saved().getFirst().getOwnerId());
        assertEquals(CALLER_USERNAME, properties.saved().getFirst().getCreatedBy());
        assertEquals(List.of(OTHER_USER_ID), users.lookups);
    }

    @Test
    void execute_adminNamingThemselves_storesAdminAsOwner() {
        service.execute(command(CALLER_ID, true));

        assertEquals(CALLER_ID, properties.saved().getFirst().getOwnerId());
    }

    @Test
    void execute_adminWithoutOwnerId_rejectsOnOwnerIdField() {
        PropertyValidationException e = assertThrows(PropertyValidationException.class,
                () -> service.execute(command(null, true)));

        assertEquals("ownerId", e.getFieldName());
        assertTrue(properties.saved().isEmpty());
    }

    @Test
    void execute_adminWithBlankOwnerId_rejectsOnOwnerIdField() {
        PropertyValidationException e = assertThrows(PropertyValidationException.class,
                () -> service.execute(command("  ", true)));

        assertEquals("ownerId", e.getFieldName());
        assertTrue(users.lookups.isEmpty());
        assertTrue(properties.saved().isEmpty());
    }

    @Test
    void execute_adminNamingUnknownOrInactiveUser_throwsOwnerNotFoundWithoutTheId() {
        String unknown = "01NOSUCHUSER00000000000000";

        PropertyOwnerNotFoundException e = assertThrows(PropertyOwnerNotFoundException.class,
                () -> service.execute(command(unknown, true)));

        assertEquals("Referenced owner was not found.", e.getMessage());
        assertEquals(List.of(unknown), users.lookups);
        assertTrue(properties.saved().isEmpty());
    }

    private static PropertyCreateCommand command(String requestedOwnerId, boolean actorIsAdmin) {
        return new PropertyCreateCommand(
                "DA-APT-001", "Title", "Description",
                "1 Bach Dang", "Thach Thang", "Hai Chau", null, null, null, null,
                PropertyType.STUDIO, 1, 1, 35.0,
                new BigDecimal("5000000"), new BigDecimal("5000000"), 6,
                requestedOwnerId, CALLER_ID, actorIsAdmin, CALLER_USERNAME,
                List.of(), List.of(), List.of());
    }

    /** Knows a fixed set of active user ids and records every lookup. */
    private static final class RecordingUserDirectory implements UserDirectoryPort {
        private final Set<String> activeUserIds;
        private final List<String> lookups = new ArrayList<>();

        private RecordingUserDirectory(Set<String> activeUserIds) {
            this.activeUserIds = activeUserIds;
        }

        @Override
        public boolean existsActiveUser(String userId) {
            lookups.add(userId);
            return activeUserIds.contains(userId);
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
