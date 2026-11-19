package vn.sunvibe.brokerage.core.application.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;
import vn.sunvibe.brokerage.core.application.command.AgreementCancelCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementReleaseSuspensionCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementSuspendCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementTerminateCommand;
import vn.sunvibe.brokerage.core.application.exception.AgreementRefusedException;
import vn.sunvibe.brokerage.core.application.exception.AgreementRefusedException.Entry;
import vn.sunvibe.brokerage.core.application.exception.AgreementRefusedException.Reason;
import vn.sunvibe.brokerage.core.application.exception.BrokerageNotFoundException;
import vn.sunvibe.brokerage.core.application.usecase.StaffViewer;
import vn.sunvibe.brokerage.core.domain.exception.AgreementContentException;
import vn.sunvibe.brokerage.core.domain.model.agreement.ActivationDecision;
import vn.sunvibe.brokerage.core.domain.model.agreement.ActivationStatement;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementEventType;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementPeriod;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementStatement;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementStatus;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementSuspension;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementTermination;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementVersion;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageAgreement;
import vn.sunvibe.brokerage.core.domain.model.agreement.IntroducedBySunvibe;
import vn.sunvibe.brokerage.core.domain.model.agreement.OwnerAnswer;
import vn.sunvibe.brokerage.core.domain.model.agreement.PhotoConsent;
import vn.sunvibe.brokerage.core.domain.model.agreement.SignedDocument;
import vn.sunvibe.brokerage.core.domain.model.agreement.SuspensionReasonType;
import vn.sunvibe.brokerage.core.domain.model.agreement.TerminationType;
import vn.sunvibe.brokerage.core.domain.model.agreement.TransactionType;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionStatus;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionTerms;
import vn.sunvibe.brokerage.core.domain.model.agreement.WithdrawalChannel;
import vn.sunvibe.brokerage.core.testsupport.ChangeBeforeLockAgreementRepository;
import vn.sunvibe.brokerage.core.testsupport.FixedAgreementSwitches;
import vn.sunvibe.brokerage.core.testsupport.InMemoryAgreementRepository;
import vn.sunvibe.brokerage.core.testsupport.InMemoryAgreementSuspensionRepository;
import vn.sunvibe.brokerage.core.testsupport.InMemoryBasisLeadPort;
import vn.sunvibe.brokerage.core.testsupport.InMemoryIntakeBasisPort;
import vn.sunvibe.brokerage.core.testsupport.NoDatabaseTransactions;
import vn.sunvibe.brokerage.core.testsupport.RecordingAccountPasswordPort;
import vn.sunvibe.brokerage.core.testsupport.RecordingAgreementTrail;
import vn.sunvibe.brokerage.core.testsupport.RecordingAgreementTrail.Event;
import vn.sunvibe.brokerage.core.testsupport.RecordingEventOutboxPort;
import vn.sunvibe.brokerage.core.testsupport.RecordingOwnerAccountPort;
import vn.sunvibe.brokerage.core.testsupport.RecordingOwnerNoticePort;
import vn.sunvibe.shared.outbox.UnitAdvertisingChangedEvent;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The four stops of an agreement (brokerage-agreement BR-5, BR-14 to BR-16, US-1 AC-5, US-5 AC-2, AC-5; E05-S8 spec
 * 3.1 to 3.4, S8-D1, S8-D5 to S8-D8, S8-D12; SU-1 to SU-3, RL-1 to RL-4, TE-1 to TE-7, CA-1 to CA-4, SR-1 at service
 * level; plan E05S8-P3, P4, P16): the steps in the spec's order, the first failing one answering, the due transitions'
 * rows in time order before the stop's own, and nothing written on a refusal. The fixtures are dated from the wall clock,
 * which the commands read (AppClock is no seam).
 */
class AgreementStopCommandServiceTest {

    private static final String LEAD = "L1";
    private static final String LEADER_LEAD = "L2";
    private static final String SALE = "S";
    private static final String OTHER_SALE = "S9";
    private static final String MANAGER = "M";
    private static final String ADMIN = "AD";
    private static final String LEADER = "LD";
    private static final String OWNER = "O";
    /** A ULID: the outbox event refuses any other unit id. */
    private static final String UNIT = "01JQ7T9Z8XW4H3M5N6P2R0S001";
    private static final StaffViewer SALE_VIEWER = new StaffViewer(SALE, Set.of("ROLE_SALE"));
    private static final StaffViewer MANAGER_VIEWER = new StaffViewer(MANAGER, Set.of("ROLE_SALES_MANAGER",
            "ROLE_SALE_LEADER", "ROLE_SALE"));
    private static final StaffViewer ADMIN_VIEWER = new StaffViewer(ADMIN, Set.of("ROLE_ADMIN"));
    private static final StaffViewer LEADER_VIEWER = new StaffViewer(LEADER, Set.of("ROLE_SALE_LEADER", "ROLE_SALE"));
    private static final String AGREEMENT = "01JSTOPAGREEMENT0000000001";
    private static final long ROW_VERSION = 3L;
    private static final Instant WALL = Instant.now().truncatedTo(ChronoUnit.MICROS);
    private static final LocalDate TODAY = AgreementVersion.todayInVietnam(WALL);
    private static final AgreementPeriod RUNNING = new AgreementPeriod(TODAY.minusDays(10), TODAY.plusMonths(6));
    private static final AgreementPeriod AHEAD = new AgreementPeriod(TODAY.plusDays(10), TODAY.plusMonths(6));
    private static final AgreementPeriod STARTED_YESTERDAY = new AgreementPeriod(TODAY.minusDays(1),
            TODAY.plusMonths(6));
    private static final AgreementPeriod ENDED = new AgreementPeriod(TODAY.minusMonths(6), TODAY.minusDays(1));
    private static final Instant CREATED = WALL.minus(Duration.ofDays(60));
    private static final PhotoConsent CONSENT = new PhotoConsent(true, false);

