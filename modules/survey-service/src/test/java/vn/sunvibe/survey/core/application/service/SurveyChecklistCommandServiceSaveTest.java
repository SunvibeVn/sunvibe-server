package vn.sunvibe.survey.core.application.service;

import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import vn.sunvibe.survey.core.application.command.BasisInput;
import vn.sunvibe.survey.core.application.command.CorrectionInput;
import vn.sunvibe.survey.core.application.command.SaveChecklistGroupCommand;
import vn.sunvibe.survey.core.application.command.TakeOverChecklistCommand;
import vn.sunvibe.survey.core.application.exception.ChecklistNotFoundException;
import vn.sunvibe.survey.core.application.exception.ChecklistReadOnlyException;
import vn.sunvibe.survey.core.application.exception.ChecklistTakeoverRequiredException;
import vn.sunvibe.survey.core.application.exception.SurveyConcurrentModificationException;
import vn.sunvibe.survey.core.application.exception.SurveyorIneligibleException;
import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistActivity;
import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistActivityType;
import vn.sunvibe.survey.core.domain.exception.ChecklistValidationException;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyIntent;
import vn.sunvibe.survey.core.domain.model.checklist.AmenitiesContent;
import vn.sunvibe.survey.core.domain.model.checklist.AmenityCode;
import vn.sunvibe.survey.core.domain.model.checklist.AmenityItem;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSave;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSource;
import vn.sunvibe.survey.core.domain.model.checklist.DimensionsContent;
import vn.sunvibe.survey.core.domain.model.checklist.DimensionsSpace;
import vn.sunvibe.survey.core.domain.model.checklist.GroupContent;
import vn.sunvibe.survey.core.domain.model.checklist.IdentificationContent;
import vn.sunvibe.survey.core.domain.model.checklist.ObservedLocation;
import vn.sunvibe.survey.core.domain.model.checklist.ObservedPlaceNames;
import vn.sunvibe.survey.core.domain.model.checklist.OtherItem;
import vn.sunvibe.survey.core.domain.model.checklist.SourcedField;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.checklist.Violation;
import vn.sunvibe.survey.core.domain.model.checklist.YesNo;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupDocument;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupState;
import vn.sunvibe.survey.core.domain.model.checklist.unit.Tombstone;
import vn.sunvibe.survey.core.domain.model.checklist.unit.UnitKey;
import vn.sunvibe.survey.core.domain.model.checklist.unit.UnitMeta;
import vn.sunvibe.survey.core.domain.model.log.AccessResourceType;
import vn.sunvibe.survey.core.domain.model.security.SecurityOutcome;
import vn.sunvibe.survey.core.domain.policy.checklist.CorrectedUnit;
import vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.SPACE_1;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.SPACE_2;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.dimensions;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.dimensionsSpace;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.identificationParts;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.identificationWhole;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.observedSpace;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.LEAD;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.LEADER;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.SALE_A;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.SALE_B;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.all;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.arrivalDate;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.own;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.today;

/**
 * A group save (survey-checklist spec 5.5 steps 1..10, A-12, A-17, PD-3..PD-5): the check order, the locks taken
 * before any group row is read, the no-op, the cascade of a space removal, the correction and the writes.
 */
class SurveyChecklistCommandServiceSaveTest {

    private static final AmenityItem AIR_CONDITIONER = new AmenityItem(AmenityCode.AIR_CONDITIONER, YesNo.YES,
            ChecklistSource.OBSERVED_ON_SITE, null, null, null);
    private static final AmenityItem ELEVATOR = new AmenityItem(AmenityCode.ELEVATOR, YesNo.YES,
            ChecklistSource.OBSERVED_ON_SITE, null, null, null);
    private static final OtherItem ITEM_Z = new OtherItem("01JTEM000000000000000000Z1", "Rèm cửa", null, YesNo.YES,
            ChecklistSource.OBSERVED_ON_SITE, null, null, null);
    private static final UnitKey SHARED_AC = UnitKey.sharedItem(AmenityCode.AIR_CONDITIONER);

    private final ChecklistServiceHarness h = new ChecklistServiceHarness();
    private SurveyChecklist checklist;

    // ==================== order ====================

