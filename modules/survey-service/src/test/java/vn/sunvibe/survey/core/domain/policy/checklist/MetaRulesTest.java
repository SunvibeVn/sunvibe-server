package vn.sunvibe.survey.core.domain.policy.checklist;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.domain.model.checklist.AmenitiesContent;
import vn.sunvibe.survey.core.domain.model.checklist.AmenityCode;
import vn.sunvibe.survey.core.domain.model.checklist.AmenityItem;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSource;
import vn.sunvibe.survey.core.domain.model.checklist.ConditionContent;
import vn.sunvibe.survey.core.domain.model.checklist.ConditionSpace;
import vn.sunvibe.survey.core.domain.model.checklist.DimensionsContent;
import vn.sunvibe.survey.core.domain.model.checklist.DimensionsSpace;
import vn.sunvibe.survey.core.domain.model.checklist.Field;
import vn.sunvibe.survey.core.domain.model.checklist.GroupContent;
import vn.sunvibe.survey.core.domain.model.checklist.HazardSeverity;
import vn.sunvibe.survey.core.domain.model.checklist.HazardStatus;
import vn.sunvibe.survey.core.domain.model.checklist.IdentificationContent;
import vn.sunvibe.survey.core.domain.model.checklist.Occupancy;
import vn.sunvibe.survey.core.domain.model.checklist.SourcedField;
import vn.sunvibe.survey.core.domain.model.checklist.Hazard;
import vn.sunvibe.survey.core.domain.model.checklist.YesNo;
import vn.sunvibe.survey.core.domain.model.checklist.unit.ChangeKind;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupDocument;
import vn.sunvibe.survey.core.domain.model.checklist.unit.Tombstone;
import vn.sunvibe.survey.core.domain.model.checklist.unit.UnitChange;
import vn.sunvibe.survey.core.domain.model.checklist.unit.UnitDiff;
import vn.sunvibe.survey.core.domain.model.checklist.unit.UnitKey;
import vn.sunvibe.survey.core.domain.model.checklist.unit.UnitMeta;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.SPACE_1;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.conditionSpace;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.dimensionsSpace;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.identificationParts;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.identificationWhole;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.observedSpace;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.openHazard;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.safety;

/**
 * Who observed or heard each changed unit, when, and who typed it (survey-checklist spec 4.5, A-18, A-19, A-26;
 * W16-35 at domain level).
 */
class MetaRulesTest {

    private static final String PERFORMER = "01JPERF0RMER00000000000001";
    private static final String TYPIST = "01JTYP1ST00000000000000001";
    private static final String RECEIVER = "01JRECE1VER000000000000001";
    private static final Instant NOW = Instant.parse("2026-10-24T03:00:00Z");
    private static final VisitFacts VISIT = new VisitFacts(PERFORMER, LocalDate.parse("2026-10-20"), 2);
    private static final LocalDate STATED = LocalDate.parse("2026-10-15");
    private static final SubmittedForm FORM = new SubmittedForm(LocalDate.parse("2026-10-01"), "12 Bạch Đằng", "48",
            "20194", null, null, 15_000_000L, LocalDate.parse("2026-11-01"));
    private static final IdentificationContent WHOLE = identificationWhole();

