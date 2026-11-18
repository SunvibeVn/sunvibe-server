package vn.sunvibe.brokerage.core.domain.model.agreement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Activation, its refusal, the 14-day lapse and the stored start in the aggregate (brokerage-agreement US-3 AC-1 to
 * AC-5; BR-5, BR-6, BR-11; E05-S6 spec S6-D1 step 11, S6-D4 to S6-D6). The boundaries are proved with a fixed
 * {@code now}: 2027-01-10 10:00 in Vietnam.
 */
class BrokerageAgreementActivationTest {

    private static final Instant NOW = Instant.parse("2027-01-10T03:00:00.123456Z");
    private static final Instant CREATED = NOW.minus(Duration.ofDays(5));
    private static final Instant SENT = NOW.minus(Duration.ofDays(2));
    private static final Instant CONFIRMED_AT = NOW.minus(Duration.ofDays(1));
    private static final Instant ACTIVATE_BY = CONFIRMED_AT.plus(Duration.ofDays(14));
    private static final AgreementPeriod AHEAD =
            new AgreementPeriod(LocalDate.parse("2027-02-01"), LocalDate.parse("2027-12-31"));
    private static final AgreementPeriod TODAY =
            new AgreementPeriod(LocalDate.parse("2027-01-10"), LocalDate.parse("2027-12-31"));
    private static final AgreementPeriod PASSED =
            new AgreementPeriod(LocalDate.parse("2027-01-01"), LocalDate.parse("2027-12-31"));
    private static final PhotoConsent CONSENT = new PhotoConsent(true, false);
    private static final VersionTerms TERMS = new VersionTerms(8_000_000L,
            new TermsDeposit(DepositBasis.MONTHS, 2, null), 12, 1, List.of());
    private static final BrokerageFee FEE = new BrokerageFee(FeeFormula.of(FeeFormulaType.FIXED_AMOUNT, null,
            2_000_000L, null), 7, FeeFormula.of(FeeFormulaType.NOT_CHARGED, null, null, null),
            new EarlyLeaseEndRefund(EarlyLeaseEndType.NO_REFUND, null, null), 90);
    private static final OwnerAnswer CONFIRMED = OwnerAnswer.confirmed("O1", AgreementStatement.VERSION, CONFIRMED_AT);
    private static final SignedDocument DOCUMENT = new SignedDocument("HDMG-1", LocalDate.parse("2027-01-09"), "A", "B");
    private static final ActivationDecision ACTIVATION =
            ActivationDecision.activated("M1", NOW, ActivationStatement.VERSION, DOCUMENT);
    private static final ActivationDecision REFUSAL =
            ActivationDecision.refused("M1", NOW, ActivationRefusalType.DOCUMENT_DIFFERS, "Rent differs.");

    /** AV-2: a start date ahead activates the version as ACTIVATED; the agreement is in effect at once. */
    @Test
    void activate_startAhead_activatedAgreementActive() {
        BrokerageAgreement agreement = awaitingActivation(AHEAD);

        BrokerageAgreement activated = agreement.activate(1, ACTIVATION, NOW);

        assertEquals(AgreementStatus.ACTIVE, activated.getStatus());
        assertEquals(NOW, activated.getUpdatedAt());
        assertEquals(7L, activated.getRowVersionOrNull());
        AgreementVersion version = activated.getVersions().getFirst();
        assertEquals(VersionStatus.ACTIVATED, version.getStatus());
        assertEquals(ACTIVATION, version.getActivationOrNull());
        assertEquals(CONFIRMED, version.getOwnerAnswerOrNull());
        assertEquals(NOW, version.getUpdatedAt());
        assertEquals(TERMS, version.getTerms());
        assertEquals(AgreementStatus.AWAITING_CONFIRMATION, agreement.getStatus());
    }

    /** Review Focus 5: a start date of today has begun at 00:00 in Vietnam, so the version is in effect at once. */
    @Test
    void activate_startToday_effectiveAtOnce() {
        BrokerageAgreement activated = awaitingActivation(TODAY).activate(1, ACTIVATION, NOW);

        assertEquals(AgreementStatus.ACTIVE, activated.getStatus());
        assertEquals(VersionStatus.EFFECTIVE, activated.getVersions().getFirst().getStatus());
        assertEquals(ACTIVATION, activated.getVersions().getFirst().getActivationOrNull());
    }

