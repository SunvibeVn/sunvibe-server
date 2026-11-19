package vn.sunvibe.brokerage.core.domain.model.agreement;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The expiry at every read and the time order of the due transitions (brokerage-agreement BR-5, BR-6, US-5 AC-1;
 * E05-S8 spec S8-D2, S8-D3, EX-1, EX-3, EX-6; plan E05S8-P3). The period runs from 2027-01-01 to 2027-06-30 in Vietnam:
 * it starts at 2026-12-31T17:00Z and ends at 2027-06-30T17:00Z.
 */
class BrokerageAgreementExpiryTest {

    private static final AgreementPeriod PERIOD =
            new AgreementPeriod(LocalDate.parse("2027-01-01"), LocalDate.parse("2027-06-30"));
    private static final Instant STARTS_AT = Instant.parse("2026-12-31T17:00:00Z");
    private static final Instant ENDS_AT = Instant.parse("2027-06-30T17:00:00Z");
    private static final Instant CREATED = Instant.parse("2026-11-01T03:00:00Z");
    private static final Instant SENT = Instant.parse("2026-11-20T03:00:00Z");
    private static final Instant CONFIRMED_AT = Instant.parse("2026-11-21T03:00:00Z");
    private static final Instant ACTIVATED_AT = Instant.parse("2026-11-22T03:00:00Z");
    private static final PhotoConsent CONSENT = new PhotoConsent(true, false);
    private static final VersionTerms TERMS = new VersionTerms(8_000_000L,
            new TermsDeposit(DepositBasis.MONTHS, 2, null), 12, 1, List.of());
    private static final BrokerageFee FEE = new BrokerageFee(FeeFormula.of(FeeFormulaType.FIXED_AMOUNT, null,
            2_000_000L, null), 7, FeeFormula.of(FeeFormulaType.NOT_CHARGED, null, null, null),
            new EarlyLeaseEndRefund(EarlyLeaseEndType.NO_REFUND, null, null), 90);
    private static final OwnerAnswer CONFIRMED = OwnerAnswer.confirmed("O1", AgreementStatement.VERSION, CONFIRMED_AT);
    private static final ActivationDecision ACTIVATION = ActivationDecision.activated("M1", ACTIVATED_AT,
            ActivationStatement.VERSION, new SignedDocument("HDMG-1", LocalDate.parse("2026-11-21"), "A", "B"));

    /** EX-1: one microsecond before the end the agreement is still in effect, and nothing is due. */
    @Test
    void effectiveAt_activeOneMicroBeforeEndsAt_stillActive() {
        BrokerageAgreement active = agreement(AgreementStatus.ACTIVE, List.of(effective(1)));

        BrokerageAgreement read = active.effectiveAt(ENDS_AT.minusNanos(1_000));

        assertSame(active, read);
        assertEquals(AgreementStatus.ACTIVE, read.getStatus());
        assertNull(read.getEndedAtOrNull());
    }

    /** EX-1, A-2: from the start of the day after the end date in Vietnam the agreement reads EXPIRED. */
    @Test
    void effectiveAt_activeAtEndsAt_expiredWithEndedAtEndsAt() {
        BrokerageAgreement active = agreement(AgreementStatus.ACTIVE, List.of(effective(1)));
        assertEquals(ENDS_AT, active.getVersions().getFirst().endsAt());

        BrokerageAgreement read = active.effectiveAt(ENDS_AT);

        assertEquals(AgreementStatus.EXPIRED, read.getStatus());
        assertEquals(ENDS_AT, read.getEndedAtOrNull());
        assertNull(read.getTerminationOrNull());
        assertEquals(VersionStatus.EFFECTIVE, read.getVersions().getFirst().getStatus());
        assertEquals(7L, read.getRowVersionOrNull());
    }

    /** EX-6: a suspended agreement expires the same way. */
    @Test
    void effectiveAt_suspendedPastEnd_expired() {
        BrokerageAgreement suspended = agreement(AgreementStatus.SUSPENDED, List.of(effective(1)));

        BrokerageAgreement read = suspended.effectiveAt(ENDS_AT.plus(Duration.ofDays(3)));

        assertEquals(AgreementStatus.EXPIRED, read.getStatus());
        assertEquals(ENDS_AT, read.getEndedAtOrNull());
    }