    @Test
    void ownerStatedDateAndCount_observerIsTheReceiverOnTheStatedDay() {
        ConditionSpace stated = new ConditionSpace(SPACE_1, Field.value(Occupancy.OCCUPIED), SourcedField.unknown(),
                SourcedField.ownerStated(LocalDate.parse("2026-12-01"), STATED, RECEIVER, "note of the call"),
                Field.unknown(), Field.unknown(), Field.unknown());
        UnitMeta availableFrom = metaOf(first(new ConditionContent(null, null, List.of(stated)),
                UnitKey.spaceField(ChecklistGroup.CONDITION, SPACE_1, "availableFrom")));
        DimensionsSpace bedrooms = new DimensionsSpace(SPACE_1, SourcedField.unknown(),
                SourcedField.ownerStated(2, STATED, RECEIVER, null), SourcedField.unknown(), SourcedField.unknown(),
                null);
        UnitMeta counted = metaOf(first(new DimensionsContent(List.of(bedrooms)),
                UnitKey.spaceField(ChecklistGroup.DIMENSIONS, SPACE_1, "bedrooms")));

        for (UnitMeta meta : List.of(availableFrom, counted)) {
            assertEquals(RECEIVER, meta.observedByUserId());
            assertEquals(STATED, meta.observedOn());
            assertEquals(ChecklistSource.OWNER_STATED, meta.lastSource());
            assertEquals(TYPIST, meta.enteredByUserId());
            assertEquals(NOW, meta.enteredAt());
            assertNull(meta.formField());
            assertNull(meta.correctionSaveId());
        }
    }

    @Test
    void unsourcedField_observerIsThePerformerOnTheArrivalDate() {
        UnitMeta occupancy = metaOf(first(new ConditionContent(null, null, List.of(conditionSpace(SPACE_1))),
                UnitKey.spaceField(ChecklistGroup.CONDITION, SPACE_1, "occupancy")));
        UnitMeta measured = metaOf(first(new DimensionsContent(List.of(dimensionsSpace(SPACE_1))),
                UnitKey.spaceField(ChecklistGroup.DIMENSIONS, SPACE_1, "area")));

        for (UnitMeta meta : List.of(occupancy, measured)) {
            assertEquals(PERFORMER, meta.observedByUserId());
            assertEquals(VISIT.arrivalDate(), meta.observedOn());
            assertEquals(TYPIST, meta.enteredByUserId());
        }
        assertNull(occupancy.lastSource());
        assertEquals(ChecklistSource.MEASURED, measured.lastSource());
    }

    @Test
    void ownerFormAddress_noObserverSubmissionDateAndFormField() {
        IdentificationContent g1 = new IdentificationContent(SourcedField.of(WHOLE.observedAddress().value(),
                ChecklistSource.OWNER_FORM), null, WHOLE.propertyType(), WHOLE.fit(), WHOLE.rentalScope(),
                WHOLE.spaces(), null, null);
        UnitMeta meta = metaOf(first(g1, UnitKey.field(ChecklistGroup.IDENTIFICATION, "observedAddress")));

        assertNull(meta.observedByUserId());
        assertEquals(FORM.submittedOn(), meta.observedOn());
        assertEquals("address", meta.formField());
        assertEquals(ChecklistSource.OWNER_FORM, meta.lastSource());
    }

    /** spec A-26: a note is the writer's words: no observer, no observation date. */
    @Test
    void note_hasNoObserver() {
        IdentificationContent g1 = new IdentificationContent(WHOLE.observedAddress(), null, WHOLE.propertyType(),
                WHOLE.fit(), WHOLE.rentalScope(), WHOLE.spaces(), null, "Chủ hẹn gọi lại");
        UnitMeta meta = metaOf(first(g1, UnitKey.field(ChecklistGroup.IDENTIFICATION, "generalNote")));

        assertNull(meta.observedByUserId());
        assertNull(meta.observedOn());
        assertEquals(TYPIST, meta.enteredByUserId());
    }

    @Test
    void hazardWithAnOwnerReport_staysObservedByThePerformer() {
        Hazard reported = new Hazard("01JHAZARD00000000000000001", null, null, "Dây điện hở", HazardSeverity.IMPORTANT,
                HazardStatus.OWNER_REPORTED_FIXED, "Chủ nói đã thay", STATED, RECEIVER, null);
        UnitMeta meta = metaOf(first(safety(reported), UnitKey.hazard(reported.hazardId())));

        assertEquals(PERFORMER, meta.observedByUserId());
        assertEquals(VISIT.arrivalDate(), meta.observedOn());
    }

