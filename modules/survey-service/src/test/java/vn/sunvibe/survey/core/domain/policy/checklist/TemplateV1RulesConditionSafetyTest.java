package vn.sunvibe.survey.core.domain.policy.checklist;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.domain.model.checklist.AreaCheck;
import vn.sunvibe.survey.core.domain.model.checklist.AreaCheckResult;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSource;
import vn.sunvibe.survey.core.domain.model.checklist.ConditionContent;
import vn.sunvibe.survey.core.domain.model.checklist.ConditionSpace;
import vn.sunvibe.survey.core.domain.model.checklist.Field;
import vn.sunvibe.survey.core.domain.model.checklist.Hazard;
import vn.sunvibe.survey.core.domain.model.checklist.HazardLink;
import vn.sunvibe.survey.core.domain.model.checklist.HazardSeverity;
import vn.sunvibe.survey.core.domain.model.checklist.HazardStatus;
import vn.sunvibe.survey.core.domain.model.checklist.Observation;
import vn.sunvibe.survey.core.domain.model.checklist.Occupancy;
import vn.sunvibe.survey.core.domain.model.checklist.SafetyContent;
import vn.sunvibe.survey.core.domain.model.checklist.SourcedField;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static vn.sunvibe.survey.core.domain.policy.checklist.TemplateV1RulesIdentificationTest.assertPaths;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.SALE;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.SPACE_1;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.SPACE_2;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.TODAY;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.condition;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.conditionParts;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.conditionSpace;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.identificationParts;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.observedSpace;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.openHazard;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.ownerSaid;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.safety;

/** The stateless body rules of G4 and G5 (survey-checklist spec 4.6, 4.6.1, 5.5 save step 1; W16-6, W16-10, W16-12). */
class TemplateV1RulesConditionSafetyTest {

    private static final String PHONE = "gọi 0905 123 456";
    private static final String H1 = "01JHAZARD00000000000000001";
    private static final String H2 = "01JHAZARD00000000000000002";
    private static final String EARLIER_CHECKLIST = "01JCHECK000000000000000001";
    private static final String EARLIER_HAZARD = "01JHAZARD00000000000000009";

    @Test
    void validConditionAndSafety_passUnchanged() {
        for (var content : List.of(condition(SPACE_1), conditionParts(SPACE_1, SPACE_2),
                safety(openHazard(H1, HazardSeverity.IMPORTANT)), safety())) {
            assertEquals(content, TemplateV1Rules.checkAndNormalise(content, TODAY));
        }
    }

    // ==================== G4 ====================

    /** W16-10: the main space always exists. */
    @Test
    void mainSpace_notApplicable_isRefused() {
        assertPaths(withSpace(s -> new ConditionSpace(s.spaceId(), s.occupancy(), s.expectedVacantFrom(),
                s.availableFrom(), Field.notApplicable("Không có"), s.kitchen(), s.bathroom())),
                "content.spaces.0.mainSpace.state");
    }

    @Test
    void kitchenBathroomAndSharedAreas_mayBeNotApplicable() {
        ConditionContent none = withSpace(s -> new ConditionSpace(s.spaceId(), s.occupancy(), s.expectedVacantFrom(),
                s.availableFrom(), s.mainSpace(), Field.notApplicable("Không có bếp"),
                Field.notApplicable("Dùng phòng tắm chung")));

        assertEquals(none, TemplateV1Rules.checkAndNormalise(none, TODAY));
    }

    /** W16-10: NOT_ENTERED needs what was not reached and why. */
    @Test
    void notEntered_withoutReason_isRefused() {
        assertPaths(withKitchen(new AreaCheck(AreaCheckResult.NOT_ENTERED, null, null, null)),
                "content.spaces.0.kitchen.value.notEnteredReason");
        assertPaths(withKitchen(new AreaCheck(AreaCheckResult.NOT_ENTERED, null, null, PHONE)),
                "content.spaces.0.kitchen.value.notEnteredReason");
        assertPaths(withKitchen(new AreaCheck(AreaCheckResult.NOT_ENTERED, null, PHONE, "Cửa khóa")),
                "content.spaces.0.kitchen.value.note");
    }