    private final List<String> calls = new ArrayList<>();
    private final InMemoryBasisLeadPort leads = new InMemoryBasisLeadPort(calls)
            .lead(LEAD, SALE, SALE, MANAGER, ADMIN, LEADER)
            .lead(LEADER_LEAD, LEADER, LEADER, MANAGER, ADMIN);
    private final InMemoryAgreementRepository stored = new InMemoryAgreementRepository();
    private final ChangeBeforeLockAgreementRepository agreements = new ChangeBeforeLockAgreementRepository(stored);
    private final RecordingAgreementTrail trail = new RecordingAgreementTrail();
    private final RecordingEventOutboxPort outbox = new RecordingEventOutboxPort();
    private final RecordingOwnerNoticePort notices = new RecordingOwnerNoticePort();
    private final InMemoryAgreementSuspensionRepository suspensions = new InMemoryAgreementSuspensionRepository();
    private final AtomicInteger sequence = new AtomicInteger();

    private AgreementCommandService service() {
        return new AgreementCommandService(leads, new InMemoryIntakeBasisPort(calls),
                new RecordingOwnerAccountPort(calls), agreements, trail,
                () -> String.format("01JID%021d", sequence.incrementAndGet()), notices, new FixedAgreementSwitches(),
                new RecordingAccountPasswordPort(), new TransactionTemplate(new NoDatabaseTransactions()), outbox,
                suspensions);
    }

    // ==================== suspension (spec 3.1) ====================

    /** SU-1: a manager suspends with a reason and a note: one row, one open record, one event; no notice. */
    @Test
    void suspend_activeByManager_suspendedRecordRowEvent() {
        stored.store(agreement(AgreementStatus.ACTIVE, effective(RUNNING)));

        service().suspend(new AgreementSuspendCommand(AGREEMENT, SuspensionReasonType.COMPLAINT,
                "  Tenant reported a different price.  "), MANAGER_VIEWER);

        BrokerageAgreement after = read();
        assertEquals(AgreementStatus.SUSPENDED, after.getStatus());
        assertEquals(ROW_VERSION + 1, after.getRowVersionOrNull());
        assertEquals(List.of(AgreementEventType.SUSPENDED), types());
        assertEquals(MANAGER, trail.events().getFirst().actorUserIdOrNull());
        assertEquals(1, trail.events().getFirst().versionNoOrNull());
        AgreementSuspension record = suspensions.all().getFirst();
        assertEquals(1, suspensions.all().size());
        assertTrue(record.isOpen());
        assertEquals(SuspensionReasonType.COMPLAINT, record.reasonType());
        assertEquals("Tenant reported a different price.", record.noteOrNull());
        assertEquals(MANAGER, record.suspendedBy());
        assertEquals(List.of(UnitAdvertisingChangedEvent.now(UNIT).aggregateId()),
                outbox.events().stream().map(event -> event.aggregateId()).toList());
        assertEquals(UnitAdvertisingChangedEvent.TYPE, outbox.events().getFirst().type());
        assertEquals(List.of(), notices.agreementPending());
        assertTrue(calls.contains("forUpdate " + LEAD + " " + MANAGER));
    }

    /** SU-2: an ended, a not-in-effect and a suspended agreement answer their own code and write nothing. */
    @Test
    void suspend_wrongState_endedNotInEffectAlreadySuspended() {
        Map<BrokerageAgreement, Reason> cases = Map.of(
                stopped(AgreementStatus.TERMINATED), Reason.AGREEMENT_ENDED,
                agreement(AgreementStatus.ACTIVE, effective(ENDED)), Reason.AGREEMENT_ENDED,
                agreement(AgreementStatus.DRAFTING, draft()), Reason.AGREEMENT_NOT_IN_EFFECT,
                agreement(AgreementStatus.AWAITING_CONFIRMATION, awaitingOwner(WALL.minus(Duration.ofDays(1)))),
                Reason.AGREEMENT_NOT_IN_EFFECT,
                agreement(AgreementStatus.SUSPENDED, effective(RUNNING)), Reason.AGREEMENT_ALREADY_SUSPENDED);
        cases.forEach((agreement, reason) -> {
            stored.store(agreement);

            assertEquals(reason, refused(() -> service().suspend(suspendCommand(), MANAGER_VIEWER)), reason.name());
            assertNothingWritten();
        });
    }