    /** spec A-19: a cleared owner statement keeps its source, observer and date. */
    @Test
    void clearedOwnerStatement_keepsLastSourceObserverAndDate() {
        UnitKey key = UnitKey.spaceField(ChecklistGroup.DIMENSIONS, SPACE_1, "area");
        UnitMeta stored = new UnitMeta(PERFORMER, Instant.parse("2026-10-20T03:00:00Z"), RECEIVER, STATED, null,
                ChecklistSource.OWNER_STATED, 77L);
        DimensionsSpace statedArea = new DimensionsSpace(SPACE_1, SourcedField.ownerStated(new BigDecimal("60.0"),
                STATED, RECEIVER, null), SourcedField.unknown(), SourcedField.unknown(), SourcedField.unknown(), null);
        GroupDocument document = new GroupDocument(ChecklistGroup.DIMENSIONS,
                new DimensionsContent(List.of(statedArea)), Map.of(key, stored), Map.of());

        UnitChange cleared = UnitDiff.between(document, new DimensionsContent(List.of()), WHOLE).get(0);
        UnitMeta meta = MetaRules.next(cleared, TYPIST, NOW, VISIT, Optional.of(FORM));

        assertEquals(new UnitMeta(TYPIST, NOW, RECEIVER, STATED, null, ChecklistSource.OWNER_STATED, null), meta);
    }

    // ==================== removal (spec A-29, BR-29, BR-30) ====================

    /** BR-29: the remover becomes the last editor at the removal time; the observer and source stay the value's. */
    @Test
    void removed_valueOfOther_stampsRemoverKeepsObserver() {
        UnitKey bed = UnitKey.sharedItem(AmenityCode.BED);
        UnitMeta stored = new UnitMeta(PERFORMER, Instant.parse("2026-10-20T03:00:00Z"), RECEIVER, STATED, null,
                ChecklistSource.OWNER_STATED, 5L);
        GroupDocument document = new GroupDocument(ChecklistGroup.AMENITIES, new AmenitiesContent(List.of(
                new AmenityItem(AmenityCode.BED, YesNo.YES, ChecklistSource.OWNER_STATED, STATED, RECEIVER, "relay")),
                List.of(), List.of()), Map.of(bed, stored), Map.of());

        Tombstone tombstone = MetaRules.removed(removal(document, new AmenitiesContent(List.of(), List.of(),
                List.of()), bed), TYPIST, NOW);

        assertEquals(new Tombstone(TYPIST, NOW, RECEIVER, STATED, null, ChecklistSource.OWNER_STATED, null, null),
                tombstone);
    }

    /** BR-30: a hazard removed with an owner fix report keeps who heard the report and its date. */
    @Test
    void removed_hazardWithReport_keepsReportFacts() {
        Hazard reported = new Hazard("01JHAZARD00000000000000001", null, null, "Dây điện hở", HazardSeverity.MINOR,
                HazardStatus.OWNER_REPORTED_FIXED, "Chủ nói đã thay", STATED, RECEIVER, "relay");
        UnitKey key = UnitKey.hazard(reported.hazardId());
        GroupDocument document = new GroupDocument(ChecklistGroup.SAFETY, safety(reported), Map.of(key,
                new UnitMeta(PERFORMER, Instant.parse("2026-10-20T03:00:00Z"), PERFORMER, VISIT.arrivalDate(), null,
                        null, null)), Map.of());

        Tombstone tombstone = MetaRules.removed(removal(document, safety(openHazard("01JHAZARD00000000000000002",
                HazardSeverity.MINOR)), key), TYPIST, NOW);

        assertEquals(new Tombstone(TYPIST, NOW, PERFORMER, VISIT.arrivalDate(), null, null, null,
                new Tombstone.OwnerReportFacts(RECEIVER, STATED)), tombstone);
    }