    /** EX-3, S8-D3: a pending version whose deadline is at or after the end is cancelled at the end, never lapsed. */
    @Test
    void effectiveAt_pendingVersionDeadlineAtOrAfterEnd_cancelledAtEnd() {
        Instant now = ENDS_AT.plus(Duration.ofDays(20));
        for (Instant sentAt : List.of(ENDS_AT.minus(Duration.ofDays(7)), ENDS_AT.minus(Duration.ofDays(3)))) {
            BrokerageAgreement active = agreement(AgreementStatus.ACTIVE, List.of(effective(1), awaitingOwner(2,
                    sentAt)));

            BrokerageAgreement read = active.effectiveAt(now);

            assertEquals(AgreementStatus.EXPIRED, read.getStatus(), sentAt.toString());
            assertEquals(VersionStatus.EFFECTIVE, read.getVersions().get(0).getStatus());
            assertEquals(VersionStatus.CANCELLED, read.getVersions().get(1).getStatus(), sentAt.toString());
            assertEquals(List.of(new DueTransition(1, AgreementEventType.EXPIRED, ENDS_AT)),
                    active.dueTransitionsAt(now), sentAt.toString());
        }
    }

    /** EX-3, S8-D3: a pending version whose deadline came before the end lapsed at it, then the agreement expired. */
    @Test
    void effectiveAt_pendingVersionDeadlineBeforeEnd_lapsedAtDeadline() {
        Instant sentAt = ENDS_AT.minus(Duration.ofDays(10));
        Instant deadline = sentAt.plus(AgreementVersion.ANSWER_WINDOW);
        Instant now = ENDS_AT.plus(Duration.ofDays(1));
        BrokerageAgreement active = agreement(AgreementStatus.ACTIVE, List.of(effective(1), awaitingOwner(2, sentAt)));

        BrokerageAgreement read = active.effectiveAt(now);

        assertEquals(AgreementStatus.EXPIRED, read.getStatus());
        assertEquals(ENDS_AT, read.getEndedAtOrNull());
        assertEquals(VersionStatus.LAPSED, read.getVersions().get(1).getStatus());
        assertEquals(List.of(new DueTransition(2, AgreementEventType.VERSION_LAPSED, deadline),
                new DueTransition(1, AgreementEventType.EXPIRED, ENDS_AT)), active.dueTransitionsAt(now));
    }

    /** EX-3: a draft has no lapse deadline, so the end cancels it; the version in effect stays EFFECTIVE. */
    @Test
    void effectiveAt_pendingDraftWithoutDeadline_cancelledAtEnd() {
        BrokerageAgreement active = agreement(AgreementStatus.ACTIVE, List.of(effective(1), draft(2)));

        BrokerageAgreement read = active.effectiveAt(ENDS_AT);

        assertEquals(AgreementStatus.EXPIRED, read.getStatus());
        assertEquals(VersionStatus.EFFECTIVE, read.getVersions().get(0).getStatus());
        assertEquals(VersionStatus.CANCELLED, read.getVersions().get(1).getStatus());
        assertEquals(ENDS_AT, read.getVersions().get(1).getUpdatedAt());
    }

    /** S8-D3: the version that was in effect stays EFFECTIVE in the data; a stored expiry reads as it is. */
    @Test
    void effectiveAt_expired_effectiveVersionStaysEffective() {
        BrokerageAgreement expired = agreement(AgreementStatus.ACTIVE, List.of(effective(1))).withDueExpiry(ENDS_AT);
        BrokerageAgreement stored = BrokerageAgreement.load("A1", "U1", "P101", "P1", TransactionType.LONG_TERM_RENT,
                "O1", "L1", AgreementStatus.EXPIRED, "S1", CREATED, ENDS_AT, 8L, expired.getVersions(), ENDS_AT, null);

        assertEquals(VersionStatus.EFFECTIVE, expired.getVersions().getFirst().getStatus());
        assertSame(stored, stored.effectiveAt(ENDS_AT.plus(Duration.ofDays(400))));
        assertEquals(List.of(), stored.dueTransitionsAt(ENDS_AT.plus(Duration.ofDays(400))));
    }