    /** SU-3: the start that came and was not stored is stored first, its row at the start, then the suspension's. */
    @Test
    void suspend_startDueNotStored_startedRowThenSuspended() {
        AgreementVersion activated = activated(STARTED_YESTERDAY);
        stored.store(agreement(AgreementStatus.ACTIVE, activated));

        service().suspend(suspendCommand(), MANAGER_VIEWER);

        assertEquals(List.of(AgreementEventType.VERSION_STARTED, AgreementEventType.SUSPENDED), types());
        assertEquals(activated.startsAt(), trail.events().getFirst().at());
        assertNull(trail.events().getFirst().actorUserIdOrNull());
        AgreementVersion version = read().getVersions().getFirst();
        assertEquals(VersionStatus.EFFECTIVE, version.getStatus());
        assertEquals(activated.getActivationOrNull(), version.getActivationOrNull());
        assertEquals(AgreementStatus.SUSPENDED, read().getStatus());
    }

    // ==================== release (spec 3.2) ====================

    /** RL-1: an independent manager lifts it: ACTIVE, the record closed with who, when and why, one row, one event. */
    @Test
    void releaseSuspension_independentManager_activeRecordClosedRowEvent() {
        suspendedWithOpenRecord(LEAD);

        service().releaseSuspension(new AgreementReleaseSuspensionCommand(AGREEMENT, ROW_VERSION,
                "  The price was checked.  "), ADMIN_VIEWER);

        assertEquals(AgreementStatus.ACTIVE, read().getStatus());
        assertEquals(List.of(AgreementEventType.SUSPENSION_RELEASED), types());
        assertEquals(ADMIN, trail.events().getFirst().actorUserIdOrNull());
        AgreementSuspension record = suspensions.all().getFirst();
        assertFalse(record.isOpen());
        assertEquals(ADMIN, record.releasedByOrNull());
        assertEquals("The price was checked.", record.releaseReasonOrNull());
        assertEquals(1, outbox.events().size());
    }

    /** RL-2: the lead's current assignee, holding SALE_LEADER, may not lift it. */
    @Test
    void releaseSuspension_currentAssignee_notIndependent() {
        suspendedWithOpenRecord(LEADER_LEAD);

        assertEquals(Reason.AGREEMENT_RELEASER_NOT_INDEPENDENT, refused(() -> service().releaseSuspension(
                releaseCommand(ROW_VERSION), LEADER_VIEWER)));
        assertNothingWritten();
        assertTrue(suspensions.all().getFirst().isOpen());
    }

    @Test
    void releaseSuspension_staleDataVersion_concurrentModification() {
        suspendedWithOpenRecord(LEAD);

        AgreementRefusedException refusal = assertThrows(AgreementRefusedException.class,
                () -> service().releaseSuspension(releaseCommand(ROW_VERSION - 1), MANAGER_VIEWER));

        assertEquals(Reason.CONCURRENT_MODIFICATION, refusal.reason());
        assertEquals(List.of(new Entry("expectedDataVersion", null)), refusal.entries());
        assertNothingWritten();
    }

    /** RL-2 states: ACTIVE is not suspended; a suspension past its end reads EXPIRED and has ended. */
    @Test
    void releaseSuspension_activeOrPastEnd_notSuspendedOrEnded() {
        stored.store(agreement(AgreementStatus.ACTIVE, effective(RUNNING)));
        assertEquals(Reason.AGREEMENT_NOT_SUSPENDED, refused(() -> service().releaseSuspension(
                releaseCommand(ROW_VERSION), MANAGER_VIEWER)));

        stored.store(agreement(AgreementStatus.SUSPENDED, effective(ENDED)));
        suspensions.store(AgreementSuspension.open("01JSUSPENSION0000000000001", AGREEMENT,
                SuspensionReasonType.DISPUTE, null, MANAGER, WALL.minus(Duration.ofDays(30))));
        assertEquals(Reason.AGREEMENT_ENDED, refused(() -> service().releaseSuspension(releaseCommand(ROW_VERSION),
                MANAGER_VIEWER)));
        assertNothingWritten();
        assertTrue(suspensions.all().getFirst().isOpen());
    }

    /** RL-4: the start that came while suspended is stored first, then the release. */
    @Test
    void releaseSuspension_startDueNotStored_startedRowThenReleased() {
        AgreementVersion activated = activated(STARTED_YESTERDAY);
        stored.store(agreement(AgreementStatus.SUSPENDED, activated));
        suspensions.store(AgreementSuspension.open("01JSUSPENSION0000000000001", AGREEMENT,
                SuspensionReasonType.NEEDS_CHECK, null, MANAGER, WALL.minus(Duration.ofDays(3))));

        service().releaseSuspension(releaseCommand(ROW_VERSION), ADMIN_VIEWER);

        assertEquals(List.of(AgreementEventType.VERSION_STARTED, AgreementEventType.SUSPENSION_RELEASED), types());
        assertEquals(activated.startsAt(), trail.events().getFirst().at());
        assertEquals(VersionStatus.EFFECTIVE, read().getVersions().getFirst().getStatus());
        assertEquals(AgreementStatus.ACTIVE, read().getStatus());
    }