    /**
     * spec A-29: a unit already cleared to UNKNOWN holds no value, so its tombstone keeps the editor and time of its
     * clearing; the save points it to the removal's correction when one was needed.
     */
    @Test
    void removed_clearedUnit_keepsItsMetaWithRemovalCorrection() {
        UnitKey kitchen = UnitKey.field(ChecklistGroup.CONDITION, "sharedKitchen");
        UnitMeta cleared = new UnitMeta(RECEIVER, Instant.parse("2026-10-21T03:00:00Z"), PERFORMER,
                VISIT.arrivalDate(), null, null, 3L);
        GroupDocument document = new GroupDocument(ChecklistGroup.CONDITION, new ConditionContent(Field.unknown(),
                Field.unknown(), List.of(conditionSpace(SPACE_1))), Map.of(kitchen, cleared), Map.of());
        UnitChange change = UnitDiff.between(document, identificationParts(observedSpace(SPACE_1, "P101")),
                new ConditionContent(null, null, List.of(conditionSpace(SPACE_1))), WHOLE).stream()
                .filter(c -> c.key().equals(kitchen)).findFirst().orElseThrow();

        Tombstone tombstone = MetaRules.removed(change, TYPIST, NOW);

        assertEquals(ChangeKind.REMOVED, change.kind());
        assertEquals(Tombstone.of(cleared), tombstone);
        assertEquals(new Tombstone(RECEIVER, Instant.parse("2026-10-21T03:00:00Z"), PERFORMER, VISIT.arrivalDate(),
                null, null, 9L, null), tombstone.withCorrection(9L));
    }

    /**
     * Guard (spec A-29): an entry coming back gets the observer and date of its new source and the caller as editor;
     * the caller is never made the observer of an on-site value.
     */
    @Test
    void next_entryBackFromTombstone_metaOfItsNewSource() {
        UnitKey ac = UnitKey.sharedItem(AmenityCode.AIR_CONDITIONER);
        GroupDocument removed = new GroupDocument(ChecklistGroup.AMENITIES, new AmenitiesContent(List.of(), List.of(),
                List.of()), Map.of(), Map.of(ac, new Tombstone(RECEIVER, Instant.parse("2026-10-22T03:00:00Z"),
                PERFORMER, VISIT.arrivalDate(), null, ChecklistSource.OBSERVED_ON_SITE, 4L, null)));

        UnitMeta onSite = metaOf(change(removed, new AmenitiesContent(List.of(new AmenityItem(
                AmenityCode.AIR_CONDITIONER, YesNo.YES, ChecklistSource.OBSERVED_ON_SITE, null, null, null)), List.of(),
                List.of()), ac));
        UnitMeta stated = metaOf(change(removed, new AmenitiesContent(List.of(new AmenityItem(
                AmenityCode.AIR_CONDITIONER, YesNo.NO, ChecklistSource.OWNER_STATED, STATED, RECEIVER, "relay")),
                List.of(), List.of()), ac));

        assertEquals(new UnitMeta(TYPIST, NOW, PERFORMER, VISIT.arrivalDate(), null,
                ChecklistSource.OBSERVED_ON_SITE, null), onSite);
        assertEquals(new UnitMeta(TYPIST, NOW, RECEIVER, STATED, null, ChecklistSource.OWNER_STATED, null), stated);
    }

    // ==================== helpers ====================

    private static UnitChange removal(GroupDocument stored, GroupContent next, UnitKey key) {
        UnitChange change = change(stored, next, key);
        assertEquals(ChangeKind.REMOVED, change.kind());
        return change;
    }

    private static UnitChange change(GroupDocument stored, GroupContent next, UnitKey key) {
        return UnitDiff.between(stored, next, WHOLE).stream().filter(c -> c.key().equals(key)).findFirst()
                .orElseThrow();
    }

    private static UnitChange first(GroupContent next, UnitKey key) {
        return UnitDiff.between(GroupDocument.initial(next.group()), next, WHOLE).stream()
                .filter(change -> change.key().equals(key)).findFirst().orElseThrow();
    }

    private static UnitMeta metaOf(UnitChange change) {
        return MetaRules.next(change, TYPIST, NOW, VISIT, Optional.of(FORM));
    }
}