    @Test
    void notEntered_withANoteOfWhatWasSeen_isAccepted() {
        ConditionContent partly = withKitchen(new AreaCheck(AreaCheckResult.NOT_ENTERED, null,
                "Nhìn từ cửa thấy bếp gas", "Chỉ vào được nửa bếp"));

        assertEquals(partly, TemplateV1Rules.checkAndNormalise(partly, TODAY));
    }

    @Test
    void inspected_needsNoneObservedAndANoteWhenSomethingWasSeen() {
        assertPaths(withKitchen(new AreaCheck(AreaCheckResult.INSPECTED, null, null, null)),
                "content.spaces.0.kitchen.value.noneObserved");
        assertPaths(withKitchen(new AreaCheck(AreaCheckResult.INSPECTED, false, null, null)),
                "content.spaces.0.kitchen.value.note");
        assertPaths(withKitchen(new AreaCheck(AreaCheckResult.INSPECTED, false, PHONE, null)),
                "content.spaces.0.kitchen.value.note");
        assertPaths(withKitchen(new AreaCheck(AreaCheckResult.INSPECTED, true, null, "Cửa khóa")),
                "content.spaces.0.kitchen.value.notEnteredReason");
        assertPaths(withKitchen(new AreaCheck(AreaCheckResult.NOT_ENTERED, true, null, "Cửa khóa")),
                "content.spaces.0.kitchen.value.noneObserved");
        assertPaths(withKitchen(new AreaCheck(null, true, null, null)), "content.spaces.0.kitchen.value.result");
        ConditionContent seen = withKitchen(new AreaCheck(AreaCheckResult.INSPECTED, false, "Vòi nước rỉ", null));
        assertEquals(seen, TemplateV1Rules.checkAndNormalise(seen, TODAY));
    }

    @Test
    void occupancy_hasNoNotApplicable() {
        assertPaths(withSpace(s -> new ConditionSpace(s.spaceId(), Field.notApplicable("x"), s.expectedVacantFrom(),
                s.availableFrom(), s.mainSpace(), s.kitchen(), s.bathroom())), "content.spaces.0.occupancy.state");
    }

    @Test
    void dates_upToThreeYearsAndOwnerFormOnlyOnAvailableFrom() {
        assertPaths(withDates(SourcedField.of(TODAY.plusYears(3).plusDays(1), ChecklistSource.OBSERVED_ON_SITE),
                SourcedField.unknown()), "content.spaces.0.expectedVacantFrom.value");
        assertPaths(withDates(SourcedField.unknown(), ownerSaid(TODAY.plusYears(4), TODAY)),
                "content.spaces.0.availableFrom.value");
        assertPaths(withDates(SourcedField.of(TODAY, ChecklistSource.OWNER_FORM), SourcedField.unknown()),
                "content.spaces.0.expectedVacantFrom.source");
        assertPaths(withDates(SourcedField.unknown(), SourcedField.of(TODAY, ChecklistSource.MEASURED)),
                "content.spaces.0.availableFrom.source");
    }

    /** spec A-7: an owner's-form date only has to equal the submission, checked at save step 9. */
    @Test
    void ownerFormAvailableFrom_fourYearsAhead_passesTheBodyRules() {
        ConditionContent content = withDates(SourcedField.unknown(),
                SourcedField.of(TODAY.plusYears(4), ChecklistSource.OWNER_FORM));

        assertEquals(content, TemplateV1Rules.checkAndNormalise(content, TODAY));
    }

    @Test
    void sharedAreas_areCheckedLikeTheOthers() {
        ConditionContent parts = conditionParts(SPACE_1);
        assertPaths(new ConditionContent(Field.value(new AreaCheck(AreaCheckResult.NOT_ENTERED, null, null, null)),
                parts.sharedBathroom(), parts.spaces()), "content.sharedKitchen.value.notEnteredReason");
        assertPaths(new ConditionContent(parts.sharedKitchen(), Field.notApplicable(PHONE), parts.spaces()),
                "content.sharedBathroom.naReason");
    }

    @Test
    void condition_spaceIdTwice_isRefused() {
        assertPaths(new ConditionContent(null, null, List.of(conditionSpace(SPACE_1), conditionSpace(SPACE_1))),
                "content.spaces.1.spaceId");
    }