    @Test
    void save_invalidBodyForAnInvisibleLead_is400() {
        start(SALE_A);
        DimensionsContent bad = new DimensionsContent(List.of(new DimensionsSpace("bad", SourcedField.unknown(),
                SourcedField.unknown(), SourcedField.unknown(), SourcedField.unknown(), null)));

        assertPaths(() -> save(SALE_B, ChecklistGroup.DIMENSIONS, 0L, bad, null), "content.spaces.0.spaceId");
    }

    /** spec 5.5 step 1: the catalog check of the address runs before any lookup. */
    @Test
    void save_badWardOnHiddenLead_answers400() {
        start(SALE_A);
        IdentificationContent g1 = withAddress(new ObservedLocation("12 Bạch Đằng", "48", "99999", null, null));

        assertPaths(() -> save(SALE_B, ChecklistGroup.IDENTIFICATION, 0L, g1, null),
                "content.observedAddress.value.wardCode");
        assertEquals(List.of(), h.securityEvents.all());
    }

    /** spec 5.5: rules that need stored data never answer before the 404. */
    @Test
    void save_unknownSpaceForAnInvisibleLead_isNotFound() {
        start(SALE_A);

        assertThrows(ChecklistNotFoundException.class, () -> save(SALE_B, ChecklistGroup.DIMENSIONS, 0L,
                dimensions(SPACE_2), null));
        assertEquals(SecurityOutcome.NOT_VISIBLE, h.securityEvents.all().get(0).outcome());
    }

    @Test
    void save_nonAssigneeOnAReadOnlyDraft_is403NotReadOnly() {
        start(SALE_A);
        h.leads.withLead(LEAD, "SURVEY_SCHEDULED", "LONG_TERM_RENT", 2, SALE_A, 5L);

        assertThrows(AccessDeniedException.class, () -> save(LEADER, ChecklistGroup.DIMENSIONS, 0L,
                dimensions(SPACE_1), null, all(LEADER)));
        assertEquals(SecurityOutcome.NOT_ASSIGNEE, h.securityEvents.all().get(0).outcome());
    }

    @Test
    void save_readOnlyBefore422_422BeforeTakeoverRequired() {
        start(SALE_A);
        h.lead(LEAD, "ON_HOLD", SALE_A);
        h.directory.makeIneligible(SALE_A);
        assertThrows(ChecklistReadOnlyException.class, () -> save(SALE_A, ChecklistGroup.DIMENSIONS, 0L,
                dimensions(SPACE_1), null));

        h.lead(LEAD, "SURVEY_SCHEDULED", SALE_B);
        h.directory.makeIneligible(SALE_B);
        assertThrows(SurveyorIneligibleException.class, () -> save(SALE_B, ChecklistGroup.DIMENSIONS, 0L,
                dimensions(SPACE_1), null));
    }

    /** spec 5.5 step 6 before step 7: a non-holder sending the stored content is told to take over. */
    @Test
    void save_nonHolderWithTheStoredContent_isTakeoverRequired() {
        start(SALE_A);
        GroupContent stored = h.groups.get(checklist.getId(), ChecklistGroup.DIMENSIONS).document().content();
        h.lead(LEAD, "SURVEY_SCHEDULED", SALE_B);

        assertThrows(ChecklistTakeoverRequiredException.class, () -> save(SALE_B, ChecklistGroup.DIMENSIONS, 0L,
                stored, null));
    }

    /** spec A-17: equal content with a stale version answers the stored state and writes only the access log. */
    @Test
    void save_equalContentWithAStaleVersion_isANoOp() {
        start(SALE_A);
        save(SALE_A, ChecklistGroup.DIMENSIONS, 0L, dimensions(SPACE_1), null);
        int activities = h.leads.checklistActivities().size();
        int logs = h.accessLogs.all().size();
        int rows = h.saves.all().size();

        save(SALE_A, ChecklistGroup.DIMENSIONS, 0L, dimensions(SPACE_1), null);

        assertEquals(1L, h.groups.get(checklist.getId(), ChecklistGroup.DIMENSIONS).version());
        assertEquals(rows, h.saves.all().size());
        assertEquals(activities, h.leads.checklistActivities().size());
        assertEquals(logs + 1, h.accessLogs.all().size());
    }

