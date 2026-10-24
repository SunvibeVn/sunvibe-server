package vn.sunvibe.survey.core.domain.policy.checklist;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.domain.exception.ChecklistValidationException;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSource;
import vn.sunvibe.survey.core.domain.model.checklist.Field;
import vn.sunvibe.survey.core.domain.model.checklist.FieldState;
import vn.sunvibe.survey.core.domain.model.checklist.Fit;
import vn.sunvibe.survey.core.domain.model.checklist.FitAssessment;
import vn.sunvibe.survey.core.domain.model.checklist.GroupContent;
import vn.sunvibe.survey.core.domain.model.checklist.IdentificationContent;
import vn.sunvibe.survey.core.domain.model.checklist.ObservedLocation;
import vn.sunvibe.survey.core.domain.model.checklist.ObservedSpace;
import vn.sunvibe.survey.core.domain.model.checklist.PropertyTypeCode;
import vn.sunvibe.survey.core.domain.model.checklist.RentalScope;
import vn.sunvibe.survey.core.domain.model.checklist.SourcedField;
import vn.sunvibe.survey.core.domain.model.checklist.Violation;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.SALE;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.SPACE_1;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.SPACE_2;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.TODAY;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.identification;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.identificationParts;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.identificationWhole;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.observedSpace;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.space;

/**
 * The stateless body rules of G1 (survey-checklist spec 4.6, 5.5 save step 1; W16-6, W16-9): every broken value is
 * reported on its JSON path, all of them at once, without quoting the value.
 */
class TemplateV1RulesIdentificationTest {

    private static final String PHONE = "gọi 0905 123 456";
    private static final ObservedLocation ADDRESS = new ObservedLocation("12 Bạch Đằng", "48", "20194", null, null);

    @Test
    void checkAndNormalise_validWholeAndParts_passUnchanged() {
        IdentificationContent whole = identificationWhole();
        IdentificationContent parts = identificationParts(observedSpace(SPACE_1, "P101"),
                observedSpace(SPACE_2, "P102"));

        assertEquals(whole, TemplateV1Rules.checkAndNormalise(whole, TODAY));
        assertEquals(parts, TemplateV1Rules.checkAndNormalise(parts, TODAY));
    }

    @Test
    void wrapper_valueStateWithoutValue_isRefused() {
        assertPaths(with(g -> withType(g, new Field<>(FieldState.VALUE, null, null))), "content.propertyType.value");
    }

    @Test
    void wrapper_valueWithUnknownState_isRefused() {
        assertPaths(with(g -> withType(g, new Field<>(FieldState.UNKNOWN, PropertyTypeCode.APARTMENT, null))),
                "content.propertyType.value");
    }

    @Test
    void wrapper_stateMissing_isRefused() {
        assertPaths(with(g -> withType(g, new Field<>(null, null, null))), "content.propertyType.state");
    }

    @Test
    void wrapper_notApplicableOnAFieldWithoutNa_isRefused() {
        assertPaths(with(g -> withAddress(g, SourcedField.notApplicable("Không có địa chỉ"))),
                "content.observedAddress.state");
        assertPaths(with(g -> withType(g, Field.notApplicable("Không rõ"))), "content.propertyType.state");
    }

    @Test
    void ownerStated_withoutDateOrReceiver_isRefused() {
        assertPaths(with(g -> withAddress(g, SourcedField.ownerStated(ADDRESS, null, SALE, null))),
                "content.observedAddress.confirmedOn");
        assertPaths(with(g -> withAddress(g, SourcedField.ownerStated(ADDRESS, TODAY, null, null))),
                "content.observedAddress.receivedByUserId");
    }

    @Test
    void ownerStated_receiverNotAUserId_isRefused() {
        assertPaths(with(g -> withAddress(g, SourcedField.ownerStated(ADDRESS, TODAY, "someone", null))),
                "content.observedAddress.receivedByUserId");
    }

    @Test
    void ownerStated_dateAfterTodayOrBefore2000_isRefused() {
        assertPaths(with(g -> withAddress(g, SourcedField.ownerStated(ADDRESS, TODAY.plusDays(1), SALE, null))),
                "content.observedAddress.confirmedOn");
        assertPaths(with(g -> withAddress(g, SourcedField.ownerStated(ADDRESS, LocalDate.parse("1999-12-31"), SALE,
                null))), "content.observedAddress.confirmedOn");
    }

