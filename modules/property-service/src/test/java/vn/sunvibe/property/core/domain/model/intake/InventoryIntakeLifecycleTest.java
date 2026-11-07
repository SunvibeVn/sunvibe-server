package vn.sunvibe.property.core.domain.model.intake;

import org.junit.jupiter.api.Test;
import vn.sunvibe.property.core.domain.exception.IntakeStateException;
import vn.sunvibe.property.core.domain.model.property.enums.AssetType;
import vn.sunvibe.property.core.domain.model.property.enums.RentalLayout;
import vn.sunvibe.property.core.domain.model.unit.UnitAvailabilityState;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/** The state table of property-create intake spec 4.2 on the aggregate, and what each command leaves behind. */
class InventoryIntakeLifecycleTest {

    private static final Instant NOW = Instant.parse("2026-11-06T02:00:00Z");
    private static final List<SelectedSpace> SELECTION = List.of(new SelectedSpace("01SPACE1", "P101", "Floor 1"));
    private static final IntakeTarget CREATE = new IntakeTarget(TargetMode.CREATE, null, null, List.of());
    private static final List<AvailabilityItem> AVAILABILITY = List.of(new AvailabilityItem("01SPACE1",
            AvailabilityChoice.VACANT, LocalDate.of(2026, 11, 6), "Called", null, "01SALE"));

    private static final Set<IntakeStatus> OPEN = EnumSet.of(IntakeStatus.DRAFT, IntakeStatus.AWAITING_OWNER,
            IntakeStatus.OWNER_CONFIRMED, IntakeStatus.CASE_OPEN);

    /** Command -> the statuses of spec 4.2 it runs in. */
    private static final Map<String, Set<IntakeStatus>> ALLOWED = new LinkedHashMap<>();
    private static final Map<String, Consumer<InventoryIntake>> COMMANDS = new LinkedHashMap<>();

    static {
        command("select", OPEN, intake -> intake.select(SELECTION, NOW));
        command("awaitOwner", EnumSet.of(IntakeStatus.DRAFT, IntakeStatus.AWAITING_OWNER, IntakeStatus.COMPLETED),
                intake -> intake.awaitOwner(NOW));
        command("confirmOwner", EnumSet.of(IntakeStatus.AWAITING_OWNER, IntakeStatus.COMPLETED),
                intake -> intake.confirmOwner(NOW));
        command("backToDraft", EnumSet.of(IntakeStatus.AWAITING_OWNER, IntakeStatus.OWNER_CONFIRMED,
                IntakeStatus.CASE_OPEN), intake -> intake.backToDraft(NOW));
        command("target", EnumSet.of(IntakeStatus.OWNER_CONFIRMED), intake -> intake.target(CREATE, NOW));
        command("availability", EnumSet.of(IntakeStatus.OWNER_CONFIRMED), intake -> intake.availability(AVAILABILITY, NOW));
        command("openCase", EnumSet.of(IntakeStatus.OWNER_CONFIRMED), intake -> intake.openCase(NOW));
        command("closeCase", EnumSet.of(IntakeStatus.CASE_OPEN), intake -> intake.closeCase(NOW));
        command("complete", EnumSet.of(IntakeStatus.OWNER_CONFIRMED), intake -> intake.complete("01PROP", "01PROOF",
                Map.of("property", 1L), "hash", "01SALE", NOW));
        command("abandon", OPEN, intake -> intake.abandon(NOW));
        command("restore", EnumSet.of(IntakeStatus.COMPLETED), intake -> intake.restore("01PROOF", NOW));
    }

    private static void command(String name, Set<IntakeStatus> allowed, Consumer<InventoryIntake> body) {
        ALLOWED.put(name, allowed);
        COMMANDS.put(name, body);
    }

    @Test
    void lifecycle_commandOutsideState_refused() {
        for (Map.Entry<String, Consumer<InventoryIntake>> command : COMMANDS.entrySet()) {
            for (IntakeStatus status : IntakeStatus.values()) {
                InventoryIntake intake = intakeIn(status);
                if (ALLOWED.get(command.getKey()).contains(status)) {
                    assertDoesNotThrow(() -> command.getValue().accept(intake), command.getKey() + " in " + status);
                } else {
                    assertThrows(IntakeStateException.class, () -> command.getValue().accept(intake),
                            command.getKey() + " in " + status);
                    assertEquals(status, intake.getStatus(), "a refused command moves nothing");
                }
            }
        }
    }

    @Test
    void create_startsDraftAtVersionZero() {
        InventoryIntake intake = draft();

        assertEquals(IntakeStatus.DRAFT, intake.getStatus());
        assertEquals(0L, intake.getVersion());
        assertEquals(SELECTION, intake.getSelection());
        assertEquals(ScopeHash.of(List.of("01SPACE1")), intake.scopeHash());
    }

    /** spec 4.2: a selection change voids what was built on the old scope. */
    @Test
    void select_clearsTargetAndAvailability_toDraft() {
        InventoryIntake intake = intakeIn(IntakeStatus.OWNER_CONFIRMED);
        intake.target(new IntakeTarget(TargetMode.LINK, "01PROP", null,
                List.of(new SpaceUnitMapping("01SPACE1", "01UNIT"))), NOW);
        intake.availability(AVAILABILITY, NOW);

        intake.select(List.of(new SelectedSpace("01SPACE2", "P102", "Floor 2")), NOW);

        assertEquals(IntakeStatus.DRAFT, intake.getStatus());
        assertNull(intake.getTarget());
        assertEquals(List.of(), intake.getAvailability());
        assertEquals(ScopeHash.of(List.of("01SPACE2")), intake.scopeHash());
    }

