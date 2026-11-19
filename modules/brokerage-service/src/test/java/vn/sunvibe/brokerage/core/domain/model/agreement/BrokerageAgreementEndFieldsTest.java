package vn.sunvibe.brokerage.core.domain.model.agreement;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The agreement's end fields, stated one way only (E05-S8 spec 5.1, LD-1 domain): the end moment only in a final state,
 * the termination record only in TERMINATED; a stored final row without them loads with null, its status kept.
 */
class BrokerageAgreementEndFieldsTest {

    private static final Instant CREATED = Instant.parse("2026-11-01T03:00:00Z");
    private static final Instant ENDED = Instant.parse("2026-12-02T03:00:00Z");
    private static final AgreementPeriod PERIOD =
            new AgreementPeriod(LocalDate.parse("2027-01-01"), LocalDate.parse("2027-06-30"));
    private static final AgreementTermination SUNVIBE = new AgreementTermination(TerminationType.SUNVIBE_TERMINATED,
            null, null, null, "A dispute with the owner.", ENDED);

    @Test
    void load_endedAtOnUnfinishedState_refused() {
        for (AgreementStatus status : List.of(AgreementStatus.DRAFTING, AgreementStatus.AWAITING_CONFIRMATION,
                AgreementStatus.ACTIVE, AgreementStatus.SUSPENDED)) {
            assertThrows(IllegalStateException.class, () -> load(status, ENDED, null), status.name());
        }
    }

    @Test
    void load_terminationOnOtherThanTerminated_refused() {
        for (AgreementStatus status : AgreementStatus.values()) {
            if (status != AgreementStatus.TERMINATED) {
                assertThrows(IllegalStateException.class, () -> load(status, status.isUnfinished() ? null : ENDED,
                        SUNVIBE), status.name());
            }
        }
        assertEquals(SUNVIBE, load(AgreementStatus.TERMINATED, ENDED, SUNVIBE).getTerminationOrNull());
    }

    /** LD-1: no command wrote a final state before E05-S8, so such a row may lack its end; it loads with null. */
    @Test
    void load_finalStateWithoutEndFields_accepted() {
        for (AgreementStatus status : List.of(AgreementStatus.EXPIRED, AgreementStatus.TERMINATED,
                AgreementStatus.CANCELLED)) {
            BrokerageAgreement loaded = load(status, null, null);

            assertEquals(status, loaded.getStatus());
            assertNull(loaded.getEndedAtOrNull());
            assertNull(loaded.getTerminationOrNull());
            assertEquals(ENDED, load(status, ENDED, null).getEndedAtOrNull());
        }
    }

    @Test
    void load_keptThirteenArguments_noEndFields() {
        BrokerageAgreement loaded = BrokerageAgreement.load("A1", "U1", "P101", "P1", TransactionType.LONG_TERM_RENT,
                "O1", "L1", AgreementStatus.CANCELLED, "S1", CREATED, CREATED, 3L, List.of(cancelledVersion()));

        assertEquals(AgreementStatus.CANCELLED, loaded.getStatus());
        assertNull(loaded.getEndedAtOrNull());
        assertNull(loaded.getTerminationOrNull());
    }

    private static BrokerageAgreement load(AgreementStatus status, Instant endedAtOrNull,
                                           AgreementTermination terminationOrNull) {
        return BrokerageAgreement.load("A1", "U1", "P101", "P1", TransactionType.LONG_TERM_RENT, "O1", "L1", status,
                "S1", CREATED, CREATED, 3L, List.of(cancelledVersion()), endedAtOrNull, terminationOrNull);
    }

    private static AgreementVersion cancelledVersion() {
        return AgreementVersion.load("V1", 1, VersionStatus.CANCELLED, PERIOD, "L1", "I1", "S1", CREATED, CREATED);
    }
}
