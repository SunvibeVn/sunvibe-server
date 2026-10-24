package vn.sunvibe.survey.core.domain.policy.checklist;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSource;
import vn.sunvibe.survey.core.domain.model.checklist.ConditionContent;
import vn.sunvibe.survey.core.domain.model.checklist.ConditionSpace;
import vn.sunvibe.survey.core.domain.model.checklist.DimensionsContent;
import vn.sunvibe.survey.core.domain.model.checklist.DimensionsSpace;
import vn.sunvibe.survey.core.domain.model.checklist.Field;
import vn.sunvibe.survey.core.domain.model.checklist.GroupContent;
import vn.sunvibe.survey.core.domain.model.checklist.Hazard;
import vn.sunvibe.survey.core.domain.model.checklist.HazardSeverity;
import vn.sunvibe.survey.core.domain.model.checklist.HazardStatus;
import vn.sunvibe.survey.core.domain.model.checklist.IdentificationContent;
import vn.sunvibe.survey.core.domain.model.checklist.ObservedLocation;
import vn.sunvibe.survey.core.domain.model.checklist.RentMoney;
import vn.sunvibe.survey.core.domain.model.checklist.RentalScope;
import vn.sunvibe.survey.core.domain.model.checklist.SourcedField;
import vn.sunvibe.survey.core.domain.model.checklist.TermsContent;
import vn.sunvibe.survey.core.domain.model.checklist.TermsSpace;
import vn.sunvibe.survey.core.domain.model.checklist.Violation;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupDocument;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.SPACE_1;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.identificationWhole;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.safety;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.terms;

/**
 * The receiver, relay basis and owner's form rules of save step 9 (survey-checklist BR-21, BR-25, spec A-18, A-25;
 * W16-41 at domain level), on created or changed units only.
 */
class SourceRulesTest {

    private static final String CALLER = "01JCA11ER00000000000000001";
    private static final String MANAGER = "01JMANAGER0000000000000001";
    private static final String STRANGER = "01JSTRANGER000000000000001";
    private static final Set<String> STAFF = Set.of(CALLER, MANAGER);
    private static final LocalDate STATED = LocalDate.parse("2026-10-15");
    private static final SubmittedForm FORM = new SubmittedForm(LocalDate.parse("2026-10-01"), "12 Bạch Đằng", "48",
            "20194", new BigDecimal("16.054456"), new BigDecimal("108.202167"), 15_000_000L,
            LocalDate.parse("2026-11-01"));
    private static final ObservedLocation FORM_ADDRESS = new ObservedLocation("12 Bạch Đằng", "48", "20194",
            new BigDecimal("16.054456"), new BigDecimal("108.202167"));
    private static final IdentificationContent WHOLE = identificationWhole();
    private static final String RECEIVER_MESSAGE = "Choose who heard it from the lead's staff.";

    @Test
    void managerOnTheLeadStaff_isAcceptedWithARelayBasis() {
        assertEquals(List.of(), check(area(SourcedField.ownerStated(BigDecimal.TEN, STATED, MANAGER,
                "Ghi chú cuộc gọi của quản lý 20/10")), WHOLE.rentalScope()));
    }

    @Test
    void someoneElsesStatement_withoutRelayBasis_isRefused() {
        assertEquals(List.of(new Violation("content.spaces.0.area.relayBasis",
                        "content.spaces.0.area.relayBasis is required when someone else heard the owner.")),
                check(area(SourcedField.ownerStated(BigDecimal.TEN, STATED, MANAGER, null)), RentalScope.WHOLE));
    }

    @Test
    void ownStatement_withRelayBasis_isRefused() {
        assertEquals(List.of("content.spaces.0.area.relayBasis"), paths(check(area(SourcedField.ownerStated(
                BigDecimal.TEN, STATED, CALLER, "Tôi nghe trực tiếp")), RentalScope.WHOLE)));
    }

    @Test
    void receiverOutsideTheLeadStaff_isRefusedWithTheOneMessage() {
        assertEquals(List.of(new Violation("content.spaces.0.area.receivedByUserId", RECEIVER_MESSAGE)),
                check(area(SourcedField.ownerStated(BigDecimal.TEN, STATED, STRANGER, "relay")), RentalScope.WHOLE));
    }

    /** W16-41: an unchanged unit keeps its receiver even when the receiver left the lead's staff. */
    @Test
    void unchangedUnitWhoseReceiverLeftTheSet_passes() {
        DimensionsContent stored = area(SourcedField.ownerStated(BigDecimal.TEN, STATED, STRANGER, "relay"));
        GroupDocument document = new GroupDocument(ChecklistGroup.DIMENSIONS, stored, Map.of(), Map.of());
        DimensionsContent next = new DimensionsContent(List.of(new DimensionsSpace(SPACE_1,
                stored.spaces().get(0).area(), SourcedField.of(2, ChecklistSource.OBSERVED_ON_SITE),
                SourcedField.unknown(), SourcedField.unknown(), null)));

        assertEquals(List.of(), SourceRules.check(UnitDiff.between(document, next, WHOLE), CALLER, STAFF,
                Optional.of(FORM), RentalScope.WHOLE));
    }

