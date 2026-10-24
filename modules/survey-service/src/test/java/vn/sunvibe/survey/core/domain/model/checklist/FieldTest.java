package vn.sunvibe.survey.core.domain.model.checklist;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The field wrappers of template v1 (survey-checklist spec 4.6): plain carriers with the three states. */
class FieldTest {

    @Test
    void factories_setStateValueAndReason() {
        assertEquals(new Field<>(FieldState.UNKNOWN, null, null), Field.unknown());
        assertEquals(new Field<>(FieldState.VALUE, "Hẻm 3m", null), Field.value("Hẻm 3m"));
        assertEquals(new Field<>(FieldState.NOT_APPLICABLE, null, "Không có"), Field.notApplicable("Không có"));
        assertTrue(Field.unknown().isUnknown());
        assertTrue(Field.value(1).isValue());
        assertFalse(Field.notApplicable("x").isValue());
    }

    @Test
    void sourcedFactories_setTheSourceParts() {
        LocalDate on = LocalDate.parse("2026-10-20");

        assertEquals(new SourcedField<>(FieldState.VALUE, 3, null, ChecklistSource.OWNER_STATED, on, "u", "relay"),
                SourcedField.ownerStated(3, on, "u", "relay"));
        SourcedField<Integer> onSite = SourcedField.of(3, ChecklistSource.OBSERVED_ON_SITE);
        assertNull(onSite.confirmedOn());
        assertNull(onSite.receivedByUserId());
        assertTrue(SourcedField.unknown().isUnknown());
        assertNull(SourcedField.unknown().source());
    }

    /** survey-checklist spec 4.10: values never reach a log line. */
    @Test
    void toString_hidesTheValue() {
        assertFalse(Field.value("12 Bạch Đằng").toString().contains("Bạch"));
        assertFalse(SourcedField.of("12 Bạch Đằng", ChecklistSource.OWNER_FORM).toString().contains("Bạch"));
        assertFalse(new ObservedLocation("12 Bạch Đằng", "48", "20194", null, null).toString().contains("Bạch"));
        assertFalse(Money.vnd(12_345_678).toString().contains("12345678"));
        assertFalse(RentMoney.monthly(12_345_678).toString().contains("12345678"));
        assertFalse(new Deposit(DepositBasis.AMOUNT, null, Money.vnd(24_691_356)).toString().contains("24691356"));
        assertFalse(new ObservedPlaceNames("Đà Nẵng", "Hải Châu").toString().contains("Hải"));
    }

    @Test
    void groups_templateOrderAndPathNames() {
        assertEquals("identification", ChecklistGroup.IDENTIFICATION.pathName());
        assertEquals("terms", ChecklistGroup.TERMS.pathName());
        assertFalse(ChecklistGroup.EVIDENCE.isStored());
        assertEquals(7, ChecklistGroup.stored().size());
        assertEquals(ChecklistGroup.TERMS, ChecklistGroup.stored().get(6));
    }
}
