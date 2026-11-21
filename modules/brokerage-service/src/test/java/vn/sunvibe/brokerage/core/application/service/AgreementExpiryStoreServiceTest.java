package vn.sunvibe.brokerage.core.application.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;
import vn.sunvibe.brokerage.core.application.command.AgreementCreateCommand;
import vn.sunvibe.brokerage.core.application.exception.AgreementRefusedException;
import vn.sunvibe.brokerage.core.application.exception.AgreementRefusedException.Reason;
import vn.sunvibe.brokerage.core.application.shared.external.property.IntakeBasisFacts;
import vn.sunvibe.brokerage.core.application.shared.external.property.IntakeBasisFacts.Holding;
import vn.sunvibe.brokerage.core.application.shared.external.property.IntakeBasisFacts.Unit;
import vn.sunvibe.brokerage.core.application.usecase.AgreementCommandUseCase;
import vn.sunvibe.brokerage.core.application.usecase.AgreementCreateResult;
import vn.sunvibe.brokerage.core.application.usecase.StaffViewer;
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
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageFee;
import vn.sunvibe.brokerage.core.domain.model.agreement.OwnerAnswer;
import vn.sunvibe.brokerage.core.domain.model.agreement.PhotoConsent;
import vn.sunvibe.brokerage.core.domain.model.agreement.SignedDocument;
import vn.sunvibe.brokerage.core.domain.model.agreement.SuspensionReasonType;
import vn.sunvibe.brokerage.core.domain.model.agreement.TerminationType;
import vn.sunvibe.brokerage.core.domain.model.agreement.TransactionType;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionStatus;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionTerms;
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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stored expiry (brokerage-agreement BR-5, BR-6, US-5 AC-1; E05-S8 spec S8-D2 to S8-D4, EX-2, EX-4, EX-6; plan
 * E05S8-P3, P6, P7): the store at a fixed instant through its seam, the transitions due before the end stored first in
 * time order, no event and no notice; and the create's step that stores a due expiry of the unit's earlier agreement
 * before it inserts. The create reads the wall clock, so its fixtures are dated from it.
 */
class AgreementExpiryStoreServiceTest {

    private static final String LEAD = "L1";
    private static final String SALE = "S";
    private static final String OWNER = "O";
    private static final StaffViewer SALE_VIEWER = new StaffViewer(SALE, Set.of("ROLE_SALE"));
    private static final String AGREEMENT = "01JEXPIRYAGREEMENT00000001";
    private static final String OTHER = "01JEXPIRYAGREEMENT00000002";
    private static final long ROW_VERSION = 5L;
    private static final LocalDate END = LocalDate.parse("2027-06-30");
    private static final AgreementPeriod PERIOD = new AgreementPeriod(LocalDate.parse("2027-01-01"), END);
    private static final Instant STARTS_AT = Instant.parse("2026-12-31T17:00:00Z");
    private static final Instant ENDS_AT = Instant.parse("2027-06-30T17:00:00Z");
    private static final Instant AFTER_END = Instant.parse("2027-07-01T03:00:00Z");
    private static final Instant CREATED = Instant.parse("2026-11-01T03:00:00Z");
    private static final Instant WALL = Instant.now().truncatedTo(ChronoUnit.MICROS);
    private static final LocalDate TODAY = AgreementVersion.todayInVietnam(WALL);

    private final List<String> calls = new ArrayList<>();
    private final InMemoryBasisLeadPort leads = new InMemoryBasisLeadPort(calls).lead(LEAD, SALE, SALE);
    private final InMemoryIntakeBasisPort bases = new InMemoryIntakeBasisPort(calls).basis(LEAD,
            new IntakeBasisFacts("I1", "P1", "USABLE", List.of(new Unit("U1", "P101", List.of())), OWNER,
                    List.of(new Holding(OWNER, "OWNER", "UNVERIFIED"))));
    private final RecordingOwnerAccountPort accounts = new RecordingOwnerAccountPort(calls).answer(OWNER, true);
    private final InMemoryAgreementRepository stored = new InMemoryAgreementRepository();
    private final ChangeBeforeLockAgreementRepository agreements = new ChangeBeforeLockAgreementRepository(stored);
    private final RecordingAgreementTrail trail = new RecordingAgreementTrail();
    private final RecordingEventOutboxPort outbox = new RecordingEventOutboxPort();
    private final RecordingOwnerNoticePort notices = new RecordingOwnerNoticePort();
    private final InMemoryAgreementSuspensionRepository suspensions = new InMemoryAgreementSuspensionRepository();
    private final AtomicInteger sequence = new AtomicInteger();