    /**
     * SR-1 at service level: a SUSPENDED agreement whose open record is missing breaks the rule of the records; the
     * release throws, which rolls the command back on PostgreSQL (asserted there, plan E05S8-P16).
     */
    @Test
    void releaseSuspension_noOpenRecord_illegalState() {
        stored.store(agreement(AgreementStatus.SUSPENDED, effective(RUNNING)));

        assertThrows(IllegalStateException.class, () -> service().releaseSuspension(releaseCommand(ROW_VERSION),
                MANAGER_VIEWER));
        assertEquals(List.of(), outbox.events());
    }

    // ==================== termination (spec 3.3) ====================

    /** TE-1: the assigned Sale records that the unit is let: TERMINATED at now, the record, one row, one event. */
    @Test
    void terminate_unitLetByAssignedSale_terminatedRecordRowEvent() {
        stored.store(agreement(AgreementStatus.ACTIVE, effective(RUNNING)));
        Instant before = Instant.now().truncatedTo(ChronoUnit.MICROS);

        service().terminate(new AgreementTerminateCommand(AGREEMENT, TerminationType.UNIT_LET, TODAY, null,
                IntroducedBySunvibe.YES, "  Let to a friend.  "), SALE_VIEWER);

        BrokerageAgreement after = read();
        assertEquals(AgreementStatus.TERMINATED, after.getStatus());
        assertNotNull(after.getEndedAtOrNull());
        assertFalse(after.getEndedAtOrNull().isBefore(before));
        assertEquals(new AgreementTermination(TerminationType.UNIT_LET, TODAY, null, IntroducedBySunvibe.YES,
                "Let to a friend.", after.getEndedAtOrNull()), after.getTerminationOrNull());
        assertEquals(VersionStatus.EFFECTIVE, after.getVersions().getFirst().getStatus());
        assertEquals(List.of(new Event(AGREEMENT, 1, AgreementEventType.TERMINATED, SALE, after.getEndedAtOrNull())),
                trail.events());
        assertEquals(1, outbox.events().size());
        assertEquals(List.of(), notices.agreementPending());
        assertEquals(List.of(), notices.agreementActivated());
    }

    /** TE-2: who records each reason. */
    @Test
    void terminate_permissions_byTypeAndViewer() {
        stored.store(agreement(AgreementStatus.ACTIVE, effective(RUNNING)));
        assertEquals(Reason.AGREEMENT_ASSIGNEE_ONLY, refused(() -> service().terminate(unitLet(), MANAGER_VIEWER)));
        assertEquals(Reason.AGREEMENT_TERMINATION_NOT_ALLOWED, refused(() -> service().terminate(
                sunvibe("A dispute with the owner."), SALE_VIEWER)));
        assertNothingWritten();

        for (StaffViewer viewer : List.of(SALE_VIEWER, MANAGER_VIEWER, ADMIN_VIEWER, LEADER_VIEWER)) {
            stored.store(agreement(AgreementStatus.ACTIVE, effective(RUNNING)));

            service().terminate(ownerWithdrew(), viewer);

            assertEquals(AgreementStatus.TERMINATED, read().getStatus(), viewer.userId());
        }
        stored.store(agreement(AgreementStatus.ACTIVE, effective(RUNNING)));
        service().terminate(sunvibe("A dispute with the owner."), MANAGER_VIEWER);
        assertEquals(TerminationType.SUNVIBE_TERMINATED, read().getTerminationOrNull().type());
    }

    @Test
    void terminate_final_agreementEnded() {
        for (BrokerageAgreement ended : List.of(stopped(AgreementStatus.TERMINATED), stopped(AgreementStatus.CANCELLED),
                stopped(AgreementStatus.EXPIRED), agreement(AgreementStatus.ACTIVE, effective(ENDED)))) {
            stored.store(ended);

            assertEquals(Reason.AGREEMENT_ENDED, refused(() -> service().terminate(ownerWithdrew(), SALE_VIEWER)));
            assertNothingWritten();
        }
    }

    /** TE-6: before it took effect, the pending version is cancelled; an answer the owner gave stays on it. */
    @Test
    void terminate_beforeEffect_pendingCancelled() {
        stored.store(agreement(AgreementStatus.DRAFTING, draft()));
        service().terminate(ownerWithdrew(), SALE_VIEWER);
        assertEquals(VersionStatus.CANCELLED, read().getVersions().getFirst().getStatus());

        stored.store(agreement(AgreementStatus.AWAITING_CONFIRMATION, awaitingOwner(WALL.minus(Duration.ofDays(1)))));
        service().terminate(ownerWithdrew(), MANAGER_VIEWER);
        assertEquals(VersionStatus.CANCELLED, read().getVersions().getFirst().getStatus());

        AgreementVersion confirmed = ownerConfirmed(WALL.minus(Duration.ofDays(1)));
        stored.store(agreement(AgreementStatus.AWAITING_CONFIRMATION, confirmed));
        service().terminate(sunvibe("The owner's papers were refused."), MANAGER_VIEWER);
        AgreementVersion cancelled = read().getVersions().getFirst();
        assertEquals(VersionStatus.CANCELLED, cancelled.getStatus());
        assertEquals(confirmed.getOwnerAnswerOrNull(), cancelled.getOwnerAnswerOrNull());

        AgreementVersion rejected = version(VersionStatus.OWNER_REJECTED, AHEAD, WALL.minus(Duration.ofDays(2)),
                OwnerAnswer.rejected(OWNER, null, WALL.minus(Duration.ofDays(1))), null);
        stored.store(agreement(AgreementStatus.DRAFTING, rejected));
        service().terminate(unitLet(), SALE_VIEWER);
        assertEquals(List.of(rejected.getStatus()), read().getVersions().stream().map(AgreementVersion::getStatus)
                .toList());
        assertEquals(AgreementStatus.TERMINATED, read().getStatus());
    }

