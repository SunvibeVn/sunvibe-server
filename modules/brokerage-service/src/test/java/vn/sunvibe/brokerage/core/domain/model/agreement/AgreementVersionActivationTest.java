package vn.sunvibe.brokerage.core.domain.model.agreement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The time rules of a version awaiting activation and of an activated one (brokerage-agreement US-3 AC-4, AC-5; BR-5,
 * BR-6, BR-11; E05-S6 spec S6-D5, S6-D6, S6-D14): 14 days from the owner's confirmation to activate it, its start and
 * end as instants of Vietnam's calendar, and the combinations of the activation record. Boundaries with a fixed
 * {@code now}.
 */
class AgreementVersionActivationTest {

    private static final Instant CREATED = Instant.parse("2026-11-20T03:15:00.123456Z");
    private static final Instant SENT = Instant.parse("2026-11-22T08:30:00.654321Z");
    private static final Instant CONFIRMED_AT = SENT.plus(Duration.ofHours(5));
    private static final Instant ACTIVATE_BY = CONFIRMED_AT.plus(Duration.ofDays(14));
    private static final AgreementPeriod PERIOD =
            new AgreementPeriod(LocalDate.parse("2027-01-01"), LocalDate.parse("2027-12-31"));
    private static final PhotoConsent CONSENT = new PhotoConsent(true, false);
    private static final VersionTerms TERMS = new VersionTerms(8_000_000L,
            new TermsDeposit(DepositBasis.MONTHS, 2, null), 12, 1, List.of());
    private static final BrokerageFee FEE = new BrokerageFee(FeeFormula.of(FeeFormulaType.FIXED_AMOUNT, null,
            2_000_000L, null), 7, FeeFormula.of(FeeFormulaType.NOT_CHARGED, null, null, null),
            new EarlyLeaseEndRefund(EarlyLeaseEndType.NO_REFUND, null, null), 90);
    private static final OwnerAnswer CONFIRMED = OwnerAnswer.confirmed("O1", AgreementStatement.VERSION, CONFIRMED_AT);
    private static final OwnerAnswer REJECTED = OwnerAnswer.rejected("O1", null, CONFIRMED_AT);
    private static final SignedDocument DOCUMENT = new SignedDocument("HDMG-1", LocalDate.parse("2026-11-25"), "A", "B");
    private static final ActivationDecision ACTIVATION = ActivationDecision.activated("M1", CONFIRMED_AT.plusSeconds(60),
            ActivationStatement.VERSION, DOCUMENT);
    private static final ActivationDecision REFUSAL = ActivationDecision.refused("M1", CONFIRMED_AT.plusSeconds(60),
            ActivationRefusalType.DOCUMENT_DIFFERS, null);

    @Test
    void activateBy_confirmedAnswerPlusFourteenDays() {
        assertEquals(Duration.ofDays(14), AgreementVersion.ACTIVATION_WINDOW);
        for (VersionStatus status : List.of(VersionStatus.OWNER_CONFIRMED, VersionStatus.LAPSED,
                VersionStatus.ACTIVATION_REFUSED, VersionStatus.ACTIVATED, VersionStatus.EFFECTIVE)) {
            assertEquals(ACTIVATE_BY, version(status, CONFIRMED, null).activateByOrNull(), status.name());
        }
        assertEquals(Instant.parse("2026-12-06T13:30:00.654321Z"),
                version(VersionStatus.OWNER_CONFIRMED, CONFIRMED, null).activateByOrNull());

        assertNull(version(VersionStatus.OWNER_REJECTED, REJECTED, null).activateByOrNull());
        assertNull(version(VersionStatus.LAPSED, null, null).activateByOrNull());
        assertNull(version(VersionStatus.OWNER_CONFIRMED, null, null).activateByOrNull());
        assertNull(version(VersionStatus.AWAITING_OWNER, null, null).activateByOrNull());
    }

    /** E05-S6 spec S6-D5 (LP-2): the deadline of whichever lapse applies to the version, for its history row. */
    @Test
    void lapseDeadline_perState_answerByActivateByOrNull() {
        assertEquals(SENT.plus(Duration.ofDays(7)), version(VersionStatus.AWAITING_OWNER, null, null)
                .lapseDeadlineOrNull());
        assertEquals(ACTIVATE_BY, version(VersionStatus.OWNER_CONFIRMED, CONFIRMED, null).lapseDeadlineOrNull());
        assertNull(AgreementVersion.load("V1", 1, VersionStatus.DRAFT, PERIOD, "L1", "I1", "S1", CREATED, CREATED)
                .lapseDeadlineOrNull());
        assertNull(version(VersionStatus.EFFECTIVE, CONFIRMED, ACTIVATION).lapseDeadlineOrNull());
        assertNull(version(VersionStatus.OWNER_CONFIRMED, null, null).lapseDeadlineOrNull());
    }