    private AgreementCommandService service() {
        return new AgreementCommandService(leads, bases, accounts, agreements, trail,
                () -> String.format("01JNEW%020d", sequence.incrementAndGet()), notices, new FixedAgreementSwitches(),
                new RecordingAccountPasswordPort(), new TransactionTemplate(new NoDatabaseTransactions()), outbox,
                suspensions);
    }

    // ==================== the store (EX-2) ====================

    /** EX-2: EXPIRED with its end, the row version raised, one EXPIRED row at the end without an actor, no event. */
    @Test
    void storeDueExpiriesAt_endedYesterday_expiredRowNoActorNoEvent() {
        stored.store(agreement(AGREEMENT, AgreementStatus.ACTIVE, effective(PERIOD)));

        int count = service().storeDueExpiriesAt(AFTER_END);

        assertEquals(1, count);
        BrokerageAgreement after = read(AGREEMENT);
        assertEquals(AgreementStatus.EXPIRED, after.getStatus());
        assertEquals(ENDS_AT, after.getEndedAtOrNull());
        assertEquals(ROW_VERSION + 1, after.getRowVersionOrNull());
        assertEquals(VersionStatus.EFFECTIVE, after.getVersions().getFirst().getStatus());
        assertEquals(List.of(new Event(AGREEMENT, 1, AgreementEventType.EXPIRED, null, ENDS_AT)), trail.events());
        assertEquals(List.of(), outbox.events());
        assertEquals(List.of(), notices.agreementPending());
        assertEquals(List.of(), notices.agreementActivated());
    }

    /**
     * EX-2, plan E05S8-P3: an activated version that started and ended with neither stored is stored EFFECTIVE with its
     * activation record, never CANCELLED; its start's row comes before the expiry's.
     */
    @Test
    void storeDueExpiriesAt_startNotStored_startedRowThenExpired() {
        stored.store(agreement(AGREEMENT, AgreementStatus.ACTIVE, activated(PERIOD)));

        service().storeDueExpiriesAt(AFTER_END);

        BrokerageAgreement after = read(AGREEMENT);
        assertEquals(AgreementStatus.EXPIRED, after.getStatus());
        assertEquals(ENDS_AT, after.getEndedAtOrNull());
        AgreementVersion version = after.getVersions().getFirst();
        assertEquals(VersionStatus.EFFECTIVE, version.getStatus());
        assertEquals(activation(), version.getActivationOrNull());
        assertEquals(List.of(new Event(AGREEMENT, 1, AgreementEventType.VERSION_STARTED, null, STARTS_AT),
                new Event(AGREEMENT, 1, AgreementEventType.EXPIRED, null, ENDS_AT)), trail.events());
    }

    @Test
    void storeDueExpiriesAt_secondRun_storesNothing() {
        stored.store(agreement(AGREEMENT, AgreementStatus.ACTIVE, effective(PERIOD)));
        service().storeDueExpiriesAt(AFTER_END);

        int second = service().storeDueExpiriesAt(AFTER_END.plusSeconds(60));

        assertEquals(0, second);
        assertEquals(1, trail.events().size());
        assertEquals(ROW_VERSION + 1, read(AGREEMENT).getRowVersionOrNull());
    }

    @Test
    void storeDueExpiriesAt_moreThanHundredDue_atMostHundred() {
        for (int index = 0; index < 101; index++) {
            stored.store(agreement(String.format("01JEXPIRYMANY%013d", index), AgreementStatus.ACTIVE,
                    effective(PERIOD)));
        }

        int count = service().storeDueExpiriesAt(AFTER_END);

        assertEquals(AgreementCommandUseCase.MAX_EXPIRIES_PER_RUN, count);
        assertEquals(100, stored.all().stream().filter(agreement -> agreement.getStatus() == AgreementStatus.EXPIRED)
                .count());
        assertEquals(AgreementStatus.ACTIVE, read(String.format("01JEXPIRYMANY%013d", 100)).getStatus());
    }

