package vn.sunvibe.brokerage.core.domain.model.agreement;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The four stop transitions of the aggregate (brokerage-agreement BR-5, BR-14, BR-15, US-1 AC-5, US-5 AC-2; E05-S8
 * spec S8-D1, S8-D3, 5.1, TE-4): suspension and its release on an agreement in effect, termination of every agreement
 * that has not ended, cancellation of one that never took effect; the end cancels the pending version, an EFFECTIVE
 * version stays EFFECTIVE. The command refuses a state first; the aggregate throws on it.
 */
class BrokerageAgreementStopTest {

    private static final Instant NOW = Instant.parse("2027-01-10T03:00:00.123456Z");
    private static final Instant CREATED = NOW.minus(Duration.ofDays(30));
    private static final Instant SENT = NOW.minus(Duration.ofDays(2));
    private static final Instant CONFIRMED_AT = NOW.minus(Duration.ofDays(1));
    private static final AgreementPeriod AHEAD =
            new AgreementPeriod(LocalDate.parse("2027-02-01"), LocalDate.parse("2027-12-31"));
    private static final AgreementPeriod RUNNING =
            new AgreementPeriod(LocalDate.parse("2027-01-01"), LocalDate.parse("2027-12-31"));
    private static final PhotoConsent CONSENT = new PhotoConsent(true, false);
    private static final VersionTerms TERMS = new VersionTerms(8_000_000L,
            new TermsDeposit(DepositBasis.MONTHS, 2, null), 12, 1, List.of());
    private static final BrokerageFee FEE = new BrokerageFee(FeeFormula.of(FeeFormulaType.FIXED_AMOUNT, null,
            2_000_000L, null), 7, FeeFormula.of(FeeFormulaType.NOT_CHARGED, null, null, null),
            new EarlyLeaseEndRefund(EarlyLeaseEndType.NO_REFUND, null, null), 90);
    private static final OwnerAnswer CONFIRMED = OwnerAnswer.confirmed("O1", AgreementStatement.VERSION, CONFIRMED_AT);
    private static final ActivationDecision ACTIVATION = ActivationDecision.activated("M1", CONFIRMED_AT,
            ActivationStatement.VERSION, new SignedDocument("HDMG-1", LocalDate.parse("2027-01-09"), "A", "B"));
    private static final AgreementTermination WITHDREW = new AgreementTermination(TerminationType.OWNER_WITHDREW,
            LocalDate.parse("2027-01-09"), WithdrawalChannel.PHONE_CALL, null, null, NOW);

    @Test
    void suspend_active_suspended() {
        BrokerageAgreement active = agreement(AgreementStatus.ACTIVE, effective());

        BrokerageAgreement suspended = active.suspend(NOW);

        assertEquals(AgreementStatus.SUSPENDED, suspended.getStatus());
        assertEquals(NOW, suspended.getUpdatedAt());
        assertEquals(7L, suspended.getRowVersionOrNull());
        assertEquals(active.getVersions(), suspended.getVersions());
        assertNull(suspended.getEndedAtOrNull());
        assertEquals(AgreementStatus.SUSPENDED, agreement(AgreementStatus.ACTIVE, activated()).suspend(NOW).getStatus());
    }

    @Test
    void suspend_notActive_illegalState() {
        for (BrokerageAgreement agreement : List.of(agreement(AgreementStatus.DRAFTING, draft()),
                agreement(AgreementStatus.AWAITING_CONFIRMATION, awaitingOwner()),
                agreement(AgreementStatus.SUSPENDED, effective()), terminated(), cancelled())) {
            assertThrows(IllegalStateException.class, () -> agreement.suspend(NOW), agreement.getStatus().name());
        }
    }

    @Test
    void releaseSuspension_suspended_active() {
        BrokerageAgreement suspended = agreement(AgreementStatus.SUSPENDED, effective());

        BrokerageAgreement released = suspended.releaseSuspension(NOW);

        assertEquals(AgreementStatus.ACTIVE, released.getStatus());
        assertEquals(NOW, released.getUpdatedAt());
        assertEquals(suspended.getVersions(), released.getVersions());
        assertNull(released.getEndedAtOrNull());
    }