    /** survey-checklist BR-19, W16-7: an owner said it before the lead existed. */
    @Test
    void ownerStated_longAgo_isAccepted() {
        IdentificationContent content = with(g -> withAddress(g, SourcedField.ownerStated(ADDRESS,
                LocalDate.parse("2001-05-01"), SALE, null)));

        assertEquals(content, TemplateV1Rules.checkAndNormalise(content, TODAY));
    }

    @Test
    void onSiteSource_withDateReceiverOrRelay_isRefused() {
        assertPaths(with(g -> withAddress(g, sourced(ChecklistSource.OBSERVED_ON_SITE, TODAY, null, null))),
                "content.observedAddress.confirmedOn");
        assertPaths(with(g -> withAddress(g, sourced(ChecklistSource.OBSERVED_ON_SITE, null, SALE, null))),
                "content.observedAddress.receivedByUserId");
        assertPaths(with(g -> withAddress(g, sourced(ChecklistSource.OBSERVED_ON_SITE, null, null, "ghi chú"))),
                "content.observedAddress.relayBasis");
    }

    @Test
    void ownerForm_withRelayBasis_isRefused() {
        assertPaths(with(g -> withAddress(g, sourced(ChecklistSource.OWNER_FORM, null, null, "ghi chú"))),
                "content.observedAddress.relayBasis");
    }

    @Test
    void source_ofAnotherFamilyOrMissing_isRefused() {
        assertPaths(with(g -> withAddress(g, sourced(ChecklistSource.MEASURED, null, null, null))),
                "content.observedAddress.source");
        assertPaths(with(g -> withAddress(g, sourced(null, null, null, null))), "content.observedAddress.source");
    }

    @Test
    void source_onUnknownField_isRefused() {
        assertPaths(with(g -> withAddress(g, new SourcedField<>(FieldState.UNKNOWN, null, null,
                ChecklistSource.OBSERVED_ON_SITE, null, null, null))), "content.observedAddress.source");
    }

    @Test
    void address_textAndCatalogShape_isChecked() {
        assertPaths(with(g -> withAddress(g, onSite(new ObservedLocation("x".repeat(256), "48", "20194", null,
                null)))), "content.observedAddress.value.addressText");
        assertPaths(with(g -> withAddress(g, onSite(new ObservedLocation("12 Bạch Đằng " + PHONE, "48", "20194",
                null, null)))), "content.observedAddress.value.addressText");
        assertPaths(with(g -> withAddress(g, onSite(new ObservedLocation("12 Bạch Đằng", null, "20194", null,
                null)))), "content.observedAddress.value.provinceCode");
        assertPaths(with(g -> withAddress(g, onSite(new ObservedLocation("12 Bạch Đằng", "48", " ", null, null)))),
                "content.observedAddress.value.wardCode");
        assertPaths(with(g -> withAddress(g, onSite(new ObservedLocation("12 Bạch Đằng", "48", "20194",
                new BigDecimal("16.05"), null)))), "content.observedAddress.value.longitude");
    }

    @Test
    void fit_blankOrLongOrPhoneReason_isRefused() {
        assertPaths(with(g -> withFit(g, new FitAssessment(Fit.IN_SCOPE, "   "))), "content.fit.value.reason");
        assertPaths(with(g -> withFit(g, new FitAssessment(Fit.IN_SCOPE, "x".repeat(1001)))),
                "content.fit.value.reason");
        assertPaths(with(g -> withFit(g, new FitAssessment(Fit.IN_SCOPE, PHONE))), "content.fit.value.reason");
        assertPaths(with(g -> withFit(g, new FitAssessment(null, "Lý do"))), "content.fit.value.assessment");
    }

    @Test
    void notes_withPhoneNumberOrTooLong_areRefused() {
        assertPaths(with(g -> new IdentificationContent(g.observedAddress(), PHONE, g.propertyType(), g.fit(),
                g.rentalScope(), g.spaces(), g.otherPartsNote(), g.generalNote())), "content.differenceNote");
        assertPaths(with(g -> new IdentificationContent(g.observedAddress(), "x".repeat(501), g.propertyType(),
                g.fit(), g.rentalScope(), g.spaces(), g.otherPartsNote(), g.generalNote())), "content.differenceNote");
        assertPaths(with(g -> new IdentificationContent(g.observedAddress(), g.differenceNote(), g.propertyType(),
                g.fit(), g.rentalScope(), g.spaces(), PHONE, g.generalNote())), "content.otherPartsNote");
        assertPaths(with(g -> new IdentificationContent(g.observedAddress(), g.differenceNote(), g.propertyType(),
                g.fit(), g.rentalScope(), g.spaces(), g.otherPartsNote(), PHONE)), "content.generalNote");
        assertPaths(with(g -> new IdentificationContent(g.observedAddress(), g.differenceNote(), g.propertyType(),
                g.fit(), g.rentalScope(), g.spaces(), g.otherPartsNote(), "x".repeat(2001))), "content.generalNote");
    }

