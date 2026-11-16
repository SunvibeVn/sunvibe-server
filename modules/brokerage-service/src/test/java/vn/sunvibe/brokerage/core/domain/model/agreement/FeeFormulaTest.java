package vn.sunvibe.brokerage.core.domain.model.agreement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import vn.sunvibe.brokerage.core.domain.exception.AgreementContentException;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static vn.sunvibe.brokerage.core.domain.model.agreement.FeeFormulaType.FIXED_AMOUNT;
import static vn.sunvibe.brokerage.core.domain.model.agreement.FeeFormulaType.FREE;
import static vn.sunvibe.brokerage.core.domain.model.agreement.FeeFormulaType.NOT_CHARGED;
import static vn.sunvibe.brokerage.core.domain.model.agreement.FeeFormulaType.PERCENT_OF_RENT;

/**
 * How a brokerage fee is set (brokerage-agreement BR-7, E-13; E05-S4 spec S4-D3, FP-2): a percentage of one month's rent
 * in 1 to 5 tiers by lease term, the first from 1 month and the starts strictly increasing; a whole positive VND amount;
 * or free of charge with a plain reason; for the renewal, no charge. Each kind holds nothing of another kind.
 */
class FeeFormulaTest {

    private static final long MAX = 999_999_999_999_999L;

    @Test
    void percentOfRent_tiers_boundsAndOrder() {
        assertDoesNotThrow(() -> FeeFormula.of(PERCENT_OF_RENT, tiers(1, 1), null, null));
        assertDoesNotThrow(() -> FeeFormula.of(PERCENT_OF_RENT, tiers(1, 300), null, null));
        assertDoesNotThrow(() -> FeeFormula.of(PERCENT_OF_RENT, tiers(1, 50, 120, 100), null, null));
        FeeFormula five = FeeFormula.of(PERCENT_OF_RENT, tiers(1, 50, 12, 100, 24, 150, 36, 200, 120, 300), null, null);
        assertEquals(List.of(1, 12, 24, 36, 120), five.tiers().stream().map(FeeTier::fromMonths).toList());
        assertEquals(List.of(50, 100, 150, 200, 300), five.tiers().stream().map(FeeTier::percent).toList());

        // a tier's own bounds, placed under tiers[i]. by whoever builds the list (the request converter)
        assertEquals("percent", refused(() -> new FeeTier(1, 0)));
        assertEquals("percent", refused(() -> new FeeTier(1, 301)));
        assertEquals("fromMonths", refused(() -> new FeeTier(0, 50)));
        assertEquals("fromMonths", refused(() -> new FeeTier(121, 50)));

        assertEquals("tiers", refused(() -> FeeFormula.of(PERCENT_OF_RENT,
                tiers(1, 50, 12, 60, 24, 70, 36, 80, 48, 90, 60, 100), null, null)));
        assertEquals("tiers[0].fromMonths", refused(() -> FeeFormula.of(PERCENT_OF_RENT, tiers(2, 50), null, null)));
        assertEquals("tiers[2].fromMonths", refused(() -> FeeFormula.of(PERCENT_OF_RENT, tiers(1, 50, 12, 60, 12, 70),
                null, null)));
        assertEquals("tiers[2].fromMonths", refused(() -> FeeFormula.of(PERCENT_OF_RENT, tiers(1, 50, 12, 60, 6, 70),
                null, null)));
        // no ordering of the percentages: the brief orders the terms only
        assertDoesNotThrow(() -> FeeFormula.of(PERCENT_OF_RENT, tiers(1, 100, 12, 50), null, null));
    }

    @Test
    void percentOfRent_noTierOrOtherParts_refusedOnTheField() {
        assertEquals("tiers", refused(() -> FeeFormula.of(PERCENT_OF_RENT, null, null, null)));
        assertEquals("tiers", refused(() -> FeeFormula.of(PERCENT_OF_RENT, List.of(), null, null)));
        List<FeeTier> withNull = new ArrayList<>();
        withNull.add(null);
        assertEquals("tiers", refused(() -> FeeFormula.of(PERCENT_OF_RENT, withNull, null, null)));
        assertEquals("amount", refused(() -> FeeFormula.of(PERCENT_OF_RENT, tiers(1, 50), 1_000L, null)));
        assertEquals("freeReason", refused(() -> FeeFormula.of(PERCENT_OF_RENT, tiers(1, 50), null, "Reason")));
        assertEquals("type", refused(() -> FeeFormula.of(null, tiers(1, 50), null, null)));
    }