    @Test
    void canonical_partsNullSharedKitchen_keptNull() {
        ConditionContent sent = condition(SPACE_1);

        ConditionContent canonical = (ConditionContent) TemplateV1Rules.canonical(sent,
                identificationParts(observedSpace(SPACE_1, "P101"), observedSpace(SPACE_2, "P102")));

        assertNull(canonical.sharedKitchen());
        assertNull(canonical.sharedBathroom());
        assertEquals(List.of(conditionSpace(SPACE_1), new ConditionSpace(SPACE_2, Field.unknown(),
                SourcedField.unknown(), SourcedField.unknown(), Field.unknown(), Field.unknown(), Field.unknown())),
                canonical.spaces());
    }

    // ==================== G5 ====================

    @Test
    void egressAndAccessRestrictions_textAndObservationRules() {
        SafetyContent valid = safety();
        assertPaths(new SafetyContent(Field.value(PHONE), valid.accessRestrictions(), valid.hazards()),
                "content.egress.value");
        assertPaths(new SafetyContent(valid.egress(), Field.value(new Observation(false, null)), valid.hazards()),
                "content.accessRestrictions.value.note");
        assertPaths(new SafetyContent(valid.egress(), Field.value(new Observation(false, PHONE)), valid.hazards()),
                "content.accessRestrictions.value.note");
        assertPaths(new SafetyContent(valid.egress(), Field.value(new Observation(null, "x")), valid.hazards()),
                "content.accessRestrictions.value.noneObserved");
        assertPaths(new SafetyContent(valid.egress(), valid.accessRestrictions(), Field.notApplicable("x")),
                "content.hazards.state");
    }

    /** W16-6: at most 30 hazards. */
    @Test
    void hazards_thirtyOne_isRefused() {
        List<Hazard> many = new ArrayList<>();
        for (int i = 1; i <= 31; i++) {
            many.add(openHazard(String.format("01JHAZARD%017d", i), HazardSeverity.MINOR));
        }
        assertPaths(safety(many.toArray(Hazard[]::new)), "content.hazards.value");
        SafetyContent thirty = safety(many.subList(0, 30).toArray(Hazard[]::new));
        assertEquals(thirty, TemplateV1Rules.checkAndNormalise(thirty, TODAY));
    }

    /** W16-42: a linked hazard starts empty; its description is still required. */
    @Test
    void linkedHazard_withoutDescription_isRefused() {
        assertPaths(safety(new Hazard(H1, null, new HazardLink(EARLIER_CHECKLIST, EARLIER_HAZARD), null,
                HazardSeverity.IMPORTANT, HazardStatus.OPEN, null, null, null, null)),
                "content.hazards.value.0.description");
    }

    /** W16-12: an owner fix report needs the date, the receiver and a status note. */
    @Test
    void ownerReportedFixed_needsDateReceiverAndNote() {
        assertPaths(safety(hazard(HazardStatus.OWNER_REPORTED_FIXED, "Chủ nói đã sửa", null, SALE, null)),
                "content.hazards.value.0.ownerReportedOn");
        assertPaths(safety(hazard(HazardStatus.OWNER_REPORTED_FIXED, "Chủ nói đã sửa", TODAY, null, null)),
                "content.hazards.value.0.receivedByUserId");
        assertPaths(safety(hazard(HazardStatus.OWNER_REPORTED_FIXED, null, TODAY, SALE, null)),
                "content.hazards.value.0.statusNote");
        Hazard reported = hazard(HazardStatus.OWNER_REPORTED_FIXED, "Chủ nói đã sửa", LocalDate.parse("2026-01-02"),
                SALE, "Ghi chú cuộc gọi của trưởng nhóm");
        assertEquals(safety(reported), TemplateV1Rules.checkAndNormalise(safety(reported), TODAY));
    }