    /** The failure rule of the stores: a fault of one agreement is logged with its id and class name; the run goes on. */
    @Test
    void storeDueExpiriesAt_oneFails_loggedAndRunGoesOn() {
        stored.store(agreement(AGREEMENT, AgreementStatus.ACTIVE, effective(PERIOD)));
        stored.store(agreement(OTHER, AgreementStatus.ACTIVE, effective(PERIOD)));
        stored.failNextUpdate(AGREEMENT, new IllegalStateException("the store refused"));
        int[] count = new int[1];

        List<ILoggingEvent> lines = logsOf(() -> count[0] = service().storeDueExpiriesAt(AFTER_END));

        assertEquals(1, count[0]);
        assertEquals(AgreementStatus.ACTIVE, read(AGREEMENT).getStatus());
        assertEquals(AgreementStatus.EXPIRED, read(OTHER).getStatus());
        assertEquals(1, lines.size(), lines.toString());
        String text = lines.getFirst().getFormattedMessage();
        assertTrue(text.contains("BROKERAGE_TRANSITION_FAILED"), text);
        assertTrue(text.contains(AGREEMENT), text);
        assertTrue(!text.contains("the store refused"), text);
    }

    /** A-2: the last microsecond of the end date in Vietnam stores nothing; the start of the next day stores it. */
    @Test
    void storeDueExpiriesAt_vietnamMidnight_selectsFromTheNextDay() {
        stored.store(agreement(AGREEMENT, AgreementStatus.ACTIVE, effective(PERIOD)));

        assertEquals(0, service().storeDueExpiriesAt(Instant.parse("2027-06-30T16:59:59.999999Z")));
        assertEquals(AgreementStatus.ACTIVE, read(AGREEMENT).getStatus());

        assertEquals(1, service().storeDueExpiriesAt(Instant.parse("2027-06-30T17:00:00Z")));
        assertEquals(AgreementStatus.EXPIRED, read(AGREEMENT).getStatus());
    }

    /** EX-6 at service level: a suspended agreement expires with its open record left open. */
    @Test
    void storeDueExpiriesAt_suspended_recordStaysOpen() {
        stored.store(agreement(AGREEMENT, AgreementStatus.SUSPENDED, effective(PERIOD)));
        suspensions.store(AgreementSuspension.open("01JSUSPENSION0000000000001", AGREEMENT,
                SuspensionReasonType.COMPLAINT, null, "M", ENDS_AT.minus(Duration.ofDays(5))));

        service().storeDueExpiriesAt(AFTER_END);

        assertEquals(AgreementStatus.EXPIRED, read(AGREEMENT).getStatus());
        assertTrue(suspensions.all().getFirst().isOpen());
    }

    // ==================== the create's due expiry (EX-4, plan E05S8-P7) ====================

    /** EX-4: the unit's earlier agreement past its end is stored EXPIRED with its row, then the new one inserted. */
    @Test
    void create_earlierEndedNotStored_storesExpiryThenCreates() {
        AgreementPeriod endedYesterday = new AgreementPeriod(TODAY.minusMonths(6), TODAY.minusDays(1));
        stored.store(agreement(AGREEMENT, AgreementStatus.ACTIVE, effective(endedYesterday)));

        AgreementCreateResult result = service().create(createCommand(), SALE_VIEWER);

        BrokerageAgreement earlier = read(AGREEMENT);
        assertEquals(AgreementStatus.EXPIRED, earlier.getStatus());
        Instant endsAt = earlier.getVersions().getFirst().endsAt();
        assertEquals(endsAt, earlier.getEndedAtOrNull());
        assertEquals(List.of(AgreementEventType.EXPIRED, AgreementEventType.CREATED),
                trail.events().stream().map(Event::type).toList());
        assertEquals(new Event(AGREEMENT, 1, AgreementEventType.EXPIRED, null, endsAt), trail.events().getFirst());
        assertEquals(result.agreementId(), trail.events().get(1).agreementId());
        assertEquals(List.of("update", "create"), stored.calls().stream()
                .filter(call -> call.equals("update") || call.equals("create")).toList());
    }

    /** EX-4, its due-start clause: the start comes first, the version EFFECTIVE with its record, then the expiry. */
    @Test
    void create_earlierStartAndEndNotStored_startedThenExpiredThenCreates() {
        AgreementPeriod past = new AgreementPeriod(TODAY.minusMonths(6), TODAY.minusDays(1));
        AgreementVersion activated = activated(past);
        stored.store(agreement(AGREEMENT, AgreementStatus.ACTIVE, activated));

        service().create(createCommand(), SALE_VIEWER);

        BrokerageAgreement earlier = read(AGREEMENT);
        assertEquals(AgreementStatus.EXPIRED, earlier.getStatus());
        assertEquals(VersionStatus.EFFECTIVE, earlier.getVersions().getFirst().getStatus());
        assertEquals(activation(), earlier.getVersions().getFirst().getActivationOrNull());
        assertEquals(List.of(AgreementEventType.VERSION_STARTED, AgreementEventType.EXPIRED, AgreementEventType.CREATED),
                trail.events().stream().map(Event::type).toList());
        assertEquals(activated.startsAt(), trail.events().get(0).at());
        assertEquals(activated.endsAt(), trail.events().get(1).at());
        assertEquals(2, stored.all().size());
    }