    @Test
    void ownerFixReport_receiverAndRelayRules() {
        Hazard heardByStranger = new Hazard("01JHAZARD00000000000000001", null, null, "Dây điện",
                HazardSeverity.IMPORTANT, HazardStatus.OWNER_REPORTED_FIXED, "Chủ nói đã sửa", STATED, STRANGER, "x");
        Hazard heardByManager = new Hazard("01JHAZARD00000000000000001", null, null, "Dây điện",
                HazardSeverity.IMPORTANT, HazardStatus.OWNER_REPORTED_FIXED, "Chủ nói đã sửa", STATED, MANAGER, null);

        assertEquals(List.of("content.hazards.value.0.receivedByUserId"), paths(check(safety(heardByStranger),
                RentalScope.WHOLE)));
        assertEquals(List.of("content.hazards.value.0.relayBasis"), paths(check(safety(heardByManager),
                RentalScope.WHOLE)));
    }

    @Test
    void ownerFormAddress_onlyTheSubmittedOne() {
        assertEquals(List.of(), check(withAddress(FORM_ADDRESS), RentalScope.WHOLE));
        assertEquals(List.of("content.observedAddress.source"), paths(check(withAddress(new ObservedLocation(
                "14 Bạch Đằng", "48", "20194", FORM_ADDRESS.latitude(), FORM_ADDRESS.longitude())),
                RentalScope.WHOLE)));
        assertEquals(List.of("content.observedAddress.source"), paths(SourceRules.check(changes(
                withAddress(FORM_ADDRESS)), CALLER, STAFF, Optional.empty(), RentalScope.WHOLE)));
    }

    @Test
    void ownerFormRent_onlyTheMonthlySubmittedPriceInWholeScope() {
        assertEquals(List.of(), check(rent(15_000_000), RentalScope.WHOLE));
        assertEquals(List.of("content.spaces.0.monthlyRent.source"), paths(check(rent(14_000_000),
                RentalScope.WHOLE)));
        assertEquals(List.of("content.spaces.0.monthlyRent.source"), paths(check(rent(15_000_000),
                RentalScope.PARTS)));
        SubmittedForm nightly = new SubmittedForm(FORM.submittedOn(), FORM.addressDetail(), FORM.provinceCode(),
                FORM.wardCode(), null, null, null, FORM.availableFrom());
        assertEquals(List.of("content.spaces.0.monthlyRent.source"), paths(SourceRules.check(changes(
                rent(15_000_000)), CALLER, STAFF, Optional.of(nightly), RentalScope.WHOLE)));
    }

    @Test
    void ownerFormAvailableFrom_onlyTheSubmittedDateInWholeScope() {
        assertEquals(List.of(), check(availableFrom(FORM.availableFrom()), RentalScope.WHOLE));
        assertEquals(List.of("content.spaces.0.availableFrom.source"), paths(check(availableFrom(
                LocalDate.parse("2026-11-02")), RentalScope.WHOLE)));
        assertEquals(List.of("content.spaces.0.availableFrom.source"), paths(check(availableFrom(
                FORM.availableFrom()), RentalScope.PARTS)));
    }

    @Test
    void clearedUnit_isNotChecked() {
        DimensionsContent stored = area(SourcedField.ownerStated(BigDecimal.TEN, STATED, STRANGER, null));
        GroupDocument document = new GroupDocument(ChecklistGroup.DIMENSIONS, stored,
                Map.of(UnitKey.spaceField(ChecklistGroup.DIMENSIONS, SPACE_1, "area"), new UnitMeta(CALLER,
                        Instant.parse("2026-10-20T03:00:00Z"), STRANGER, STATED, null, ChecklistSource.OWNER_STATED,
                        null)), Map.of());

        assertEquals(List.of(), SourceRules.check(UnitDiff.between(document, new DimensionsContent(List.of()), WHOLE),
                CALLER, STAFF, Optional.of(FORM), RentalScope.WHOLE));
    }

    // ==================== helpers ====================

    private static List<Violation> check(GroupContent next, RentalScope scope) {
        return SourceRules.check(changes(next), CALLER, STAFF, Optional.of(FORM), scope);
    }

    private static List<UnitChange> changes(GroupContent next) {
        IdentificationContent g1 = next instanceof IdentificationContent g ? g : WHOLE;
        return UnitDiff.between(GroupDocument.initial(next.group()), next, g1);
    }

    private static List<String> paths(List<Violation> violations) {
        return violations.stream().map(Violation::path).toList();
    }

    private static DimensionsContent area(SourcedField<BigDecimal> area) {
        return new DimensionsContent(List.of(new DimensionsSpace(SPACE_1, area, SourcedField.unknown(),
                SourcedField.unknown(), SourcedField.unknown(), null)));
    }

    private static IdentificationContent withAddress(ObservedLocation address) {
        return new IdentificationContent(SourcedField.of(address, ChecklistSource.OWNER_FORM), null,
                WHOLE.propertyType(), WHOLE.fit(), WHOLE.rentalScope(), WHOLE.spaces(), null, null);
    }

    private static TermsContent rent(long amount) {
        TermsContent terms = terms();
        return new TermsContent(terms.common(), List.of(new TermsSpace(SPACE_1,
                SourcedField.of(RentMoney.monthly(amount), ChecklistSource.OWNER_FORM), SourcedField.unknown(),
                SourcedField.unknown(), SourcedField.unknown(), null, null)));
    }

    private static ConditionContent availableFrom(LocalDate date) {
        return new ConditionContent(null, null, List.of(new ConditionSpace(SPACE_1, Field.unknown(),
                SourcedField.unknown(), SourcedField.of(date, ChecklistSource.OWNER_FORM), Field.unknown(),
                Field.unknown(), Field.unknown())));
    }
}
