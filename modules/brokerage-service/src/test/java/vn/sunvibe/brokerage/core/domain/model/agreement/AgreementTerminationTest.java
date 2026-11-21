package vn.sunvibe.brokerage.core.domain.model.agreement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import vn.sunvibe.brokerage.core.domain.exception.AgreementContentException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The termination record (brokerage-agreement BR-14, BR-17; E05-S8 spec S8-D5, plan E05S8-P5): each of the three types
 * with its own fields, the reported date from 2000-01-01 to today in Vietnam, the note trimmed under the character rule,
 * and no private value in a text.
 */
class AgreementTerminationTest {

    private static final Instant NOW = Instant.parse("2026-12-02T03:00:00Z");
    private static final LocalDate TODAY = LocalDate.parse("2026-12-02");
    private static final LocalDate REPORTED = LocalDate.parse("2026-12-01");
    private static final String NOTE = "The owner signed with a tenant found elsewhere.";

    @Test
    void of_eachTypeWithItsFields_accepted() {
        AgreementTermination withdrew = AgreementTermination.of(TerminationType.OWNER_WITHDREW, REPORTED,
                WithdrawalChannel.PHONE_CALL, null, "  " + NOTE + " ", TODAY, NOW);
        AgreementTermination let = AgreementTermination.of(TerminationType.UNIT_LET, REPORTED, null,
                IntroducedBySunvibe.UNKNOWN, null, TODAY, NOW);
        AgreementTermination sunvibe = AgreementTermination.of(TerminationType.SUNVIBE_TERMINATED, null, null, null,
                NOTE, TODAY, NOW);

        assertEquals(new AgreementTermination(TerminationType.OWNER_WITHDREW, REPORTED, WithdrawalChannel.PHONE_CALL,
                null, NOTE, NOW), withdrew);
        assertEquals(new AgreementTermination(TerminationType.UNIT_LET, REPORTED, null, IntroducedBySunvibe.UNKNOWN,
                null, NOW), let);
        assertEquals(new AgreementTermination(TerminationType.SUNVIBE_TERMINATED, null, null, null, NOTE, NOW), sunvibe);
        assertEquals(500, AgreementTermination.of(TerminationType.SUNVIBE_TERMINATED, null, null, null, "a".repeat(500),
                TODAY, NOW).noteOrNull().length());
    }

    @Test
    void constructor_fieldOfAnotherTypeOrMissingRequired_illegalArgument() {
        List<Executable> refused = List.of(
                () -> new AgreementTermination(null, REPORTED, WithdrawalChannel.EMAIL, null, null, NOW),
                () -> new AgreementTermination(TerminationType.OWNER_WITHDREW, REPORTED, WithdrawalChannel.EMAIL, null,
                        null, null),
                () -> new AgreementTermination(TerminationType.OWNER_WITHDREW, null, WithdrawalChannel.EMAIL, null,
                        null, NOW),
                () -> new AgreementTermination(TerminationType.OWNER_WITHDREW, REPORTED, null, null, null, NOW),
                () -> new AgreementTermination(TerminationType.OWNER_WITHDREW, REPORTED, WithdrawalChannel.EMAIL,
                        IntroducedBySunvibe.YES, null, NOW),
                () -> new AgreementTermination(TerminationType.UNIT_LET, null, null, IntroducedBySunvibe.NO, null, NOW),
                () -> new AgreementTermination(TerminationType.UNIT_LET, REPORTED, null, null, null, NOW),
                () -> new AgreementTermination(TerminationType.UNIT_LET, REPORTED, WithdrawalChannel.OTHER,
                        IntroducedBySunvibe.NO, null, NOW),
                () -> new AgreementTermination(TerminationType.SUNVIBE_TERMINATED, null, null, null, null, NOW),
                () -> new AgreementTermination(TerminationType.SUNVIBE_TERMINATED, REPORTED, null, null, NOTE, NOW),
                () -> new AgreementTermination(TerminationType.SUNVIBE_TERMINATED, null, WithdrawalChannel.EMAIL, null,
                        NOTE, NOW),
                () -> new AgreementTermination(TerminationType.SUNVIBE_TERMINATED, null, null,
                        IntroducedBySunvibe.YES, NOTE, NOW),
                () -> new AgreementTermination(TerminationType.UNIT_LET, REPORTED, null, IntroducedBySunvibe.NO,
                        "a".repeat(501), NOW),
                () -> new AgreementTermination(TerminationType.UNIT_LET, REPORTED, null, IntroducedBySunvibe.NO,
                        "A‮B", NOW));
        for (int index = 0; index < refused.size(); index++) {
            assertThrows(IllegalArgumentException.class, refused.get(index), "case " + index);
        }
    }