    /** brokerage-agreement US-3 AC-5, BR-6: a start date in the past is allowed; the right starts at the activation. */
    @Test
    void activate_startPassed_effectiveAtOnce() {
        BrokerageAgreement activated = awaitingActivation(PASSED).activate(1, ACTIVATION, NOW);

        assertEquals(VersionStatus.EFFECTIVE, activated.getVersions().getFirst().getStatus());
        assertEquals(AgreementStatus.ACTIVE, activated.getStatus());
    }

    @Test
    void activate_notAwaitingOrOtherVersionOrPastActivateBy_illegalState() {
        BrokerageAgreement awaiting = awaitingActivation(PASSED);
        BrokerageAgreement waitingForOwner = load(AgreementStatus.AWAITING_CONFIRMATION, List.of(
                AgreementVersion.load("V1", 1, VersionStatus.AWAITING_OWNER, PASSED, "L1", "I1", "S1", CREATED, SENT,
                        CONSENT, TERMS, FEE, SENT)));
        BrokerageAgreement active = awaiting.activate(1, ACTIVATION, NOW);
        List<Executable> refused = List.of(
                () -> awaiting.activate(2, ACTIVATION, NOW),
                () -> awaiting.activate(1, ACTIVATION, ACTIVATE_BY),
                () -> awaiting.refuseActivation(1, REFUSAL, ACTIVATE_BY),
                () -> waitingForOwner.activate(1, ACTIVATION, NOW),
                () -> active.activate(1, ACTIVATION, NOW),
                () -> active.refuseActivation(1, REFUSAL, NOW));
        for (int index = 0; index < refused.size(); index++) {
            assertThrows(IllegalStateException.class, refused.get(index), "case " + index);
        }

        BrokerageAgreement justBefore = awaiting.activate(1, ACTIVATION, ACTIVATE_BY.minus(1, ChronoUnit.MICROS));
        assertEquals(AgreementStatus.ACTIVE, justBefore.getStatus());
    }

    /** brokerage-agreement US-3 AC-3 (E05-S6 spec S6-D4, S5-D9): refused, and drafting again with no pending version. */
    @Test
    void refuseActivation_refusedAndDraftingNoPending() {
        BrokerageAgreement refused = awaitingActivation(AHEAD).refuseActivation(1, REFUSAL, NOW);

        assertEquals(AgreementStatus.DRAFTING, refused.getStatus());
        assertEquals(NOW, refused.getUpdatedAt());
        AgreementVersion version = refused.getVersions().getFirst();
        assertEquals(VersionStatus.ACTIVATION_REFUSED, version.getStatus());
        assertEquals(REFUSAL, version.getActivationOrNull());
        assertEquals(CONFIRMED, version.getOwnerAnswerOrNull());
        assertTrue(refused.getVersions().stream().noneMatch(each -> each.getStatus().isPending()));
        assertEquals(VersionStatus.DRAFT, refused.openDraft("V2", "S1", NOW).getVersions().get(1).getStatus());
    }

    /** LP-1: from C + 14 days the confirmed version lapses, keeps the owner's answer, and the agreement drafts again. */
    @Test
    void withDueLapse_confirmedAtActivateBy_lapsedAnswerKeptDrafting() {
        BrokerageAgreement agreement = awaitingActivation(AHEAD);
        Instant justBefore = ACTIVATE_BY.minus(1, ChronoUnit.MICROS);

        assertSame(agreement, agreement.withDueLapse(justBefore));
        assertNull(agreement.dueLapseOrNull(justBefore));

        BrokerageAgreement lapsed = agreement.withDueLapse(ACTIVATE_BY);

        assertEquals(AgreementStatus.DRAFTING, lapsed.getStatus());
        AgreementVersion version = lapsed.getVersions().getFirst();
        assertEquals(VersionStatus.LAPSED, version.getStatus());
        assertEquals(CONFIRMED, version.getOwnerAnswerOrNull());
        assertNull(version.getActivationOrNull());
        assertEquals(ACTIVATE_BY, version.activateByOrNull());
        assertEquals(ACTIVATE_BY, version.getUpdatedAt());
        assertEquals("V1", agreement.dueLapseOrNull(ACTIVATE_BY).getId());
        assertEquals(ACTIVATE_BY, agreement.dueLapseOrNull(ACTIVATE_BY).lapseDeadlineOrNull());
        assertEquals(AgreementStatus.AWAITING_CONFIRMATION, agreement.getStatus());
    }