    /** TE-7: a version past its 7 days lapsed at its deadline, then the termination; the version stays LAPSED. */
    @Test
    void terminate_awaitingPastSevenDays_lapsedRowThenTerminated() {
        Instant sentAt = WALL.minus(Duration.ofDays(8));
        stored.store(agreement(AgreementStatus.AWAITING_CONFIRMATION, awaitingOwner(sentAt)));

        service().terminate(ownerWithdrew(), SALE_VIEWER);

        assertEquals(List.of(AgreementEventType.VERSION_LAPSED, AgreementEventType.TERMINATED), types());
        assertEquals(sentAt.plus(AgreementVersion.ANSWER_WINDOW), trail.events().getFirst().at());
        assertEquals(VersionStatus.LAPSED, read().getVersions().getFirst().getStatus());
        assertEquals(AgreementStatus.TERMINATED, read().getStatus());
    }

    /** Review Focus 3: a confirmed version past its 14 days lapsed at the 14-day deadline, then the termination. */
    @Test
    void terminate_confirmedPastActivationWindow_lapsedRowAtFourteenDaysThenTerminated() {
        Instant answeredAt = WALL.minus(Duration.ofDays(15));
        stored.store(agreement(AgreementStatus.AWAITING_CONFIRMATION, ownerConfirmed(answeredAt)));

        service().terminate(ownerWithdrew(), MANAGER_VIEWER);

        assertEquals(List.of(AgreementEventType.VERSION_LAPSED, AgreementEventType.TERMINATED), types());
        assertEquals(answeredAt.plus(AgreementVersion.ACTIVATION_WINDOW), trail.events().getFirst().at());
        assertEquals(VersionStatus.LAPSED, read().getVersions().getFirst().getStatus());
    }

    /** TE-4, S8-D6's rule: a termination of a suspended agreement leaves its open record open. */
    @Test
    void terminate_suspended_recordStaysOpen() {
        suspendedWithOpenRecord(LEAD);

        service().terminate(ownerWithdrew(), MANAGER_VIEWER);

        assertEquals(AgreementStatus.TERMINATED, read().getStatus());
        assertTrue(suspensions.all().getFirst().isOpen());
        assertEquals(1, suspensions.all().size());
    }

    /**
     * S8-D8's narrowing: a row version that moved after the first read is not refused; the update carries the locked
     * read's row version, so the stored one is the first read's plus two.
     */
    @Test
    void terminate_rowMovedAfterFirstRead_updatesFromTheLockedRead() {
        stored.store(agreement(AgreementStatus.ACTIVE, effective(RUNNING)));
        stored.moveAfterNextRead();

        service().terminate(ownerWithdrew(), SALE_VIEWER);

        assertEquals(AgreementStatus.TERMINATED, read().getStatus());
        assertEquals(ROW_VERSION + 2, read().getRowVersionOrNull());
    }

    /** RC-1 at service level: an activation committed between the first read and the lock is what the stop acts on. */
    @Test
    void terminate_activatedBeforeTheLock_actsOnTheLockedState() {
        stored.store(agreement(AgreementStatus.AWAITING_CONFIRMATION, ownerConfirmed(WALL.minus(Duration.ofDays(1)))));
        agreements.beforeNextLock(() -> stored.store(BrokerageAgreement.load(AGREEMENT, UNIT, "P101", "P1",
                TransactionType.LONG_TERM_RENT, OWNER, LEAD, AgreementStatus.ACTIVE, SALE, CREATED, WALL,
                ROW_VERSION + 1, List.of(activated(AHEAD)))));

        service().terminate(ownerWithdrew(), SALE_VIEWER);

        assertEquals(AgreementStatus.TERMINATED, read().getStatus());
        assertEquals(VersionStatus.CANCELLED, read().getVersions().getFirst().getStatus());
        assertNotNull(read().getVersions().getFirst().getActivationOrNull());
        assertEquals(ROW_VERSION + 2, read().getRowVersionOrNull());
    }

    // ==================== cancellation (spec 3.4) ====================

