package vn.sunvibe.brokerage.core.presentation.converter;

import org.junit.jupiter.api.Test;
import vn.sunvibe.brokerage.core.application.exception.AgreementRefusedException;
import vn.sunvibe.brokerage.core.application.exception.AgreementRefusedException.Entry;
import vn.sunvibe.brokerage.core.application.exception.AgreementRefusedException.Reason;
import vn.sunvibe.brokerage.core.application.usecase.AgreementHistoryQuery;
import vn.sunvibe.brokerage.core.application.usecase.AgreementListQuery;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementStatus;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The query parameters of the agreement list and the history to their query records (E05-S7 spec S7-D13 (2), EH-1;
 * Review Focus 2): an unknown or inexact state code is refused on {@code status} with the seven codes, a bound outside
 * on its own parameter without allowed values, the first refused parameter in the order status, page, size; the edges
 * are read.
 */
class AgreementRequestConverterQueryTest {

    private static final List<String> SEVEN = List.of("DRAFTING", "AWAITING_CONFIRMATION", "ACTIVE", "SUSPENDED",
            "EXPIRED", "TERMINATED", "CANCELLED");
    private static final String AGREEMENT = "01JAGRE0000000000000000001";

    private final AgreementRequestConverter converter = new AgreementRequestConverter();

    @Test
    void toListQuery_unknownStatus_refusedWithTheSevenCodes() {
        AgreementRefusedException refused = assertThrows(AgreementRefusedException.class,
                () -> converter.toListQuery(List.of("ACTIVE", "ENDED"), false, 0, 20));

        assertEquals(Reason.VALIDATION_FAILED, refused.reason());
        assertEquals(List.of(new Entry("status", SEVEN)), refused.entries());
        assertEquals(SEVEN, Arrays.stream(AgreementStatus.values()).map(Enum::name).toList());
    }

    /** Review Focus 2: a code in another case, with a space, or empty is not a code. */
    @Test
    void toListQuery_codeNotExact_refusedWithTheSevenCodes() {
        for (String inexact : List.of("active", " ACTIVE", "")) {
            AgreementRefusedException refused = assertThrows(AgreementRefusedException.class,
                    () -> converter.toListQuery(List.of(inexact), false, 0, 20), inexact);

            assertEquals(List.of(new Entry("status", SEVEN)), refused.entries(), inexact);
        }
    }

    @Test
    void toListQuery_boundsOutside_refusedOnTheirParameter() {
        assertEquals(List.of(new Entry("page", null)), refusal(() -> converter.toListQuery(null, false, -1, 20)));
        assertEquals(List.of(new Entry("page", null)), refusal(() -> converter.toListQuery(null, false, 10000, 20)));
        assertEquals(List.of(new Entry("size", null)), refusal(() -> converter.toListQuery(null, false, 0, 0)));
        assertEquals(List.of(new Entry("size", null)), refusal(() -> converter.toListQuery(null, false, 0, 51)));
    }

    @Test
    void toListQuery_edges_read() {
        assertEquals(new AgreementListQuery(Set.of(), false, 0, 1), converter.toListQuery(null, false, 0, 1));
        assertEquals(new AgreementListQuery(Set.of(), true, 9999, 50), converter.toListQuery(null, true, 9999, 50));
    }

    @Test
    void toListQuery_repeatedStatus_keptOnce() {
        AgreementListQuery query = converter.toListQuery(List.of("ACTIVE", "ACTIVE", "SUSPENDED"), false, 0, 20);

        assertEquals(Set.of(AgreementStatus.ACTIVE, AgreementStatus.SUSPENDED), query.statuses());
    }

    @Test
    void toListQuery_noStatus_everyState() {
        assertEquals(Set.of(), converter.toListQuery(null, false, 0, 20).statuses());
        assertEquals(Set.of(), converter.toListQuery(List.of(), false, 0, 20).statuses());
    }

    @Test
    void toListQuery_firstRefusal_statusThenPageThenSize() {
        assertEquals(List.of(new Entry("status", SEVEN)), refusal(() -> converter.toListQuery(List.of("X"), false, -1,
                0)));
        assertEquals(List.of(new Entry("page", null)), refusal(() -> converter.toListQuery(null, false, -1, 0)));
    }

    @Test
    void toHistoryQuery_bounds_refusedOrRead() {
        assertEquals(List.of(new Entry("size", null)), refusal(() -> converter.toHistoryQuery(AGREEMENT, 0, 0)));
        assertEquals(List.of(new Entry("size", null)), refusal(() -> converter.toHistoryQuery(AGREEMENT, 0, 101)));
        assertEquals(List.of(new Entry("page", null)), refusal(() -> converter.toHistoryQuery(AGREEMENT, -1, 50)));
        assertEquals(List.of(new Entry("page", null)), refusal(() -> converter.toHistoryQuery(AGREEMENT, 10000, 101)));
        assertEquals(new AgreementHistoryQuery(AGREEMENT, 0, 1), converter.toHistoryQuery(AGREEMENT, 0, 1));
        assertEquals(new AgreementHistoryQuery(AGREEMENT, 9999, 100), converter.toHistoryQuery(AGREEMENT, 9999, 100));
        assertEquals(new AgreementHistoryQuery("not-an-id", 0, 50), converter.toHistoryQuery("not-an-id", 0, 50));
    }

    private static List<Entry> refusal(org.junit.jupiter.api.function.Executable call) {
        AgreementRefusedException refused = assertThrows(AgreementRefusedException.class, call);
        assertEquals(Reason.VALIDATION_FAILED, refused.reason());
        return refused.entries();
    }
}