    /** E05-S6 spec S6-D5: an OWNER_CONFIRMED version without an owner answer (a fixture row) has no deadline. */
    @Test
    void withDueLapse_confirmedWithoutAnswer_neverLapses() {
        BrokerageAgreement agreement = load(AgreementStatus.AWAITING_CONFIRMATION, List.of(
                AgreementVersion.load("V1", 1, VersionStatus.OWNER_CONFIRMED, PASSED, "L1", "I1", "S1", CREATED, CREATED)));
        Instant muchLater = NOW.plus(Duration.ofDays(400));

        assertSame(agreement, agreement.withDueLapse(muchLater));
        assertNull(agreement.dueLapseOrNull(muchLater));
        assertSame(agreement, agreement.effectiveAt(muchLater));
    }

    /** brokerage-agreement BR-5, BR-6 (E05-S6 spec S6-D6): an activated version takes effect at the start of its date. */
    @Test
    void withDueStart_atStartsAt_effective() {
        BrokerageAgreement activated = awaitingActivation(AHEAD).activate(1, ACTIVATION, NOW);
        Instant startsAt = Instant.parse("2027-01-31T17:00:00Z");
        Instant justBefore = startsAt.minus(1, ChronoUnit.MICROS);

        assertSame(activated, activated.withDueStart(justBefore));
        assertNull(activated.dueStartOrNull(justBefore));

        BrokerageAgreement started = activated.withDueStart(startsAt);

        assertEquals(AgreementStatus.ACTIVE, started.getStatus());
        AgreementVersion version = started.getVersions().getFirst();
        assertEquals(VersionStatus.EFFECTIVE, version.getStatus());
        assertEquals(ACTIVATION, version.getActivationOrNull());
        assertEquals(startsAt, activated.dueStartOrNull(startsAt).startsAt());
        assertEquals(VersionStatus.ACTIVATED, activated.getVersions().getFirst().getStatus());
    }

    /** E05-S6 spec S6-D6: an ACTIVATED row without its activation record (a fixture) is never started. */
    @Test
    void withDueStart_activatedWithoutRecord_neverStarted() {
        BrokerageAgreement agreement = load(AgreementStatus.ACTIVE, List.of(
                AgreementVersion.load("V1", 1, VersionStatus.ACTIVATED, AHEAD, "L1", "I1", "S1", CREATED, CREATED)));
        Instant muchLater = NOW.plus(Duration.ofDays(400));

        assertSame(agreement, agreement.withDueStart(muchLater));
        assertNull(agreement.dueStartOrNull(muchLater));
    }

    /**
     * E05-S6 spec S6-D6: {@code effectiveAt} applies the due lapse, then the due start. BR-5's one pending version means
     * at most one of them is due on one agreement, so each branch is shown through {@code effectiveAt}.
     */
    @Test
    void effectiveAt_lapseThenStart_bothApplied() {
        BrokerageAgreement awaiting = awaitingActivation(AHEAD);
        BrokerageAgreement activated = awaiting.activate(1, ACTIVATION, NOW);
        Instant startsAt = Instant.parse("2027-01-31T17:00:00Z");

        BrokerageAgreement lapsed = awaiting.effectiveAt(ACTIVATE_BY);
        assertEquals(AgreementStatus.DRAFTING, lapsed.getStatus());
        assertEquals(VersionStatus.LAPSED, lapsed.getVersions().getFirst().getStatus());

        BrokerageAgreement started = activated.effectiveAt(startsAt);
        assertEquals(AgreementStatus.ACTIVE, started.getStatus());
        assertEquals(VersionStatus.EFFECTIVE, started.getVersions().getFirst().getStatus());

        assertSame(awaiting, awaiting.effectiveAt(NOW));
        assertSame(activated, activated.effectiveAt(NOW));
    }

    private static BrokerageAgreement awaitingActivation(AgreementPeriod period) {
        return load(AgreementStatus.AWAITING_CONFIRMATION, List.of(AgreementVersion.load("V1", 1,
                VersionStatus.OWNER_CONFIRMED, period, "L1", "I1", "S1", CREATED, CONFIRMED_AT, CONSENT, TERMS, FEE,
                SENT, CONFIRMED)));
    }

    private static BrokerageAgreement load(AgreementStatus status, List<AgreementVersion> versions) {
        return BrokerageAgreement.load("A1", "U1", "P101", "P1", TransactionType.LONG_TERM_RENT, "O1", "L1", status,
                "S1", CREATED, CREATED, 7L, versions);
    }
}