    /** CA-1: the assigned Sale cancels each shape of an agreement that never took effect. */
    @Test
    void cancel_draftingByAssignedSale_cancelledRowEvent() {
        List<BrokerageAgreement> shapes = List.of(agreement(AgreementStatus.DRAFTING, draft()),
                agreement(AgreementStatus.AWAITING_CONFIRMATION, awaitingOwner(WALL.minus(Duration.ofDays(1)))),
                agreement(AgreementStatus.AWAITING_CONFIRMATION, ownerConfirmed(WALL.minus(Duration.ofDays(1)))),
                agreement(AgreementStatus.DRAFTING, version(VersionStatus.OWNER_REJECTED, AHEAD,
                        WALL.minus(Duration.ofDays(2)), OwnerAnswer.rejected(OWNER, null, WALL.minus(Duration.ofDays(1))),
                        null)));
        for (BrokerageAgreement shape : shapes) {
            stored.store(shape);
            VersionStatus before = shape.getVersions().getFirst().getStatus();

            service().cancel(new AgreementCancelCommand(AGREEMENT, ROW_VERSION), SALE_VIEWER);

            BrokerageAgreement after = read();
            assertEquals(AgreementStatus.CANCELLED, after.getStatus(), before.name());
            assertNotNull(after.getEndedAtOrNull());
            assertNull(after.getTerminationOrNull());
            assertEquals(before.isPending() ? VersionStatus.CANCELLED : before,
                    after.getVersions().getFirst().getStatus(), before.name());
            assertEquals(AgreementEventType.CANCELLED, trail.events().getLast().type());
            assertEquals(SALE, trail.events().getLast().actorUserIdOrNull());
        }
        assertEquals(shapes.size(), outbox.events().size());
    }

    /** CA-2: a manager, an agreement in effect, an ended one and a stale data version are refused, writing nothing. */
    @Test
    void cancel_refusals_assigneeOnlyTookEffectEndedStale() {
        stored.store(agreement(AgreementStatus.DRAFTING, draft()));
        assertEquals(Reason.AGREEMENT_ASSIGNEE_ONLY, refused(() -> service().cancel(cancelCommand(), MANAGER_VIEWER)));

        stored.store(agreement(AgreementStatus.ACTIVE, effective(RUNNING)));
        assertEquals(Reason.AGREEMENT_TOOK_EFFECT, refused(() -> service().cancel(cancelCommand(), SALE_VIEWER)));
        stored.store(agreement(AgreementStatus.SUSPENDED, effective(RUNNING)));
        assertEquals(Reason.AGREEMENT_TOOK_EFFECT, refused(() -> service().cancel(cancelCommand(), SALE_VIEWER)));
        stored.store(stopped(AgreementStatus.CANCELLED));
        assertEquals(Reason.AGREEMENT_ENDED, refused(() -> service().cancel(cancelCommand(), SALE_VIEWER)));

        stored.store(agreement(AgreementStatus.DRAFTING, draft()));
        AgreementRefusedException stale = assertThrows(AgreementRefusedException.class,
                () -> service().cancel(new AgreementCancelCommand(AGREEMENT, ROW_VERSION + 1), SALE_VIEWER));
        assertEquals(Reason.CONCURRENT_MODIFICATION, stale.reason());
        assertEquals(List.of(new Entry("expectedDataVersion", null)), stale.entries());
        assertNothingWritten();
    }

    /** CA-4: a version past its 7 days lapsed at its deadline, then the cancellation; the version stays LAPSED. */
    @Test
    void cancel_awaitingPastSevenDays_lapsedRowThenCancelled() {
        Instant sentAt = WALL.minus(Duration.ofDays(9));
        stored.store(agreement(AgreementStatus.AWAITING_CONFIRMATION, awaitingOwner(sentAt)));

        service().cancel(cancelCommand(), SALE_VIEWER);

        assertEquals(List.of(AgreementEventType.VERSION_LAPSED, AgreementEventType.CANCELLED), types());
        assertEquals(sentAt.plus(AgreementVersion.ANSWER_WINDOW), trail.events().getFirst().at());
        assertEquals(VersionStatus.LAPSED, read().getVersions().getFirst().getStatus());
        assertEquals(AgreementStatus.CANCELLED, read().getStatus());
    }

    // ==================== all four ====================

    /**
     * S8-D8 step 3: a later version on another basis lead committed before the lock answers Spring's optimistic-locking
     * failure, which the handler answers 409 CONCURRENT_MODIFICATION without an entry, and writes nothing.
     */
    @Test
    void stops_basisMoved_concurrentModificationNoEntry() {
        for (Executable stop : List.<Executable>of(
                () -> service().suspend(suspendCommand(), MANAGER_VIEWER),
                () -> service().releaseSuspension(releaseCommand(ROW_VERSION), MANAGER_VIEWER),
                () -> service().terminate(ownerWithdrew(), SALE_VIEWER),
                () -> service().cancel(cancelCommand(), SALE_VIEWER))) {
            stored.store(agreement(AgreementStatus.ACTIVE, effective(RUNNING)));
            agreements.beforeNextLock(() -> stored.store(BrokerageAgreement.load(AGREEMENT, UNIT, "P101", "P1",
                    TransactionType.LONG_TERM_RENT, OWNER, "L9", AgreementStatus.DRAFTING, SALE, CREATED, WALL,
                    ROW_VERSION + 1, List.of(version(VersionStatus.OWNER_REJECTED, AHEAD, WALL.minus(Duration.ofDays(2)),
                            OwnerAnswer.rejected(OWNER, null, WALL.minus(Duration.ofDays(1))), null),
                            AgreementVersion.load("V2", 2, VersionStatus.DRAFT, AHEAD, "L9", "I9", SALE, WALL, WALL)))));

            assertThrows(OptimisticLockingFailureException.class, stop);
            assertNothingWritten();
        }
    }