    @Test
    void notes_blank_becomeNull() {
        IdentificationContent blank = with(g -> new IdentificationContent(g.observedAddress(), "  ", g.propertyType(),
                g.fit(), g.rentalScope(), g.spaces(), " ", "\n"));

        assertEquals(identificationWhole(), TemplateV1Rules.checkAndNormalise(blank, TODAY));
    }

    @Test
    void spaces_idNotAUlid_isRefused() {
        assertPaths(withSpaces(RentalScope.WHOLE, new ObservedSpace("space-1", "A1", "Tầng 2", null)),
                "content.spaces.0.spaceId");
    }

    @Test
    void spaces_sameIdTwice_isRefused() {
        assertPaths(withSpaces(RentalScope.PARTS, observedSpace(SPACE_1, "P101"), observedSpace(SPACE_1, "P102")),
                "content.spaces.1.spaceId");
    }

    /** W16-9: a code is unique ignoring case. */
    @Test
    void spaces_duplicateCodeIgnoringCase_isRefused() {
        assertPaths(withSpaces(RentalScope.PARTS, observedSpace(SPACE_1, "p101"), observedSpace(SPACE_2, "P101")),
                "content.spaces.1.code");
    }

    /** Review Focus 5: a Vietnamese space code is refused with the allowed characters. */
    @Test
    void identification_codeWithVietnameseLetters_isRefused() {
        ChecklistValidationException refused = assertThrows(ChecklistValidationException.class,
                () -> TemplateV1Rules.checkAndNormalise(withSpaces(RentalScope.WHOLE,
                        observedSpace(SPACE_1, "Phòng 101")), TODAY));

        assertEquals(List.of("content.spaces.0.code"), paths(refused));
        assertEquals("content.spaces.0.code must be 1 to 20 of the letters A-Z, digits, space, '.', '_', '/' or '-'.",
                refused.violations().get(0).message());
    }

    @Test
    void spaces_codeTooLongEmptyOrPhone_isRefused() {
        assertPaths(withSpaces(RentalScope.WHOLE, observedSpace(SPACE_1, "A".repeat(21))), "content.spaces.0.code");
        assertPaths(withSpaces(RentalScope.WHOLE, observedSpace(SPACE_1, " ")), "content.spaces.0.code");
        assertPaths(withSpaces(RentalScope.WHOLE, observedSpace(SPACE_1, "0905123456")), "content.spaces.0.code");
    }

    @Test
    void spaces_locationAndDescriptionTextRules() {
        assertPaths(withSpaces(RentalScope.WHOLE, new ObservedSpace(SPACE_1, "A1", PHONE, null)),
                "content.spaces.0.location");
        assertPaths(withSpaces(RentalScope.WHOLE, new ObservedSpace(SPACE_1, "A1", "x".repeat(201), null)),
                "content.spaces.0.location");
        assertPaths(withSpaces(RentalScope.WHOLE, new ObservedSpace(SPACE_1, "A1", "Tầng 2", PHONE)),
                "content.spaces.0.description");
        assertPaths(withSpaces(RentalScope.WHOLE, new ObservedSpace(SPACE_1, "A1", "Tầng 2", "x".repeat(501))),
                "content.spaces.0.description");
    }

    /** W16-9: WHOLE is exactly one space. */
    @Test
    void whole_withZeroOrTwoSpaces_isRefused() {
        assertPaths(withSpaces(RentalScope.WHOLE), "content.spaces");
        assertPaths(withSpaces(RentalScope.WHOLE, observedSpace(SPACE_1, "A1"), observedSpace(SPACE_2, "A2")),
                "content.spaces");
    }

    @Test
    void parts_withNoSpaceOr51_isRefused() {
        assertPaths(withSpaces(RentalScope.PARTS), "content.spaces");
        List<ObservedSpace> many = new ArrayList<>();
        for (int i = 1; i <= 51; i++) {
            many.add(observedSpace(space(i), "P" + i));
        }
        assertPaths(identification(RentalScope.PARTS, many), "content.spaces");
        assertPaths(identification(null, many), "content.spaces");
    }

    @Test
    void parts_fiftySpaces_areAccepted() {
        List<ObservedSpace> fifty = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            fifty.add(observedSpace(space(i), "P" + i));
        }
        IdentificationContent content = identification(RentalScope.PARTS, fifty);