    @Test
    void save_staleVersion_beforeAStep9Violation() {
        start(SALE_A);
        save(SALE_A, ChecklistGroup.DIMENSIONS, 0L, dimensions(SPACE_1), null);

        assertEquals("expectedVersion", assertThrows(SurveyConcurrentModificationException.class,
                () -> save(SALE_A, ChecklistGroup.DIMENSIONS, 0L, dimensions(SPACE_1, SPACE_2), null)).getField());
        assertPaths(() -> save(SALE_A, ChecklistGroup.DIMENSIONS, 1L, dimensions(SPACE_1, SPACE_2), null),
                "content.spaces.1.spaceId");
    }

    /** spec 5.5 step 9 before step 10: a stored-data violation answers before a missing correction. */
    @Test
    void save_step9ViolationBeforeAMissingCorrection() {
        startAndHandOverDimensions();
        DimensionsContent changedAndUnknownSpace = new DimensionsContent(List.of(
                withArea(SourcedField.of(new BigDecimal("50.0"), ChecklistSource.MEASURED)), dimensionsSpace(SPACE_2)));

        assertPaths(() -> save(SALE_B, ChecklistGroup.DIMENSIONS, 1L, changedAndUnknownSpace, null),
                "content.spaces.1.spaceId");
        assertPaths(() -> save(SALE_B, ChecklistGroup.DIMENSIONS, 1L, dims(withArea(SourcedField.of(
                new BigDecimal("50.0"), ChecklistSource.MEASURED))), null), "correction");
        assertEquals(2, h.saves.all().size());
    }

    // ==================== cascade (spec A-12) ====================

    @Test
    void save_removalWithAStaleOrMissingOtherGroupVersion_is409OnThatKeyAndWritesNothing() {
        startParts();
        save(SALE_A, ChecklistGroup.DIMENSIONS, 0L, dimensions(SPACE_1, SPACE_2), null);
        IdentificationContent one = identificationParts(observedSpace(SPACE_1, "P101"));

        assertEquals("otherGroupVersions.dimensions", assertThrows(SurveyConcurrentModificationException.class,
                () -> h.commands.save(own(SALE_A), new SaveChecklistGroupCommand(id(), ChecklistGroup.IDENTIFICATION,
                        1L, Map.of(ChecklistGroup.DIMENSIONS, 0L), null, one))).getField());
        assertEquals("otherGroupVersions.dimensions", assertThrows(SurveyConcurrentModificationException.class,
                () -> h.commands.save(own(SALE_A), new SaveChecklistGroupCommand(id(), ChecklistGroup.IDENTIFICATION,
                        1L, Map.of(), null, one))).getField());
        assertEquals(2, h.saves.all().size());
    }

    @Test
    void save_removalOfAsSpaceByB_writesEveryChangedGroupWithTheCorrectionOnTheG1Row() {
        startParts();
        save(SALE_A, ChecklistGroup.DIMENSIONS, 0L, dimensions(SPACE_1, SPACE_2), null);
        takeOverBy(SALE_B);
        IdentificationContent one = identificationParts(observedSpace(SPACE_1, "P101"));
        SaveChecklistGroupCommand removal = new SaveChecklistGroupCommand(id(), ChecklistGroup.IDENTIFICATION, 1L,
                Map.of(ChecklistGroup.DIMENSIONS, 1L), null, one);

        assertPaths(() -> h.commands.save(own(SALE_B), removal), "correction");

        h.commands.save(own(SALE_B), new SaveChecklistGroupCommand(id(), ChecklistGroup.IDENTIFICATION, 1L,
                Map.of(ChecklistGroup.DIMENSIONS, 1L), correction(), one));

        List<ChecklistSave> rows = h.saves.all().subList(2, 4);
        assertEquals(List.of(ChecklistGroup.IDENTIFICATION, ChecklistGroup.DIMENSIONS),
                rows.stream().map(ChecklistSave::group).toList());
        assertTrue(rows.get(0).correctionOrNull() != null);
        assertNull(rows.get(1).correctionOrNull());
        List<CorrectedUnit> units = rows.get(0).correctionOrNull().units();
        assertTrue(units.contains(new CorrectedUnit(UnitKey.space(SPACE_2).value(), "P102")), units.toString());
        assertTrue(units.contains(new CorrectedUnit(UnitKey.spaceField(ChecklistGroup.DIMENSIONS, SPACE_2, "area")
                .value(), "P102")), units.toString());
        assertEquals(1, ((DimensionsContent) h.groups.get(checklist.getId(), ChecklistGroup.DIMENSIONS).document()
                .content()).spaces().size());
        assertEquals(2L, h.groups.get(checklist.getId(), ChecklistGroup.DIMENSIONS).version());
        assertEquals(2, h.leads.checklistActivities().stream()
                .filter(activity -> "IDENTIFICATION".equals(activity.detailOrNull())).count(), "A's first G1 save, B's removal");
    }