    @Test
    void create_earlierUnfinished_agreementExistsNothingWritten() {
        AgreementPeriod endsToday = new AgreementPeriod(TODAY.minusMonths(6), TODAY);
        stored.store(agreement(AGREEMENT, AgreementStatus.ACTIVE, effective(endsToday)));

        AgreementRefusedException refused = assertThrows(AgreementRefusedException.class,
                () -> service().create(createCommand(), SALE_VIEWER));

        assertEquals(Reason.AGREEMENT_EXISTS, refused.reason());
        assertEquals(0, stored.count("update"));
        assertEquals(0, stored.count("create"));
        assertEquals(List.of(), trail.events());
        assertEquals(AgreementStatus.ACTIVE, read(AGREEMENT).getStatus());
    }

    /** A termination committed between the lookup and the lock: the earlier agreement gets no write; the insert runs. */
    @Test
    void create_earlierStoredFinal_writesNothingForIt() {
        AgreementPeriod endedYesterday = new AgreementPeriod(TODAY.minusMonths(6), TODAY.minusDays(1));
        stored.store(agreement(AGREEMENT, AgreementStatus.ACTIVE, effective(endedYesterday)));
        Instant terminatedAt = WALL.minus(Duration.ofDays(2));
        BrokerageAgreement terminated = BrokerageAgreement.load(AGREEMENT, "U1", "P101", "P1",
                TransactionType.LONG_TERM_RENT, OWNER, LEAD, AgreementStatus.TERMINATED, SALE, CREATED, terminatedAt,
                ROW_VERSION + 1, List.of(effective(endedYesterday)), terminatedAt, new AgreementTermination(
                        TerminationType.SUNVIBE_TERMINATED, null, null, null, "Ended.", terminatedAt));
        agreements.beforeNextLock(() -> stored.store(terminated));

        service().create(createCommand(), SALE_VIEWER);

        assertEquals(0, stored.count("update"));
        assertEquals(AgreementStatus.TERMINATED, read(AGREEMENT).getStatus());
        assertEquals(ROW_VERSION + 1, read(AGREEMENT).getRowVersionOrNull());
        assertEquals(List.of(AgreementEventType.CREATED), trail.events().stream().map(Event::type).toList());
        assertEquals(1, stored.count("create"));
    }

    // ==================== fixtures ====================

    private BrokerageAgreement read(String id) {
        return stored.all().stream().filter(agreement -> agreement.getId().equals(id)).findFirst().orElseThrow();
    }

    private static AgreementCreateCommand createCommand() {
        return new AgreementCreateCommand(LEAD, "U1", "LONG_TERM_RENT", TODAY.plusDays(10), TODAY.plusMonths(6));
    }

    private static BrokerageAgreement agreement(String id, AgreementStatus status, AgreementVersion version) {
        return BrokerageAgreement.load(id, "U1", "P101", "P1", TransactionType.LONG_TERM_RENT, OWNER, LEAD, status, SALE,
                CREATED, CREATED, ROW_VERSION, List.of(version));
    }

    private static AgreementVersion effective(AgreementPeriod period) {
        return version(VersionStatus.EFFECTIVE, period);
    }

    private static AgreementVersion activated(AgreementPeriod period) {
        return version(VersionStatus.ACTIVATED, period);
    }

    private static AgreementVersion version(VersionStatus status, AgreementPeriod period) {
        return AgreementVersion.load("V1", 1, status, period, LEAD, "I1", SALE, CREATED, CREATED,
                new PhotoConsent(true, false), VersionTerms.NONE, BrokerageFee.NONE, CREATED.plusSeconds(60),
                OwnerAnswer.confirmed(OWNER, AgreementStatement.VERSION, CREATED.plusSeconds(120)), activation());
    }

    private static ActivationDecision activation() {
        return ActivationDecision.activated("M", CREATED.plusSeconds(180), ActivationStatement.VERSION,
                new SignedDocument("HDMG-1", LocalDate.parse("2026-11-01"), "A", "B"));
    }

    /** The lines the service logs while the work runs. */
    private static List<ILoggingEvent> logsOf(Runnable work) {
        Logger logger = (Logger) LoggerFactory.getLogger(AgreementCommandService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            work.run();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
        return List.copyOf(appender.list);
    }
}