    @Test
    void releaseSuspension_notSuspended_illegalState() {
        for (BrokerageAgreement agreement : List.of(agreement(AgreementStatus.DRAFTING, draft()),
                agreement(AgreementStatus.ACTIVE, effective()), terminated(), cancelled())) {
            assertThrows(IllegalStateException.class, () -> agreement.releaseSuspension(NOW),
                    agreement.getStatus().name());
        }
    }

    /** TE-4, TE-6 domain: every unfinished state ends TERMINATED with its end and record; the pending version CANCELLED. */
    @Test
    void terminate_eachUnfinishedState_terminatedEndedAtPendingCancelled() {
        Map<String, BrokerageAgreement> cases = Map.of(
                "drafting", agreement(AgreementStatus.DRAFTING, draft()),
                "awaiting", agreement(AgreementStatus.AWAITING_CONFIRMATION, awaitingOwner()),
                "activatedStartAhead", agreement(AgreementStatus.ACTIVE, activated()));
        cases.forEach((name, agreement) -> {
            BrokerageAgreement terminated = agreement.terminate(WITHDREW, NOW);

            assertEquals(AgreementStatus.TERMINATED, terminated.getStatus(), name);
            assertEquals(NOW, terminated.getEndedAtOrNull(), name);
            assertEquals(WITHDREW, terminated.getTerminationOrNull(), name);
            assertEquals(NOW, terminated.getUpdatedAt(), name);
            assertEquals(VersionStatus.CANCELLED, terminated.getVersions().getFirst().getStatus(), name);
            assertEquals(NOW, terminated.getVersions().getFirst().getUpdatedAt(), name);
        });
        BrokerageAgreement activated = agreement(AgreementStatus.ACTIVE, activated()).terminate(WITHDREW, NOW);
        assertEquals(ACTIVATION, activated.getVersions().getFirst().getActivationOrNull());

        BrokerageAgreement suspended = agreement(AgreementStatus.SUSPENDED, effective()).terminate(WITHDREW, NOW);
        assertEquals(AgreementStatus.TERMINATED, suspended.getStatus());
        assertEquals(VersionStatus.EFFECTIVE, suspended.getVersions().getFirst().getStatus());
    }

    @Test
    void terminate_suspended_loadsThroughFullLoad() {
        BrokerageAgreement terminated = agreement(AgreementStatus.SUSPENDED, effective()).terminate(WITHDREW, NOW);

        BrokerageAgreement reloaded = BrokerageAgreement.load("A1", "U1", "P101", "P1", TransactionType.LONG_TERM_RENT,
                "O1", "L1", terminated.getStatus(), "S1", CREATED, terminated.getUpdatedAt(), 8L,
                terminated.getVersions(), terminated.getEndedAtOrNull(), terminated.getTerminationOrNull());

        assertEquals(AgreementStatus.TERMINATED, reloaded.getStatus());
        assertEquals(NOW, reloaded.getEndedAtOrNull());
        assertEquals(WITHDREW, reloaded.getTerminationOrNull());
    }

    @Test
    void terminate_final_illegalState() {
        BrokerageAgreement expired = BrokerageAgreement.load("A1", "U1", "P101", "P1", TransactionType.LONG_TERM_RENT,
                "O1", "L1", AgreementStatus.EXPIRED, "S1", CREATED, CREATED, 7L, List.of(effective()), NOW, null);
        for (BrokerageAgreement agreement : List.of(expired, terminated(), cancelled())) {
            assertThrows(IllegalStateException.class, () -> agreement.terminate(WITHDREW, NOW),
                    agreement.getStatus().name());
            assertThrows(IllegalStateException.class, () -> agreement.cancel(NOW), agreement.getStatus().name());
        }
    }

    /** E05S8-P3: a transition takes the effective copy; a raw read with a due transition is refused. */
    @Test
    void terminate_transitionStillDue_illegalState() {
        BrokerageAgreement awaiting = agreement(AgreementStatus.AWAITING_CONFIRMATION, awaitingOwner());
        Instant past = SENT.plus(Duration.ofDays(8));

        assertThrows(IllegalStateException.class, () -> awaiting.terminate(WITHDREW, past));
        assertThrows(IllegalStateException.class, () -> awaiting.cancel(past));
        assertEquals(AgreementStatus.TERMINATED, awaiting.effectiveAt(past).terminate(WITHDREW, past).getStatus());
    }