    // ==================== locks (spec 5.5, PD-3) ====================

    @Test
    void save_g3_takesG1ShareLockThenOwnRow_andReadsNoGroupRowBeforeItsLock() {
        start(SALE_A);
        h.journal.clear();

        save(SALE_A, ChecklistGroup.DIMENSIONS, 0L, dimensions(SPACE_1), null);

        List<String> groupCalls = h.journal.stream().filter(entry -> entry.startsWith("group:")).toList();
        assertEquals(List.of("group:share:IDENTIFICATION", "group:update-lock:DIMENSIONS", "group:write:DIMENSIONS"),
                groupCalls.subList(0, 3));
        assertTrue(h.journal.indexOf("lead:share") < h.journal.indexOf("group:share:IDENTIFICATION"));
        assertTrue(h.journal.indexOf("lead:activity:CHECKLIST_SAVED") > h.journal.indexOf("save-log:DIMENSIONS"));
    }

    @Test
    void save_g1Removal_readsOtherGroupsOnlyAfterG1Lock() {
        startParts();
        save(SALE_A, ChecklistGroup.DIMENSIONS, 0L, dimensions(SPACE_1, SPACE_2), null);
        h.journal.clear();

        h.commands.save(own(SALE_A), new SaveChecklistGroupCommand(id(), ChecklistGroup.IDENTIFICATION, 1L,
                Map.of(ChecklistGroup.DIMENSIONS, 1L), null, identificationParts(observedSpace(SPACE_1, "P101"))));

        List<String> groupCalls = h.journal.stream().filter(entry -> entry.startsWith("group:")).toList();
        assertEquals(List.of("group:update-lock:IDENTIFICATION", "group:others", "group:update-lock:DIMENSIONS",
                "group:write:IDENTIFICATION", "group:write:DIMENSIONS"), groupCalls.subList(0, 5));
    }

    // ==================== writes (W16-5) ====================

    @Test
    void save_g3_storesTheValuesOneSaveLogRowAndOneHistoryEntry() {
        start(SALE_A);
        SurveyAppointment visit = h.appointments.all().get(0);

        save(SALE_A, ChecklistGroup.DIMENSIONS, 0L, dimensions(SPACE_1), null);

        GroupState state = h.groups.get(checklist.getId(), ChecklistGroup.DIMENSIONS);
        assertEquals(1L, state.version());
        assertEquals(SALE_A, state.updatedByUserId());
        UnitMeta area = state.document().meta().get(UnitKey.spaceField(ChecklistGroup.DIMENSIONS, SPACE_1, "area"));
        assertEquals(SALE_A, area.observedByUserId());
        assertEquals(arrivalDate(visit), area.observedOn());
        assertEquals(SALE_A, area.enteredByUserId());
        assertEquals(2, h.saves.all().size());
        assertEquals(ChecklistGroup.DIMENSIONS, last(h.saves.all()).group());
        assertEquals(1L, last(h.saves.all()).groupVersion());
        ChecklistActivity activity = last(h.leads.checklistActivities());
        assertEquals(ChecklistActivityType.CHECKLIST_SAVED, activity.type());
        assertEquals("DIMENSIONS", activity.detailOrNull());
        assertEquals(0L, h.groups.get(checklist.getId(), ChecklistGroup.PARTS).version());
        assertEquals(List.of(), h.leads.moves());
        assertEquals(0L, h.checklists.all().get(0).getVersion());
        assertEquals(AccessResourceType.CHECKLIST, h.accessLogs.all().get(h.accessLogs.all().size() - 1)
                .resourceTypeOrNull());
    }