    /**
     * E05S8-P3: an activated version that started and ended with neither stored starts first, then the agreement
     * expires: the version reads EFFECTIVE with its activation record, never CANCELLED.
     */
    @Test
    void effectiveAt_activatedStartedAndEnded_versionEffectiveAgreementExpired() {
        BrokerageAgreement active = agreement(AgreementStatus.ACTIVE, List.of(activated(1)));

        BrokerageAgreement read = active.effectiveAt(ENDS_AT.plus(Duration.ofDays(2)));

        assertEquals(AgreementStatus.EXPIRED, read.getStatus());
        assertEquals(ENDS_AT, read.getEndedAtOrNull());
        AgreementVersion version = read.getVersions().getFirst();
        assertEquals(VersionStatus.EFFECTIVE, version.getStatus());
        assertEquals(ACTIVATION, version.getActivationOrNull());
        assertEquals(CONFIRMED, version.getOwnerAnswerOrNull());
    }

    @Test
    void dueTransitionsAt_activatedStartedAndEnded_startThenExpiryInTimeOrder() {
        BrokerageAgreement active = agreement(AgreementStatus.ACTIVE, List.of(activated(1)));

        assertEquals(List.of(new DueTransition(1, AgreementEventType.VERSION_STARTED, STARTS_AT),
                        new DueTransition(1, AgreementEventType.EXPIRED, ENDS_AT)),
                active.dueTransitionsAt(ENDS_AT.plus(Duration.ofDays(2))));
        assertEquals(List.of(new DueTransition(1, AgreementEventType.VERSION_STARTED, STARTS_AT)),
                active.dueTransitionsAt(STARTS_AT));
    }

    @Test
    void dueTransitionsAt_awaitingPastSevenDays_lapsedAtAnswerBy() {
        BrokerageAgreement awaiting = agreement(AgreementStatus.AWAITING_CONFIRMATION, List.of(awaitingOwner(1, SENT)));
        Instant answerBy = SENT.plus(AgreementVersion.ANSWER_WINDOW);

        assertEquals(List.of(new DueTransition(1, AgreementEventType.VERSION_LAPSED, answerBy)),
                awaiting.dueTransitionsAt(answerBy.plus(Duration.ofDays(1))));
        BrokerageAgreement read = awaiting.effectiveAt(answerBy.plus(Duration.ofDays(1)));
        assertEquals(AgreementStatus.DRAFTING, read.getStatus());
        assertEquals(VersionStatus.LAPSED, read.getVersions().getFirst().getStatus());
    }

    @Test
    void dueTransitionsAt_nothingDue_empty() {
        BrokerageAgreement active = agreement(AgreementStatus.ACTIVE, List.of(effective(1)));
        BrokerageAgreement awaiting = agreement(AgreementStatus.AWAITING_CONFIRMATION, List.of(awaitingOwner(1, SENT)));

        assertEquals(List.of(), active.dueTransitionsAt(ENDS_AT.minusNanos(1_000)));
        assertEquals(List.of(), awaiting.dueTransitionsAt(SENT.plus(Duration.ofDays(6))));
        assertEquals(List.of(), agreement(AgreementStatus.DRAFTING, List.of(draft(1))).dueTransitionsAt(
                ENDS_AT.plus(Duration.ofDays(400))));
    }