    @Test
    void backToDraft_clearsTargetAndAvailability() {
        InventoryIntake intake = intakeIn(IntakeStatus.OWNER_CONFIRMED);
        intake.target(CREATE, NOW);
        intake.availability(AVAILABILITY, NOW);

        intake.backToDraft(NOW);

        assertEquals(IntakeStatus.DRAFT, intake.getStatus());
        assertNull(intake.getTarget());
        assertEquals(List.of(), intake.getAvailability());
    }

    /** spec 4.2: COMPLETED stays; a request or confirmation on a result moves no status. */
    @Test
    void completed_ownerCommands_statusStays() {
        InventoryIntake intake = intakeIn(IntakeStatus.COMPLETED);

        intake.awaitOwner(NOW);
        intake.confirmOwner(NOW);

        assertEquals(IntakeStatus.COMPLETED, intake.getStatus());
    }

    /** A-13: every command that writes raises the version. */
    @Test
    void everyCommand_raisesTheVersion() {
        for (Map.Entry<String, Consumer<InventoryIntake>> command : COMMANDS.entrySet()) {
            IntakeStatus status = ALLOWED.get(command.getKey()).iterator().next();
            InventoryIntake intake = intakeIn(status);
            long before = intake.getVersion();

            command.getValue().accept(intake);

            assertEquals(before + 1, intake.getVersion(), command.getKey());
        }
    }

    @Test
    void complete_recordsResultProofAndHash() {
        InventoryIntake intake = intakeIn(IntakeStatus.OWNER_CONFIRMED);

        intake.complete("01PROP", "01PROOF", Map.of("property", 1L), "hash", "01SALE", NOW);

        assertEquals(IntakeStatus.COMPLETED, intake.getStatus());
        assertEquals("01PROP", intake.getResultPropertyId());
        assertEquals("01PROOF", intake.getProofId());
        assertEquals(Map.of("property", 1L), intake.getUsedVersions());
        assertEquals("hash", intake.getPreviewHash());
        assertEquals("01SALE", intake.getCompletedByUserId());
        assertEquals(NOW, intake.getCompletedAt());
    }

    @Test
    void restore_replacesTheProofInForce() {
        InventoryIntake intake = intakeIn(IntakeStatus.COMPLETED);

        intake.restore("01NEWPROOF", NOW);

        assertEquals("01NEWPROOF", intake.getProofId());
    }

    /** spec 4.9: each choice maps to its state; no choice stands for "not contacted" (E-14). */
    @Test
    void availabilityChoice_mapsToTheUnitState() {
        assertEquals(UnitAvailabilityState.AVAILABLE, AvailabilityChoice.VACANT.unitState());
        assertEquals(UnitAvailabilityState.OCCUPIED, AvailabilityChoice.OCCUPIED.unitState());
        assertEquals(UnitAvailabilityState.MAINTENANCE, AvailabilityChoice.UNDER_REPAIR.unitState());
        assertEquals(UnitAvailabilityState.BLOCKED, AvailabilityChoice.PAUSED.unitState());
        assertEquals(UnitAvailabilityState.BLOCKED, AvailabilityChoice.OWNER_UNSURE.unitState());
        assertEquals(UnitAvailabilityState.BLOCKED, AvailabilityChoice.SOURCE_CONFLICT.unitState());
        assertEquals(6, AvailabilityChoice.values().length);
    }

    /** spec 4.12: a selection holds places, so a row prints neither. */
    @Test
    void toString_holdsNoAddress() {
        InventoryIntake intake = draft();

        assertFalse(intake.toString().contains("1 bach dang"));
        assertFalse(SELECTION.getFirst().toString().contains("Floor"));
    }

    private static InventoryIntake draft() {
        return InventoryIntake.create("01INTAKE", 1, "01LEAD", 1, new IntakeSourceRef("01CHECKLIST", "a".repeat(64),
                RentalLayout.PARTS, AssetType.WHOLE_BUILDING, "20242", "1 bach dang", 16.07, 108.22), SELECTION, NOW);
    }

    private static InventoryIntake intakeIn(IntakeStatus status) {
        InventoryIntake intake = draft();
        switch (status) {
            case DRAFT -> {
            }
            case AWAITING_OWNER -> intake.awaitOwner(NOW);
            case OWNER_CONFIRMED -> {
                intake.awaitOwner(NOW);
                intake.confirmOwner(NOW);
            }
            case CASE_OPEN -> {
                intake.awaitOwner(NOW);
                intake.confirmOwner(NOW);
                intake.openCase(NOW);
            }
            case COMPLETED -> {
                intake.awaitOwner(NOW);
                intake.confirmOwner(NOW);
                intake.complete("01PROP", "01PROOF", Map.of(), "hash", "01SALE", NOW);
            }
            case ABANDONED -> intake.abandon(NOW);
        }
        return intake;
    }
}