    @Test
    void save_identification_keepsThePlaceNamesOfTheCatalog() {
        start(SALE_A);

        save(SALE_A, ChecklistGroup.IDENTIFICATION, 1L, withAddress(new ObservedLocation("12 Bạch Đằng", "48",
                "20194", new BigDecimal("16.0544071"), new BigDecimal("108.2021669"))), null);

        GroupState g1 = h.groups.get(checklist.getId(), ChecklistGroup.IDENTIFICATION);
        assertEquals(new ObservedPlaceNames("Da Nang", "Hai Chau"), g1.document().placeNamesOrNull());
        ObservedLocation stored = ((IdentificationContent) g1.document().content()).observedAddress().value();
        assertEquals(new BigDecimal("16.054407"), stored.latitude());
    }

    @Test
    void save_correctionNeededAndGiven_recordsItAndPointsTheUnitsToTheRow() {
        startAndHandOverDimensions();

        h.commands.save(own(SALE_B), new SaveChecklistGroupCommand(id(), ChecklistGroup.DIMENSIONS, 1L, Map.of(),
                correction(), dims(withArea(SourcedField.of(new BigDecimal("50.0"), ChecklistSource.MEASURED)))));

        ChecklistSave row = last(h.saves.all());
        UnitKey area = UnitKey.spaceField(ChecklistGroup.DIMENSIONS, SPACE_1, "area");
        assertEquals(List.of(new CorrectedUnit(area.value(), "A1")), row.correctionOrNull().units());
        UnitMeta meta = h.groups.get(checklist.getId(), ChecklistGroup.DIMENSIONS).document().meta().get(area);
        assertEquals(row.id(), meta.correctionSaveId());
        assertEquals(SALE_A, meta.observedByUserId());
        assertEquals(SALE_B, meta.enteredByUserId());
    }

    /** spec 5.2: a correction sent when none is needed is kept against every unit the save changed. */
    @Test
    void save_voluntaryCorrection_isRecordedAgainstEveryChangedUnit() {
        start(SALE_A);

        h.commands.save(own(SALE_A), new SaveChecklistGroupCommand(id(), ChecklistGroup.DIMENSIONS, 0L, Map.of(),
                correction(), dimensions(SPACE_1)));

        List<CorrectedUnit> units = last(h.saves.all()).correctionOrNull().units();
        assertEquals(4, units.size());
        UnitMeta meta = h.groups.get(checklist.getId(), ChecklistGroup.DIMENSIONS).document().meta()
                .get(UnitKey.spaceField(ChecklistGroup.DIMENSIONS, SPACE_1, "bedrooms"));
        assertEquals(last(h.saves.all()).id(), meta.correctionSaveId());
    }

    @Test
    void save_correctionBasisBeforeTheVisit_is400() {
        start(SALE_A);
        SurveyAppointment visit = h.appointments.all().get(0);

        assertPaths(() -> h.commands.save(own(SALE_A), new SaveChecklistGroupCommand(id(), ChecklistGroup.DIMENSIONS,
                0L, Map.of(), new CorrectionInput("Đo lại", new BasisInput(arrivalDate(visit).minusDays(1),
                "Đo lại bằng thước")), dimensions(SPACE_1))), "correction.basis.confirmedOn");
    }

    /** W16-13: a group-row conflict raised at flush answers on expectedVersion. */
    @Test
    void save_flushConflict_answersExpectedVersion() {
        start(SALE_A);
        h.groups.failNextUpdate = new ObjectOptimisticLockingFailureException(GroupState.class, "x");

        assertEquals("expectedVersion", assertThrows(SurveyConcurrentModificationException.class,
                () -> save(SALE_A, ChecklistGroup.DIMENSIONS, 0L, dimensions(SPACE_1), null)).getField());
    }

    // ==================== removal and return (spec A-29, BR-28, BR-29) ====================

    /** BR-29: B's removal stamps the tombstone with B, the save time and the correcting save; A stays the observer. */
    @Test
    void save_removalOfOthersItem_stampsTheTombstone() {
        startAndHandOverAmenities();

        h.commands.save(own(SALE_B), new SaveChecklistGroupCommand(id(), ChecklistGroup.AMENITIES, 1L, Map.of(),
                correction(), g6(List.of(ELEVATOR), List.of())));

        GroupState state = h.groups.get(checklist.getId(), ChecklistGroup.AMENITIES);
        assertEquals(new Tombstone(SALE_B, state.updatedAt(), SALE_A, arrivalDate(h.appointments.all().get(0)), null,
                ChecklistSource.OBSERVED_ON_SITE, last(h.saves.all()).id(), null),
                state.document().tombstone(SHARED_AC));
        assertFalse(state.document().meta().containsKey(SHARED_AC));
    }