    /** EX-6: the expiry of a suspended agreement, rebuilt through the full load with its end. */
    @Test
    void withDueExpiry_suspended_expiredAndLoadsThroughFullLoad() {
        BrokerageAgreement suspended = agreement(AgreementStatus.SUSPENDED, List.of(effective(1)));
        Instant now = ENDS_AT.plus(Duration.ofHours(1));

        BrokerageAgreement expired = suspended.withDueExpiry(now);
        BrokerageAgreement reloaded = BrokerageAgreement.load(expired.getId(), expired.getUnitId(),
                expired.getUnitCodeOrNull(), expired.getPropertyId(), expired.getTransactionType(),
                expired.getOwnerUserId(), expired.getBasisLeadId(), expired.getStatus(), expired.getCreatedByUserId(),
                expired.getCreatedAt(), expired.getUpdatedAt(), expired.getRowVersionOrNull(), expired.getVersions(),
                expired.getEndedAtOrNull(), expired.getTerminationOrNull());

        assertEquals(AgreementStatus.EXPIRED, expired.getStatus());
        assertEquals(ENDS_AT, expired.getEndedAtOrNull());
        assertEquals(now, expired.getUpdatedAt());
        assertEquals(AgreementStatus.EXPIRED, reloaded.getStatus());
        assertEquals(ENDS_AT, reloaded.getEndedAtOrNull());
        assertSame(suspended, suspended.withDueExpiry(ENDS_AT.minusNanos(1_000)));
    }

    /** E05S8-P3: the expiry is the last step of effectiveAt; on a raw read whose start is still due it refuses. */
    @Test
    void withDueExpiry_startStillDue_illegalState() {
        BrokerageAgreement raw = agreement(AgreementStatus.ACTIVE, List.of(activated(1)));

        assertThrows(IllegalStateException.class, () -> raw.withDueExpiry(ENDS_AT.plus(Duration.ofDays(2))));
    }

    /** E05S8-P3: a lapse due before the end must be applied first too. */
    @Test
    void withDueExpiry_lapseDueBeforeTheEnd_illegalState() {
        Instant sentAt = ENDS_AT.minus(Duration.ofDays(10));
        BrokerageAgreement raw = agreement(AgreementStatus.ACTIVE, List.of(effective(1), awaitingOwner(2, sentAt)));

        assertThrows(IllegalStateException.class, () -> raw.withDueExpiry(ENDS_AT.plus(Duration.ofDays(1))));
    }

    @Test
    void dueExpiryAtOrNull_beforeEnd_null() {
        BrokerageAgreement active = agreement(AgreementStatus.ACTIVE, List.of(effective(1)));

        assertNull(active.dueExpiryAtOrNull(ENDS_AT.minusNanos(1_000)));
        assertEquals(ENDS_AT, active.dueExpiryAtOrNull(ENDS_AT));
        assertNull(agreement(AgreementStatus.AWAITING_CONFIRMATION, List.of(awaitingOwner(1, SENT)))
                .dueExpiryAtOrNull(ENDS_AT.plus(Duration.ofDays(400))));
    }

    private static BrokerageAgreement agreement(AgreementStatus status, List<AgreementVersion> versions) {
        return BrokerageAgreement.load("A1", "U1", "P101", "P1", TransactionType.LONG_TERM_RENT, "O1", "L1", status,
                "S1", CREATED, CREATED, 7L, versions);
    }

    private static AgreementVersion effective(int versionNo) {
        return AgreementVersion.load("V" + versionNo, versionNo, VersionStatus.EFFECTIVE, PERIOD, "L1", "I1", "S1",
                CREATED, STARTS_AT, CONSENT, TERMS, FEE, SENT, CONFIRMED, ACTIVATION);
    }

    private static AgreementVersion activated(int versionNo) {
        return AgreementVersion.load("V" + versionNo, versionNo, VersionStatus.ACTIVATED, PERIOD, "L1", "I1", "S1",
                CREATED, ACTIVATED_AT, CONSENT, TERMS, FEE, SENT, CONFIRMED, ACTIVATION);
    }

    private static AgreementVersion awaitingOwner(int versionNo, Instant sentAt) {
        return AgreementVersion.load("V" + versionNo, versionNo, VersionStatus.AWAITING_OWNER, PERIOD, "L1", "I1", "S1",
                CREATED, sentAt, CONSENT, TERMS, FEE, sentAt);
    }

    private static AgreementVersion draft(int versionNo) {
        return AgreementVersion.load("V" + versionNo, versionNo, VersionStatus.DRAFT, PERIOD, "L1", "I1", "S1", CREATED,
                CREATED, CONSENT, TERMS, FEE, null);
    }
}