    @Test
    void stops_vanishedBeforeTheLock_notFound() {
        for (Executable stop : List.<Executable>of(
                () -> service().suspend(suspendCommand(), MANAGER_VIEWER),
                () -> service().releaseSuspension(releaseCommand(ROW_VERSION), MANAGER_VIEWER),
                () -> service().terminate(ownerWithdrew(), SALE_VIEWER),
                () -> service().cancel(cancelCommand(), SALE_VIEWER))) {
            stored.store(agreement(AgreementStatus.ACTIVE, effective(RUNNING)));
            agreements.vanishOnNextLock();

            assertThrows(BrokerageNotFoundException.class, stop);
            assertNothingWritten();
        }
    }

    /**
     * Every refusal comes before the first write: unknown and malformed ids, a lead the actor may not see, a body that
     * breaks its rule (checked before any read), a refused permission or state: no update, row, record or event.
     */
    @Test
    void stops_refused_writeNothing() {
        stored.store(agreement(AgreementStatus.ACTIVE, effective(RUNNING)));
        assertEquals("note", assertThrows(AgreementContentException.class, () -> service().suspend(
                new AgreementSuspendCommand(AGREEMENT, SuspensionReasonType.COMPLAINT, "A‮B"), MANAGER_VIEWER))
                .field());
        assertEquals("reason", assertThrows(AgreementContentException.class, () -> service().releaseSuspension(
                new AgreementReleaseSuspensionCommand(AGREEMENT, ROW_VERSION, "   "), MANAGER_VIEWER)).field());
        assertEquals("reportedOn", assertThrows(AgreementContentException.class, () -> service().terminate(
                new AgreementTerminateCommand(AGREEMENT, TerminationType.UNIT_LET, TODAY.plusDays(1), null,
                        IntroducedBySunvibe.NO, null), SALE_VIEWER)).field());
        assertEquals(List.of(), calls);
        assertEquals(List.of(), stored.calls());

        StaffViewer stranger = new StaffViewer(OTHER_SALE, Set.of("ROLE_SALE"));
        List<Executable> notFound = List.of(
                () -> service().suspend(new AgreementSuspendCommand("01JUNKNOWN0000000000000000",
                        SuspensionReasonType.COMPLAINT, null), MANAGER_VIEWER),
                () -> service().suspend(new AgreementSuspendCommand("not-an-id", SuspensionReasonType.COMPLAINT,
                        null), MANAGER_VIEWER),
                () -> service().terminate(ownerWithdrew(), stranger),
                () -> service().cancel(cancelCommand(), stranger));
        for (Executable call : notFound) {
            assertThrows(BrokerageNotFoundException.class, call);
        }
        assertEquals(Reason.AGREEMENT_ASSIGNEE_ONLY, refused(() -> service().terminate(unitLet(), ADMIN_VIEWER)));
        assertNothingWritten();
    }

    /** brokerage-agreement BR-17: the commands' texts stay out of every log line. */
    @Test
    void stopCommands_toString_withoutTexts() {
        String suspend = new AgreementSuspendCommand(AGREEMENT, SuspensionReasonType.COMPLAINT, "Secret note")
                .toString();
        String release = new AgreementReleaseSuspensionCommand(AGREEMENT, 3, "Secret reason").toString();
        String terminate = new AgreementTerminateCommand(AGREEMENT, TerminationType.OWNER_WITHDREW,
                LocalDate.parse("2026-12-01"), WithdrawalChannel.PHONE_CALL, null, "Secret note").toString();

        assertFalse(suspend.contains("Secret"), suspend);
        assertFalse(release.contains("Secret"), release);
        assertFalse(terminate.contains("Secret"), terminate);
        assertFalse(terminate.contains("2026-12-01"), terminate);
        assertFalse(terminate.contains("PHONE_CALL"), terminate);
    }

    // ==================== fixtures ====================

    private BrokerageAgreement read() {
        return stored.all().stream().filter(agreement -> agreement.getId().equals(AGREEMENT)).findFirst().orElseThrow();
    }

    private List<AgreementEventType> types() {
        return trail.events().stream().map(Event::type).toList();
    }

    private void assertNothingWritten() {
        assertEquals(0, stored.count("update"));
        assertEquals(List.of(), trail.events());
        assertEquals(List.of(), outbox.events());
        assertEquals(List.of(), notices.agreementPending());
        assertTrue(suspensions.all().stream().noneMatch(record -> record.suspendedBy().equals(MANAGER)
                && record.suspendedAt().isAfter(WALL)));
    }