    @Test
    void reportParts_onlyWithAnOwnerFixReport() {
        assertPaths(safety(hazard(HazardStatus.OPEN, null, TODAY, null, null)),
                "content.hazards.value.0.ownerReportedOn");
        assertPaths(safety(hazard(HazardStatus.OBSERVED_RESOLVED, "Đã sửa", null, SALE, null)),
                "content.hazards.value.0.receivedByUserId");
        assertPaths(safety(hazard(HazardStatus.OPEN, null, null, null, "ghi chú")),
                "content.hazards.value.0.relayBasis");
        assertPaths(safety(hazard(HazardStatus.OBSERVED_RESOLVED, null, null, null, null)),
                "content.hazards.value.0.statusNote");
    }

    @Test
    void hazardTexts_phoneAndLength() {
        assertPaths(safety(new Hazard(H1, null, null, PHONE, HazardSeverity.MINOR, HazardStatus.OPEN, null, null, null,
                null)), "content.hazards.value.0.description");
        assertPaths(safety(new Hazard(H1, null, null, "x".repeat(501), HazardSeverity.MINOR, HazardStatus.OPEN, null,
                null, null, null)), "content.hazards.value.0.description");
        assertPaths(safety(hazard(HazardStatus.OBSERVED_RESOLVED, PHONE, null, null, null)),
                "content.hazards.value.0.statusNote");
        assertPaths(safety(hazard(HazardStatus.OWNER_REPORTED_FIXED, "Đã sửa", TODAY, SALE, PHONE)),
                "content.hazards.value.0.relayBasis");
    }

    @Test
    void hazards_severityStatusAndIds() {
        assertPaths(safety(new Hazard(H1, null, null, "Dây điện", null, HazardStatus.OPEN, null, null, null, null)),
                "content.hazards.value.0.severity");
        assertPaths(safety(new Hazard(H1, null, null, "Dây điện", HazardSeverity.MINOR, null, null, null, null, null)),
                "content.hazards.value.0.status");
        assertPaths(safety(openHazard(H1, HazardSeverity.MINOR), openHazard(H1, HazardSeverity.MINOR)),
                "content.hazards.value.1.hazardId");
        assertPaths(safety(new Hazard(H1, "P101", null, "Dây điện", HazardSeverity.MINOR, HazardStatus.OPEN, null,
                null, null, null)), "content.hazards.value.0.spaceId");
        assertPaths(safety(new Hazard(H1, null, new HazardLink("x", EARLIER_HAZARD), "Dây điện", HazardSeverity.MINOR,
                HazardStatus.OPEN, null, null, null, null)), "content.hazards.value.0.previous.checklistId");
    }

    /** W16-12: an earlier hazard is linked at most once per checklist. */
    @Test
    void previous_twiceToTheSameHazard_isRefused() {
        HazardLink link = new HazardLink(EARLIER_CHECKLIST, EARLIER_HAZARD);
        assertPaths(safety(linked(H1, link), linked(H2, link)), "content.hazards.value.1.previous");
    }

    // ==================== helpers ====================

    private static Hazard hazard(HazardStatus status, String statusNote, LocalDate reportedOn, String receiver,
                                 String relay) {
        return new Hazard(H1, null, null, "Dây điện hở", HazardSeverity.IMPORTANT, status, statusNote, reportedOn,
                receiver, relay);
    }

    private static Hazard linked(String id, HazardLink link) {
        return new Hazard(id, null, link, "Vẫn còn dây điện hở", HazardSeverity.IMPORTANT, HazardStatus.OPEN, null,
                null, null, null);
    }

    private static ConditionContent withSpace(UnaryOperator<ConditionSpace> change) {
        return new ConditionContent(null, null, List.of(change.apply(conditionSpace(SPACE_1))));
    }

    private static ConditionContent withKitchen(AreaCheck kitchen) {
        return withSpace(s -> new ConditionSpace(s.spaceId(), s.occupancy(), s.expectedVacantFrom(),
                s.availableFrom(), s.mainSpace(), Field.value(kitchen), s.bathroom()));
    }

    private static ConditionContent withDates(SourcedField<LocalDate> expectedVacantFrom,
                                              SourcedField<LocalDate> availableFrom) {
        return withSpace(s -> new ConditionSpace(s.spaceId(), Field.value(Occupancy.OCCUPIED), expectedVacantFrom,
                availableFrom, s.mainSpace(), s.kitchen(), s.bathroom()));
    }
}