        assertEquals(content, TemplateV1Rules.checkAndNormalise(content, TODAY));
    }

    @Test
    void scopeNotSet_spacesMayBeEmpty() {
        IdentificationContent content = identification(null, List.of());

        assertEquals(content, TemplateV1Rules.checkAndNormalise(content, TODAY));
    }

    @Test
    void spaces_missingListOrEntry_isRefused() {
        assertPaths(identification(RentalScope.WHOLE, null), "content.spaces");
        List<ObservedSpace> withNull = new ArrayList<>();
        withNull.add(null);
        assertPaths(identification(null, withNull), "content.spaces.0");
    }

    @Test
    void twoViolations_areBothReported() {
        IdentificationContent content = withSpaces(RentalScope.PARTS, new ObservedSpace("bad", "A1", PHONE, null),
                observedSpace(SPACE_2, "a1"));

        assertPaths(content, "content.spaces.0.spaceId", "content.spaces.0.location", "content.spaces.1.code");
    }

    /** Review Focus 1: Vietnamese text typed on a Mac arrives in NFD, with a trailing space; it is the same text. */
    @Test
    void checkAndNormalise_nfdTextAndTrailingZero_equalStored() {
        String nfdAddress = Normalizer.normalize("12 Bạch Đằng ", Normalizer.Form.NFD);
        String nfdReason = Normalizer.normalize("Căn hộ cho thuê dài hạn\t", Normalizer.Form.NFD);
        IdentificationContent typed = with(g -> new IdentificationContent(
                onSite(new ObservedLocation(nfdAddress, "48", "20194", null, null)), null, g.propertyType(),
                Field.value(new FitAssessment(Fit.IN_SCOPE, nfdReason)), g.rentalScope(),
                List.of(new ObservedSpace(SPACE_1, " A1", Normalizer.normalize("Tầng 2, phía đường", Normalizer.Form.NFD),
                        "  ")), null, null));

        assertEquals(identificationWhole(), TemplateV1Rules.checkAndNormalise(typed, TODAY));
    }

    @Test
    void violationMessages_neverQuoteTheValue() {
        ChecklistValidationException refused = assertThrows(ChecklistValidationException.class,
                () -> TemplateV1Rules.checkAndNormalise(with(g -> withFit(g, new FitAssessment(Fit.IN_SCOPE,
                        "Bí mật " + PHONE))), TODAY));

        for (Violation violation : refused.violations()) {
            assertEquals(false, violation.message().contains("Bí mật"), violation.message());
            assertEquals(false, violation.message().contains("0905"), violation.message());
        }
    }

    // ==================== helpers ====================

    private static IdentificationContent with(UnaryOperator<IdentificationContent> change) {
        return change.apply(identificationWhole());
    }

    private static IdentificationContent withSpaces(RentalScope scope, ObservedSpace... spaces) {
        return identification(scope, List.of(spaces));
    }

    private static IdentificationContent withType(IdentificationContent g, Field<PropertyTypeCode> type) {
        return new IdentificationContent(g.observedAddress(), g.differenceNote(), type, g.fit(), g.rentalScope(),
                g.spaces(), g.otherPartsNote(), g.generalNote());
    }

    private static IdentificationContent withFit(IdentificationContent g, FitAssessment fit) {
        return new IdentificationContent(g.observedAddress(), g.differenceNote(), g.propertyType(), Field.value(fit),
                g.rentalScope(), g.spaces(), g.otherPartsNote(), g.generalNote());
    }

    private static IdentificationContent withAddress(IdentificationContent g, SourcedField<ObservedLocation> address) {
        return new IdentificationContent(address, g.differenceNote(), g.propertyType(), g.fit(), g.rentalScope(),
                g.spaces(), g.otherPartsNote(), g.generalNote());
    }

    private static SourcedField<ObservedLocation> onSite(ObservedLocation location) {
        return SourcedField.of(location, ChecklistSource.OBSERVED_ON_SITE);
    }

    private static SourcedField<ObservedLocation> sourced(ChecklistSource source, LocalDate confirmedOn,
                                                          String receiver, String relay) {
        return new SourcedField<>(FieldState.VALUE, ADDRESS, null, source, confirmedOn, receiver, relay);
    }

    static void assertPaths(GroupContent content, String... expected) {
        ChecklistValidationException refused = assertThrows(ChecklistValidationException.class,
                () -> TemplateV1Rules.checkAndNormalise(content, TODAY));
        assertEquals(List.of(expected), paths(refused));
    }

    static List<String> paths(ChecklistValidationException refused) {
        return refused.violations().stream().map(Violation::path).toList();
    }
}