    /** W16-46: B puts back what B removed: without a correction 400 and nothing written; with one, saved. */
    @Test
    void save_returnOfARemovedItem_needsACorrectionAndPointsItsMetaToTheSave() {
        startAndHandOverAmenities();
        h.commands.save(own(SALE_B), new SaveChecklistGroupCommand(id(), ChecklistGroup.AMENITIES, 1L, Map.of(),
                correction(), g6(List.of(ELEVATOR), List.of())));
        int rows = h.saves.all().size();
        int activities = h.leads.checklistActivities().size();

        assertPaths(() -> save(SALE_B, ChecklistGroup.AMENITIES, 2L, g6(List.of(AIR_CONDITIONER, ELEVATOR),
                List.of()), null), "correction");
        assertEquals(2L, h.groups.get(checklist.getId(), ChecklistGroup.AMENITIES).version());
        assertEquals(rows, h.saves.all().size());
        assertEquals(activities, h.leads.checklistActivities().size());

        h.commands.save(own(SALE_B), new SaveChecklistGroupCommand(id(), ChecklistGroup.AMENITIES, 2L, Map.of(),
                correction(), g6(List.of(AIR_CONDITIONER, ELEVATOR), List.of())));

        GroupDocument document = h.groups.get(checklist.getId(), ChecklistGroup.AMENITIES).document();
        UnitMeta meta = document.meta().get(SHARED_AC);
        assertEquals(last(h.saves.all()).id(), meta.correctionSaveId());
        assertEquals(SALE_A, meta.observedByUserId());
        assertEquals(SALE_B, meta.enteredByUserId());
        assertEquals(List.of(new CorrectedUnit(SHARED_AC.value(), null)), last(h.saves.all()).correctionOrNull()
                .units());
        assertNull(document.tombstone(SHARED_AC));
    }

    /** BR-29: a retried removal and a later save that leaves the item absent stamp nothing new. */
    @Test
    void save_retriedRemovalAndUnrelatedSave_leaveTheTombstone() {
        startAndHandOverAmenities();
        SaveChecklistGroupCommand removal = new SaveChecklistGroupCommand(id(), ChecklistGroup.AMENITIES, 1L, Map.of(),
                correction(), g6(List.of(ELEVATOR), List.of()));
        h.commands.save(own(SALE_B), removal);
        Tombstone stamped = h.groups.get(checklist.getId(), ChecklistGroup.AMENITIES).document().tombstone(SHARED_AC);
        assertEquals(SALE_B, stamped.enteredByUserId());

        h.commands.save(own(SALE_B), removal);
        assertEquals(2L, h.groups.get(checklist.getId(), ChecklistGroup.AMENITIES).version());
        save(SALE_B, ChecklistGroup.AMENITIES, 2L, g6(List.of(ELEVATOR), List.of(ITEM_Z)), null);

        GroupState state = h.groups.get(checklist.getId(), ChecklistGroup.AMENITIES);
        assertEquals(3L, state.version());
        assertEquals(stamped, state.document().tombstone(SHARED_AC));
    }

    /** spec A-12, A-29: a G1 removal of a space stamps the tombstone of its other item with the G1 saver. */
    @Test
    void save_cascadedSpaceRemoval_stampsOtherItemTombstoneWithTheG1Saver() {
        startParts();
        OtherItem y = new OtherItem(ITEM_Z.itemId(), "Kệ giày", SPACE_2, YesNo.YES, ChecklistSource.OBSERVED_ON_SITE,
                null, null, null);
        save(SALE_A, ChecklistGroup.AMENITIES, 0L, g6(List.of(), List.of(y)), null);
        takeOverBy(SALE_B);

        h.commands.save(own(SALE_B), new SaveChecklistGroupCommand(id(), ChecklistGroup.IDENTIFICATION, 1L,
                Map.of(ChecklistGroup.AMENITIES, 1L), correction(), identificationParts(observedSpace(SPACE_1,
                "P101"))));

        ChecklistSave g1Row = h.saves.all().stream().filter(row -> row.group() == ChecklistGroup.IDENTIFICATION)
                .reduce((first, second) -> second).orElseThrow();
        GroupState g6 = h.groups.get(checklist.getId(), ChecklistGroup.AMENITIES);
        Tombstone tombstone = g6.document().tombstone(UnitKey.otherItem(y.itemId()));
        assertEquals(2L, g6.version());
        assertEquals(SALE_B, tombstone.enteredByUserId());
        assertEquals(SALE_A, tombstone.observedByUserId());
        assertEquals(g1Row.id(), tombstone.correctionSaveId());
    }