    @Test
    void of_fieldOfAnotherTypeOrMissingRequired_refusedOnTheField() {
        assertEquals("channel", refused(() -> AgreementTermination.of(TerminationType.UNIT_LET, REPORTED,
                WithdrawalChannel.EMAIL, IntroducedBySunvibe.NO, null, TODAY, NOW)));
        assertEquals("introducedBySunvibe", refused(() -> AgreementTermination.of(TerminationType.OWNER_WITHDREW,
                REPORTED, WithdrawalChannel.EMAIL, IntroducedBySunvibe.NO, null, TODAY, NOW)));
        assertEquals("reportedOn", refused(() -> AgreementTermination.of(TerminationType.SUNVIBE_TERMINATED, REPORTED,
                null, null, NOTE, TODAY, NOW)));
        assertEquals("reportedOn", refused(() -> AgreementTermination.of(TerminationType.OWNER_WITHDREW, null,
                WithdrawalChannel.EMAIL, null, null, TODAY, NOW)));
        assertEquals("channel", refused(() -> AgreementTermination.of(TerminationType.OWNER_WITHDREW, REPORTED, null,
                null, null, TODAY, NOW)));
        assertEquals("introducedBySunvibe", refused(() -> AgreementTermination.of(TerminationType.UNIT_LET, REPORTED,
                null, null, null, TODAY, NOW)));
    }

    @Test
    void of_reportedOnAfterTodayOrBefore2000_refusedOnReportedOn() {
        assertEquals("reportedOn", refused(() -> AgreementTermination.of(TerminationType.UNIT_LET, TODAY.plusDays(1),
                null, IntroducedBySunvibe.YES, null, TODAY, NOW)));
        assertEquals("reportedOn", refused(() -> AgreementTermination.of(TerminationType.OWNER_WITHDREW,
                LocalDate.parse("1999-12-31"), WithdrawalChannel.MESSAGE, null, null, TODAY, NOW)));

        assertEquals(LocalDate.parse("2000-01-01"), AgreementTermination.of(TerminationType.OWNER_WITHDREW,
                LocalDate.parse("2000-01-01"), WithdrawalChannel.MESSAGE, null, null, TODAY, NOW).reportedOnOrNull());
        assertEquals(TODAY, AgreementTermination.of(TerminationType.UNIT_LET, TODAY, null, IntroducedBySunvibe.YES, null,
                TODAY, NOW).reportedOnOrNull());
    }

    /** Review Focus 1: at 17:30 UTC on 2 December it is already 3 December in Vietnam. */
    @Test
    void of_reportedOnTodayInVietnamAtUtcEvening_accepted() {
        Instant evening = Instant.parse("2026-12-02T17:30:00Z");
        LocalDate todayInVietnam = AgreementVersion.todayInVietnam(evening);

        assertEquals(LocalDate.parse("2026-12-03"), todayInVietnam);
        assertEquals(LocalDate.parse("2026-12-03"), AgreementTermination.of(TerminationType.UNIT_LET,
                LocalDate.parse("2026-12-03"), null, IntroducedBySunvibe.NO, null, todayInVietnam, evening)
                .reportedOnOrNull());
        assertEquals("reportedOn", refused(() -> AgreementTermination.of(TerminationType.UNIT_LET,
                LocalDate.parse("2026-12-04"), null, IntroducedBySunvibe.NO, null, todayInVietnam, evening)));
    }

    @Test
    void of_noteOver500OrControlOrBidi_refusedOnNote() {
        for (String bad : List.of("a".repeat(501), " " + "a".repeat(501) + " ", "A\u0000B", "A\u001FB", "A‮B",
                "A⁦B")) {
            assertEquals("note", refused(() -> AgreementTermination.of(TerminationType.OWNER_WITHDREW, REPORTED,
                    WithdrawalChannel.IN_PERSON, null, bad, TODAY, NOW)));
            assertEquals("note", refused(() -> AgreementTermination.of(TerminationType.SUNVIBE_TERMINATED, null, null,
                    null, bad, TODAY, NOW)));
        }
    }

    @Test
    void of_sunvibeBlankNote_refusedOnNote() {
        for (String blank : new String[]{null, "", "   ", "\n\t"}) {
            assertEquals("note", refused(() -> AgreementTermination.of(TerminationType.SUNVIBE_TERMINATED, null, null,
                    null, blank, TODAY, NOW)), String.valueOf(blank));
        }
    }

    /** Review Focus 5: a note made only of spaces is stored as null, never as an empty text. */
    @Test
    void of_blankOptionalNote_storedAsNull() {
        for (String blank : new String[]{null, "", "   ", "\n\t"}) {
            assertNull(AgreementTermination.of(TerminationType.OWNER_WITHDREW, REPORTED, WithdrawalChannel.OTHER, null,
                    blank, TODAY, NOW).noteOrNull());
            assertNull(AgreementTermination.of(TerminationType.UNIT_LET, REPORTED, null, IntroducedBySunvibe.YES,
                    blank, TODAY, NOW).noteOrNull());
        }
    }

    /** brokerage-agreement BR-17: the note, the date and the channel stay out of every log line. */
    @Test
    void toString_hidesTheNote() {
        String text = new AgreementTermination(TerminationType.OWNER_WITHDREW, REPORTED, WithdrawalChannel.PHONE_CALL,
                null, NOTE, NOW).toString();

        assertFalse(text.contains(NOTE), text);
        assertFalse(text.contains("2026-12-01"), text);
        assertFalse(text.contains("PHONE_CALL"), text);
        assertTrue(text.startsWith("AgreementTermination"), text);
    }

    private static String refused(Executable call) {
        return assertThrows(AgreementContentException.class, call).field();
    }
}