    /** LP-1: activatable at C + 14 days − 1 µs, not at C + 14 days; the lapse is due from then on. */
    @Test
    void activatableAt_justBeforeAndAtActivateBy() {
        AgreementVersion confirmed = version(VersionStatus.OWNER_CONFIRMED, CONFIRMED, null);
        Instant justBefore = ACTIVATE_BY.minus(1, ChronoUnit.MICROS);

        assertTrue(confirmed.activatableAt(CONFIRMED_AT));
        assertTrue(confirmed.activatableAt(justBefore));
        assertFalse(confirmed.activatableAt(ACTIVATE_BY));
        assertFalse(confirmed.activationLapseDueAt(justBefore));
        assertTrue(confirmed.activationLapseDueAt(ACTIVATE_BY));
        assertTrue(confirmed.activationLapseDueAt(ACTIVATE_BY.plusSeconds(1)));

        AgreementVersion withoutAnswer = version(VersionStatus.OWNER_CONFIRMED, null, null);
        assertFalse(withoutAnswer.activatableAt(CONFIRMED_AT));
        assertFalse(withoutAnswer.activationLapseDueAt(ACTIVATE_BY.plus(Duration.ofDays(400))));
        assertFalse(version(VersionStatus.LAPSED, CONFIRMED, null).activatableAt(CONFIRMED_AT));
        assertFalse(version(VersionStatus.LAPSED, CONFIRMED, null).activationLapseDueAt(ACTIVATE_BY));
    }

    /** brokerage-agreement BR-6: the start of the start date and the start of the day after the end date, in Vietnam. */
    @Test
    void startsAtEndsAt_vietnamDayBounds() {
        AgreementVersion version = version(VersionStatus.OWNER_CONFIRMED, CONFIRMED, null);

        assertEquals(Instant.parse("2026-12-31T17:00:00Z"), version.startsAt());
        assertEquals(Instant.parse("2027-12-31T17:00:00Z"), version.endsAt());
    }

    /** E05-S6 spec S6-D14: a record only with a confirmed answer, an activation only in effect, a refusal only refused. */
    @Test
    void constructor_recordOnWrongStateOrWithoutConfirmation_refused() {
        List<Executable> broken = List.of(
                () -> version(VersionStatus.EFFECTIVE, null, ACTIVATION),
                () -> version(VersionStatus.OWNER_REJECTED, REJECTED, REFUSAL),
                () -> version(VersionStatus.OWNER_CONFIRMED, CONFIRMED, ACTIVATION),
                () -> version(VersionStatus.LAPSED, CONFIRMED, ACTIVATION),
                () -> version(VersionStatus.ACTIVATION_REFUSED, CONFIRMED, ACTIVATION),
                () -> version(VersionStatus.EFFECTIVE, CONFIRMED, REFUSAL),
                () -> version(VersionStatus.OWNER_CONFIRMED, CONFIRMED, REFUSAL));
        for (int index = 0; index < broken.size(); index++) {
            assertThrows(IllegalArgumentException.class, broken.get(index), "case " + index);
        }

        for (VersionStatus status : List.of(VersionStatus.ACTIVATED, VersionStatus.EFFECTIVE, VersionStatus.SUPERSEDED,
                VersionStatus.CANCELLED)) {
            assertDoesNotThrow(() -> version(status, CONFIRMED, ACTIVATION), status.name());
        }
        assertEquals(REFUSAL, version(VersionStatus.ACTIVATION_REFUSED, CONFIRMED, REFUSAL).getActivationOrNull());
        assertNull(version(VersionStatus.ACTIVATED, null, null).getActivationOrNull());
        assertNull(version(VersionStatus.ACTIVATION_REFUSED, null, null).getActivationOrNull());
    }

    private static AgreementVersion version(VersionStatus status, OwnerAnswer answerOrNull,
                                            ActivationDecision activationOrNull) {
        return AgreementVersion.load("V1", 1, status, PERIOD, "L1", "I1", "S1", CREATED, CREATED, CONSENT, TERMS, FEE,
                SENT, answerOrNull, activationOrNull);
    }
}