    @Test
    void cancel_draftingOrAwaiting_cancelledEndedAtPendingCancelled() {
        for (BrokerageAgreement agreement : List.of(agreement(AgreementStatus.DRAFTING, draft()),
                agreement(AgreementStatus.AWAITING_CONFIRMATION, awaitingOwner()))) {
            BrokerageAgreement cancelled = agreement.cancel(NOW);

            assertEquals(AgreementStatus.CANCELLED, cancelled.getStatus());
            assertEquals(NOW, cancelled.getEndedAtOrNull());
            assertNull(cancelled.getTerminationOrNull());
            assertEquals(VersionStatus.CANCELLED, cancelled.getVersions().getFirst().getStatus());
        }
    }

    @Test
    void cancel_ownerConfirmedVersion_keepsItsOwnerAnswer() {
        BrokerageAgreement confirmed = agreement(AgreementStatus.AWAITING_CONFIRMATION, ownerConfirmed());

        BrokerageAgreement cancelled = confirmed.cancel(NOW);

        AgreementVersion version = cancelled.getVersions().getFirst();
        assertEquals(VersionStatus.CANCELLED, version.getStatus());
        assertEquals(CONFIRMED, version.getOwnerAnswerOrNull());
        assertEquals(SENT, version.getSentAtOrNull());
    }

    @Test
    void cancel_inEffect_illegalState() {
        for (BrokerageAgreement agreement : List.of(agreement(AgreementStatus.ACTIVE, effective()),
                agreement(AgreementStatus.ACTIVE, activated()), agreement(AgreementStatus.SUSPENDED, effective()))) {
            assertThrows(IllegalStateException.class, () -> agreement.cancel(NOW), agreement.getStatus().name());
        }
    }

    /** S8-D3: the version in effect stays EFFECTIVE when the agreement is terminated; its agreement says it ended. */
    @Test
    void terminate_effectiveVersion_staysEffective() {
        BrokerageAgreement terminated = agreement(AgreementStatus.ACTIVE, effective()).terminate(WITHDREW, NOW);

        assertEquals(AgreementStatus.TERMINATED, terminated.getStatus());
        assertEquals(VersionStatus.EFFECTIVE, terminated.getVersions().getFirst().getStatus());
        assertEquals(ACTIVATION, terminated.getVersions().getFirst().getActivationOrNull());
    }

    private static BrokerageAgreement agreement(AgreementStatus status, AgreementVersion version) {
        return BrokerageAgreement.load("A1", "U1", "P101", "P1", TransactionType.LONG_TERM_RENT, "O1", "L1", status,
                "S1", CREATED, CREATED, 7L, List.of(version));
    }

    private static BrokerageAgreement terminated() {
        return BrokerageAgreement.load("A1", "U1", "P101", "P1", TransactionType.LONG_TERM_RENT, "O1", "L1",
                AgreementStatus.TERMINATED, "S1", CREATED, CREATED, 7L, List.of(effective()), NOW, WITHDREW);
    }

    private static BrokerageAgreement cancelled() {
        return BrokerageAgreement.load("A1", "U1", "P101", "P1", TransactionType.LONG_TERM_RENT, "O1", "L1",
                AgreementStatus.CANCELLED, "S1", CREATED, CREATED, 7L, List.of(version(VersionStatus.CANCELLED, AHEAD,
                        null, null, null)), NOW, null);
    }

    private static AgreementVersion draft() {
        return version(VersionStatus.DRAFT, AHEAD, null, null, null);
    }

    private static AgreementVersion awaitingOwner() {
        return version(VersionStatus.AWAITING_OWNER, AHEAD, SENT, null, null);
    }

    private static AgreementVersion ownerConfirmed() {
        return version(VersionStatus.OWNER_CONFIRMED, AHEAD, SENT, CONFIRMED, null);
    }

    private static AgreementVersion activated() {
        return version(VersionStatus.ACTIVATED, AHEAD, SENT, CONFIRMED, ACTIVATION);
    }

    private static AgreementVersion effective() {
        return version(VersionStatus.EFFECTIVE, RUNNING, SENT, CONFIRMED, ACTIVATION);
    }

    private static AgreementVersion version(VersionStatus status, AgreementPeriod period, Instant sentAt,
                                            OwnerAnswer answer, ActivationDecision activation) {
        return AgreementVersion.load("V1", 1, status, period, "L1", "I1", "S1", CREATED, CREATED, CONSENT, TERMS, FEE,
                sentAt, answer, activation);
    }
}