    private static Reason refused(Executable call) {
        return assertThrows(AgreementRefusedException.class, call).reason();
    }

    private void suspendedWithOpenRecord(String leadId) {
        stored.store(BrokerageAgreement.load(AGREEMENT, UNIT, "P101", "P1", TransactionType.LONG_TERM_RENT, OWNER, leadId,
                AgreementStatus.SUSPENDED, SALE, CREATED, CREATED, ROW_VERSION, List.of(version(VersionStatus.EFFECTIVE,
                        RUNNING, CREATED, confirmedAnswer(CREATED.plusSeconds(60)), activation(), leadId))));
        suspensions.store(AgreementSuspension.open("01JSUSPENSION0000000000001", AGREEMENT,
                SuspensionReasonType.COMPLAINT, "A complaint.", MANAGER, WALL.minus(Duration.ofDays(2))));
    }

    private static AgreementSuspendCommand suspendCommand() {
        return new AgreementSuspendCommand(AGREEMENT, SuspensionReasonType.DISPUTE, null);
    }

    private static AgreementReleaseSuspensionCommand releaseCommand(long dataVersion) {
        return new AgreementReleaseSuspensionCommand(AGREEMENT, dataVersion, "The check is done.");
    }

    private static AgreementCancelCommand cancelCommand() {
        return new AgreementCancelCommand(AGREEMENT, ROW_VERSION);
    }

    private static AgreementTerminateCommand ownerWithdrew() {
        return new AgreementTerminateCommand(AGREEMENT, TerminationType.OWNER_WITHDREW, TODAY,
                WithdrawalChannel.PHONE_CALL, null, null);
    }

    private static AgreementTerminateCommand unitLet() {
        return new AgreementTerminateCommand(AGREEMENT, TerminationType.UNIT_LET, TODAY, null,
                IntroducedBySunvibe.UNKNOWN, null);
    }

    private static AgreementTerminateCommand sunvibe(String note) {
        return new AgreementTerminateCommand(AGREEMENT, TerminationType.SUNVIBE_TERMINATED, null, null, null, note);
    }

    private static BrokerageAgreement agreement(AgreementStatus status, AgreementVersion version) {
        return BrokerageAgreement.load(AGREEMENT, UNIT, "P101", "P1", TransactionType.LONG_TERM_RENT, OWNER, LEAD,
                status, SALE, CREATED, CREATED, ROW_VERSION, List.of(version));
    }

    private static BrokerageAgreement stopped(AgreementStatus status) {
        return BrokerageAgreement.load(AGREEMENT, UNIT, "P101", "P1", TransactionType.LONG_TERM_RENT, OWNER, LEAD,
                status, SALE, CREATED, CREATED, ROW_VERSION, List.of(version(VersionStatus.CANCELLED, AHEAD, null, null,
                        null)), WALL.minus(Duration.ofDays(1)), status == AgreementStatus.TERMINATED
                        ? new AgreementTermination(TerminationType.SUNVIBE_TERMINATED, null, null, null, "Ended.",
                        WALL.minus(Duration.ofDays(1))) : null);
    }

    private static AgreementVersion draft() {
        return version(VersionStatus.DRAFT, AHEAD, null, null, null);
    }

    private static AgreementVersion awaitingOwner(Instant sentAt) {
        return version(VersionStatus.AWAITING_OWNER, AHEAD, sentAt, null, null);
    }

    private static AgreementVersion ownerConfirmed(Instant answeredAt) {
        return version(VersionStatus.OWNER_CONFIRMED, AHEAD, answeredAt.minus(Duration.ofHours(1)),
                confirmedAnswer(answeredAt), null);
    }

    private static AgreementVersion activated(AgreementPeriod period) {
        return version(VersionStatus.ACTIVATED, period, CREATED, confirmedAnswer(CREATED.plusSeconds(60)),
                activation());
    }

    private static AgreementVersion effective(AgreementPeriod period) {
        return version(VersionStatus.EFFECTIVE, period, CREATED, confirmedAnswer(CREATED.plusSeconds(60)),
                activation());
    }

    private static OwnerAnswer confirmedAnswer(Instant at) {
        return OwnerAnswer.confirmed(OWNER, AgreementStatement.VERSION, at);
    }

    private static ActivationDecision activation() {
        return ActivationDecision.activated(ADMIN, CREATED.plusSeconds(120), ActivationStatement.VERSION,
                new SignedDocument("HDMG-1", CREATED.atZone(AgreementVersion.VIETNAM).toLocalDate(), "A", "B"));
    }

    private static AgreementVersion version(VersionStatus status, AgreementPeriod period, Instant sentAt,
                                            OwnerAnswer answer, ActivationDecision decision) {
        return version(status, period, sentAt, answer, decision, LEAD);
    }

    private static AgreementVersion version(VersionStatus status, AgreementPeriod period, Instant sentAt,
                                            OwnerAnswer answer, ActivationDecision decision, String leadId) {
        return AgreementVersion.load("V1", 1, status, period, leadId, "I1", SALE, CREATED, CREATED, CONSENT,
                VersionTerms.NONE, vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageFee.NONE, sentAt, answer,
                decision);
    }
}