    // ==================== helpers ====================

    /** A draft of the whole property with G1 saved by the performer (save-log row 0). */
    private void start(String performer) {
        checklist = h.started(LEAD, performer).view().checklist();
        save(performer, ChecklistGroup.IDENTIFICATION, 0L, identificationWhole(), null);
    }

    private void startParts() {
        checklist = h.started(LEAD, SALE_A).view().checklist();
        save(SALE_A, ChecklistGroup.IDENTIFICATION, 0L, identificationParts(observedSpace(SPACE_1, "P101"),
                observedSpace(SPACE_2, "P102")), null);
    }

    /** A saved G3 and G1 by A; then the lead goes to B, who takes the draft over. */
    private void startAndHandOverDimensions() {
        start(SALE_A);
        save(SALE_A, ChecklistGroup.DIMENSIONS, 0L, dimensions(SPACE_1), null);
        takeOverBy(SALE_B);
    }

    /** A saved G6 (the air conditioner and the elevator seen on site) and G1 by A; then B takes the draft over. */
    private void startAndHandOverAmenities() {
        start(SALE_A);
        save(SALE_A, ChecklistGroup.AMENITIES, 0L, g6(List.of(AIR_CONDITIONER, ELEVATOR), List.of()), null);
        takeOverBy(SALE_B);
    }

    private static AmenitiesContent g6(List<AmenityItem> shared, List<OtherItem> others) {
        return new AmenitiesContent(shared, List.of(), others);
    }

    private void takeOverBy(String user) {
        h.lead(LEAD, "SURVEY_SCHEDULED", user);
        SurveyAppointment visit = h.appointments.all().get(0);
        h.commands.takeOver(own(user), new TakeOverChecklistCommand(id(), h.checklists.all().get(0).getVersion(),
                "Được giao lại", new BasisInput(arrivalDate(visit), "Quản lý giao")));
    }

    private void save(String caller, ChecklistGroup group, long version, GroupContent content,
                      CorrectionInput correction) {
        save(caller, group, version, content, correction, own(caller));
    }

    private void save(String caller, ChecklistGroup group, long version, GroupContent content,
                      CorrectionInput correction,
                      vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer viewer) {
        h.commands.save(viewer, new SaveChecklistGroupCommand(id(), group, version, Map.of(), correction, content));
    }

    private static <T> T last(List<T> list) {
        return list.get(list.size() - 1);
    }

    private String id() {
        return checklist.getId().getValue();
    }

    private static CorrectionInput correction() {
        return new CorrectionInput("Đo lại diện tích", new BasisInput(today(), "Đo lại bằng thước laser"));
    }

    private static IdentificationContent withAddress(ObservedLocation address) {
        IdentificationContent g = identificationWhole();
        return new IdentificationContent(SourcedField.of(address, ChecklistSource.OBSERVED_ON_SITE), null,
                g.propertyType(), g.fit(), g.rentalScope(), g.spaces(), null, null);
    }

    private static DimensionsContent dims(DimensionsSpace space) {
        return new DimensionsContent(List.of(space));
    }

    private static DimensionsSpace withArea(SourcedField<BigDecimal> area) {
        DimensionsSpace s = dimensionsSpace(SPACE_1);
        return new DimensionsSpace(SPACE_1, area, s.bedrooms(), s.privateBathrooms(), s.sharedBathroomUse(), null);
    }

    private static void assertPaths(Supplier<?> call, String... paths) {
        ChecklistValidationException refused = assertThrows(ChecklistValidationException.class, call::get);
        assertEquals(List.of(paths), refused.violations().stream().map(Violation::path).toList());
    }

    private static void assertPaths(Runnable call, String... paths) {
        assertPaths(() -> {
            call.run();
            return null;
        }, paths);
    }
}
