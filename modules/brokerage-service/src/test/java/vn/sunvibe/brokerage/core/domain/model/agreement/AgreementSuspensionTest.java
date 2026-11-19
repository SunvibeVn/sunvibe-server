package vn.sunvibe.brokerage.core.domain.model.agreement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import vn.sunvibe.brokerage.core.domain.exception.AgreementContentException;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One suspension record (brokerage-agreement BR-15, BR-17; E05-S8 spec S8-D6, plan E05S8-P5): who suspended it, when,
 * why, and once lifted who lifted it, when and why; the texts trimmed under the character rule and never in a text.
 */
class AgreementSuspensionTest {

    private static final String ID = "01JSUSPENSION0000000000001";
    private static final String AGREEMENT = "01JAGREEMENT00000000000001";
    private static final String MANAGER = "01JMANAGER0000000000000001";
    private static final String RELEASER = "01JRELEASER000000000000001";
    private static final Instant SUSPENDED_AT = Instant.parse("2026-12-02T03:00:00Z");
    private static final Instant RELEASED_AT = Instant.parse("2026-12-03T03:00:00Z");
    private static final String NOTE = "Tenant reported a different price.";
    private static final String REASON = "The price was checked with the owner.";

    @Test
    void open_noteTrimmed_openRecord() {
        AgreementSuspension opened = AgreementSuspension.open(ID, AGREEMENT, SuspensionReasonType.COMPLAINT,
                "  " + NOTE + "\n", MANAGER, SUSPENDED_AT);

        assertEquals(new AgreementSuspension(ID, AGREEMENT, SuspensionReasonType.COMPLAINT, NOTE, MANAGER, SUSPENDED_AT,
                null, null, null), opened);
        assertTrue(opened.isOpen());
        assertEquals(500, AgreementSuspension.open(ID, AGREEMENT, SuspensionReasonType.DISPUTE, "a".repeat(500), MANAGER,
                SUSPENDED_AT).noteOrNull().length());
    }

    /** Review Focus 5: a note made only of spaces is stored as null, never as an empty text. */
    @Test
    void open_blankNote_storedAsNull() {
        for (String blank : new String[]{null, "", "   ", "\n\t"}) {
            assertNull(AgreementSuspension.open(ID, AGREEMENT, SuspensionReasonType.NEEDS_CHECK, blank, MANAGER,
                    SUSPENDED_AT).noteOrNull(), String.valueOf(blank));
        }
    }

    @Test
    void open_noteOver500OrBidi_refusedOnNote() {
        for (String bad : List.of("a".repeat(501), "A\u0000B", "A\u007FB", "A‪B", "A‏B")) {
            assertEquals("note", refused(() -> AgreementSuspension.open(ID, AGREEMENT,
                    SuspensionReasonType.SUSPECTED_FRAUD, bad, MANAGER, SUSPENDED_AT)));
        }
    }

    @Test
    void constructor_partialRelease_refused() {
        List<Executable> partial = List.of(
                () -> new AgreementSuspension(ID, AGREEMENT, SuspensionReasonType.COMPLAINT, null, MANAGER,
                        SUSPENDED_AT, RELEASER, null, null),
                () -> new AgreementSuspension(ID, AGREEMENT, SuspensionReasonType.COMPLAINT, null, MANAGER,
                        SUSPENDED_AT, null, RELEASED_AT, null),
                () -> new AgreementSuspension(ID, AGREEMENT, SuspensionReasonType.COMPLAINT, null, MANAGER,
                        SUSPENDED_AT, null, null, REASON),
                () -> new AgreementSuspension(ID, AGREEMENT, SuspensionReasonType.COMPLAINT, null, MANAGER,
                        SUSPENDED_AT, RELEASER, RELEASED_AT, null),
                () -> new AgreementSuspension(ID, AGREEMENT, null, null, MANAGER, SUSPENDED_AT, null, null, null),
                () -> new AgreementSuspension(ID, AGREEMENT, SuspensionReasonType.COMPLAINT, null, null, SUSPENDED_AT,
                        null, null, null),
                () -> new AgreementSuspension(ID, AGREEMENT, SuspensionReasonType.COMPLAINT, null, MANAGER, null, null,
                        null, null),
                () -> new AgreementSuspension(ID, AGREEMENT, SuspensionReasonType.COMPLAINT, null, MANAGER,
                        SUSPENDED_AT, RELEASER, RELEASED_AT, "A‮B"));
        for (int index = 0; index < partial.size(); index++) {
            assertThrows(IllegalArgumentException.class, partial.get(index), "case " + index);
        }
    }

    @Test
    void constructor_releasedBeforeSuspended_refused() {
        assertThrows(IllegalArgumentException.class, () -> new AgreementSuspension(ID, AGREEMENT,
                SuspensionReasonType.COMPLAINT, null, MANAGER, SUSPENDED_AT, RELEASER, SUSPENDED_AT.minusNanos(1000),
                REASON));
        assertEquals(SUSPENDED_AT, new AgreementSuspension(ID, AGREEMENT, SuspensionReasonType.COMPLAINT, null, MANAGER,
                SUSPENDED_AT, RELEASER, SUSPENDED_AT, REASON).releasedAtOrNull());
    }

    @Test
    void released_closesWithWhoWhenReason() {
        AgreementSuspension opened = AgreementSuspension.open(ID, AGREEMENT, SuspensionReasonType.DISPUTE, NOTE, MANAGER,
                SUSPENDED_AT);

        AgreementSuspension closed = opened.released(RELEASER, RELEASED_AT, REASON);

        assertEquals(new AgreementSuspension(ID, AGREEMENT, SuspensionReasonType.DISPUTE, NOTE, MANAGER, SUSPENDED_AT,
                RELEASER, RELEASED_AT, REASON), closed);
        assertFalse(closed.isOpen());
        assertThrows(IllegalStateException.class, () -> closed.released(RELEASER, RELEASED_AT, REASON));
    }

    @Test
    void releaseReason_blankOrOver500_refusedOnReason() {
        for (String bad : new String[]{null, "", "   ", "a".repeat(501), "A\u0000B", "A⁩B"}) {
            assertEquals("reason", refused(() -> AgreementSuspension.releaseReason(bad)), String.valueOf(bad));
        }
        assertEquals(REASON, AgreementSuspension.releaseReason("  " + REASON + "  "));
        assertEquals(500, AgreementSuspension.releaseReason("a".repeat(500)).length());
    }

    /** brokerage-agreement BR-17: the note and the release reason stay out of every log line. */
    @Test
    void toString_hidesTexts() {
        String text = new AgreementSuspension(ID, AGREEMENT, SuspensionReasonType.COMPLAINT, NOTE, MANAGER, SUSPENDED_AT,
                RELEASER, RELEASED_AT, REASON).toString();

        assertFalse(text.contains(NOTE), text);
        assertFalse(text.contains(REASON), text);
        assertTrue(text.startsWith("AgreementSuspension"), text);
    }

    private static String refused(Executable call) {
        return assertThrows(AgreementContentException.class, call).field();
    }
}