    @Test
    void fixedAmount_bounds_andNoTierOrReason() {
        assertEquals(1L, FeeFormula.of(FIXED_AMOUNT, null, 1L, null).amountOrNull());
        assertEquals(MAX, FeeFormula.of(FIXED_AMOUNT, null, MAX, null).amountOrNull());
        assertEquals(List.of(), FeeFormula.of(FIXED_AMOUNT, List.of(), 2_000_000L, null).tiers());

        assertEquals("amount", refused(() -> FeeFormula.of(FIXED_AMOUNT, null, 0L, null)));
        assertEquals("amount", refused(() -> FeeFormula.of(FIXED_AMOUNT, null, MAX + 1, null)));
        assertEquals("amount", refused(() -> FeeFormula.of(FIXED_AMOUNT, null, null, null)));
        assertEquals("tiers", refused(() -> FeeFormula.of(FIXED_AMOUNT, tiers(1, 50), 2_000_000L, null)));
        assertEquals("freeReason", refused(() -> FeeFormula.of(FIXED_AMOUNT, null, 2_000_000L, "Reason")));
        assertEquals("amount", refused(() -> FeeFormula.of(NOT_CHARGED, null, 2_000_000L, null)));
    }

    @Test
    void free_reason_trimmedBoundedAndPlain() {
        assertEquals("First unit with Sunvibe",
                FeeFormula.of(FREE, null, null, "  First unit with Sunvibe  ").freeReasonOrNull());
        assertEquals("Lần đầu hợp tác", FeeFormula.of(FREE, null, null, "Lần đầu hợp tác").freeReasonOrNull());
        String five = "a".repeat(500);
        assertEquals(five, FeeFormula.of(FREE, null, null, five).freeReasonOrNull());
        assertEquals(five, FeeFormula.of(FREE, null, null, "  " + five + "  ").freeReasonOrNull());

        List<String> refusedReasons = List.of("", "   ", "a".repeat(501), "Fir\u0000st", "Fir\u007Fst", "Fir\u0085st",
                "Fir\u009Fst", "Fir\u001Fst", "Fir\nst", "Fir‎st", "Fir‏st", "Fir‪st", "Fir‮st",
                "Fir⁦st", "Fir⁩st");
        for (String reason : refusedReasons) {
            assertEquals("freeReason", refused(() -> FeeFormula.of(FREE, null, null, reason)), reason);
        }
        assertEquals("freeReason", refused(() -> FeeFormula.of(FREE, null, null, null)));
        assertEquals("amount", refused(() -> FeeFormula.of(FREE, null, 1_000L, "Reason")));
        assertEquals("tiers", refused(() -> FeeFormula.of(FREE, tiers(1, 50), null, "Reason")));
    }

    /** Review Focus 1: Bean Validation, String.length() and the UI count UTF-16 units; an emoji is two. */
    @Test
    void free_reasonWithEmojiAtTheBound_countsUtf16Units() {
        String emoji = "😀";
        String fiveHundredUnits = "a".repeat(498) + emoji;
        String fiveHundredOneUnits = "a".repeat(499) + emoji;

        assertEquals(500, fiveHundredUnits.length());
        assertEquals(fiveHundredUnits, FeeFormula.of(FREE, null, null, fiveHundredUnits).freeReasonOrNull());
        assertEquals("freeReason", refused(() -> FeeFormula.of(FREE, null, null, fiveHundredOneUnits)));
    }

    @Test
    void notCharged_holdsNothing() {
        FeeFormula none = FeeFormula.of(NOT_CHARGED, null, null, null);
        assertEquals(List.of(), none.tiers());

        assertEquals("tiers", refused(() -> FeeFormula.of(NOT_CHARGED, tiers(1, 50), null, null)));
        assertEquals("amount", refused(() -> FeeFormula.of(NOT_CHARGED, null, 1_000L, null)));
        assertEquals("freeReason", refused(() -> FeeFormula.of(NOT_CHARGED, null, null, "Reason")));
    }

    @Test
    void tiers_copiedAndUnmodifiable() {
        List<FeeTier> source = new ArrayList<>(tiers(1, 50));
        FeeFormula formula = FeeFormula.of(PERCENT_OF_RENT, source, null, null);
        source.add(new FeeTier(12, 100));

        assertEquals(1, formula.tiers().size());
        assertThrows(UnsupportedOperationException.class, () -> formula.tiers().add(new FeeTier(24, 10)));
    }

    /** brokerage-agreement BR-17 (SD-14): the fee policy is private, so never an amount, a percentage or a reason. */
    @Test
    void toString_holdsNoAmountPercentOrReason() {
        List<String> texts = List.of(FeeFormula.of(FIXED_AMOUNT, null, 2_000_000L, null).toString(),
                FeeFormula.of(PERCENT_OF_RENT, tiers(1, 75), null, null).toString(),
                FeeFormula.of(FREE, null, null, "Secret reason").toString(), new FeeTier(1, 75).toString());

        for (String text : texts) {
            assertFalse(text.contains("2000000"), text);
            assertFalse(text.contains("75"), text);
            assertFalse(text.contains("Secret"), text);
        }
    }

    /** Tiers from pairs of (fromMonths, percent). */
    static List<FeeTier> tiers(int... pairs) {
        List<FeeTier> tiers = new ArrayList<>();
        for (int index = 0; index < pairs.length; index += 2) {
            tiers.add(new FeeTier(pairs[index], pairs[index + 1]));
        }
        return tiers;
    }

    private static String refused(Executable call) {
        return assertThrows(AgreementContentException.class, call).field();
    }
}
