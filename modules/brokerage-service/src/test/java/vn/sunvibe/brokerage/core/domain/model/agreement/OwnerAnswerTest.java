package vn.sunvibe.brokerage.core.domain.model.agreement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import vn.sunvibe.brokerage.core.domain.exception.AgreementContentException;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The owner's answer to a sent version (brokerage-agreement US-2 AC-2, AC-3; BR-10, BR-17; E05-S5 spec S5-D7, S5-D11): a
 * confirmation holds the statement version and no reason; a rejection holds an optional private reason, trimmed, under
 * the free-text rule of the fee policy, and no statement version.
 */
class OwnerAnswerTest {

    private static final Instant AT = Instant.parse("2026-11-24T03:00:00.123456Z");
    private static final String STATEMENT = AgreementStatement.VERSION;

    @Test
    void confirmed_holdsStatementNoReason() {
        OwnerAnswer answer = OwnerAnswer.confirmed("O1", STATEMENT, AT);

        assertEquals(new OwnerAnswer(OwnerAnswerType.CONFIRMED, "O1", AT, STATEMENT, null), answer);
    }

    @Test
    void rejected_reasonTrimmedOrNone() {
        assertEquals(new OwnerAnswer(OwnerAnswerType.REJECTED, "O1", AT, null, "The rent is too low."),
                OwnerAnswer.rejected("O1", "  The rent is too low.  ", AT));

        for (String none : Arrays.asList(null, "", "   ")) {
            assertEquals(new OwnerAnswer(OwnerAnswerType.REJECTED, "O1", AT, null, null),
                    OwnerAnswer.rejected("O1", none, AT), String.valueOf(none));
        }
    }

    @Test
    void rejected_reasonBoundsAndHiddenCharacters_refused() {
        String five = "a".repeat(500);
        assertEquals(five, OwnerAnswer.rejected("O1", five, AT).rejectionReasonOrNull());

        for (String reason : List.of("a".repeat(501), "Too\u0000low", "Too\u009Flow", "Too‮low", "Too⁩low")) {
            assertEquals("reason", refused(() -> OwnerAnswer.rejected("O1", reason, AT)), reason);
        }
    }

    /** Review Focus 4: the server and the UI count UTF-16 units, so an emoji is two. */
    @Test
    void rejected_reasonWithEmojiAtTheBound_countsUtf16Units() {
        String emoji = "😀";
        String fiveHundredUnits = "a".repeat(498) + emoji;
        String fiveHundredOneUnits = "a".repeat(499) + emoji;

        assertEquals(500, fiveHundredUnits.length());
        assertEquals(fiveHundredUnits, OwnerAnswer.rejected("O1", fiveHundredUnits, AT).rejectionReasonOrNull());
        assertEquals("reason", refused(() -> OwnerAnswer.rejected("O1", fiveHundredOneUnits, AT)));
    }

    /** brokerage-agreement E05-S5 spec S5-D11: a stored answer outside these combinations is a data fault. */
    @Test
    void load_brokenCombination_refused() {
        List<Executable> broken = List.of(
                () -> new OwnerAnswer(OwnerAnswerType.CONFIRMED, "O1", AT, null, null),
                () -> new OwnerAnswer(OwnerAnswerType.CONFIRMED, "O1", AT, STATEMENT, "A reason"),
                () -> new OwnerAnswer(OwnerAnswerType.REJECTED, "O1", AT, STATEMENT, null),
                () -> new OwnerAnswer(null, "O1", AT, STATEMENT, null),
                () -> new OwnerAnswer(OwnerAnswerType.REJECTED, null, AT, null, null),
                () -> new OwnerAnswer(OwnerAnswerType.REJECTED, "O1", null, null, null));

        for (int index = 0; index < broken.size(); index++) {
            assertThrows(IllegalArgumentException.class, broken.get(index), "case " + index);
        }
        assertEquals("A reason", new OwnerAnswer(OwnerAnswerType.REJECTED, "O1", AT, null, "A reason")
                .rejectionReasonOrNull());
    }

    /** brokerage-agreement BR-17: the reason, the account and the statement version stay out of every log line. */
    @Test
    void toString_holdsNoReasonUserOrStatement() {
        List<String> texts = List.of(OwnerAnswer.confirmed("01JOWNER", STATEMENT, AT).toString(),
                OwnerAnswer.rejected("01JOWNER", "Secret reason", AT).toString(),
                OwnerAnswer.rejected("01JOWNER", null, AT).toString());

        for (String text : texts) {
            assertFalse(text.contains("Secret"), text);
            assertFalse(text.contains("01JOWNER"), text);
            assertFalse(text.contains(STATEMENT), text);
        }
        assertEquals("OwnerAnswer[CONFIRMED]", texts.get(0));
        assertEquals("OwnerAnswer[REJECTED, reason]", texts.get(1));
        assertEquals("OwnerAnswer[REJECTED]", texts.get(2));
    }

    @Test
    void type_of_storedCodes() {
        assertEquals(OwnerAnswerType.CONFIRMED, OwnerAnswerType.of("CONFIRMED").orElseThrow());
        assertEquals(OwnerAnswerType.REJECTED, OwnerAnswerType.of("REJECTED").orElseThrow());
        assertNull(OwnerAnswerType.of("confirmed").orElse(null));
        assertNull(OwnerAnswerType.of(null).orElse(null));
    }

    private static String refused(Executable call) {
        return assertThrows(AgreementContentException.class, call).field();
    }
}
