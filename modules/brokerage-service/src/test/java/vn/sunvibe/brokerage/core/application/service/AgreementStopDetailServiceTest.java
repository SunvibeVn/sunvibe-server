package vn.sunvibe.brokerage.core.application.service;

import org.junit.jupiter.api.Test;
import vn.sunvibe.brokerage.core.application.query.AgreementDetailView;
import vn.sunvibe.brokerage.core.application.query.AgreementDetailView.SuspensionView;
import vn.sunvibe.brokerage.core.application.query.AgreementDetailView.TerminationView;
import vn.sunvibe.brokerage.core.application.query.AgreementListItemView;
import vn.sunvibe.brokerage.core.application.query.OwnerAgreementView;
import vn.sunvibe.brokerage.core.application.shared.external.lead.LeadScopeFacts;
import vn.sunvibe.brokerage.core.application.shared.external.property.IntakeBasisFacts;
import vn.sunvibe.brokerage.core.application.shared.external.property.PropertyOwnerFacts;
import vn.sunvibe.brokerage.core.application.shared.external.property.UnitBasisFacts;
import vn.sunvibe.brokerage.core.application.usecase.AgreementListQuery;
import vn.sunvibe.brokerage.core.application.usecase.AgreementStandingView;
import vn.sunvibe.brokerage.core.application.usecase.StaffViewer;
import vn.sunvibe.brokerage.core.domain.model.agreement.ActivationDecision;
import vn.sunvibe.brokerage.core.domain.model.agreement.ActivationStatement;
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
import vn.sunvibe.brokerage.core.domain.model.agreement.StopAction;
import vn.sunvibe.brokerage.core.domain.model.agreement.SuspensionReasonType;
import vn.sunvibe.brokerage.core.domain.model.agreement.TerminationType;
import vn.sunvibe.brokerage.core.domain.model.agreement.TransactionType;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionStatus;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionTerms;
import vn.sunvibe.brokerage.core.domain.model.agreement.WithdrawalChannel;
import vn.sunvibe.brokerage.core.domain.repository.AgreementIdPage;
import vn.sunvibe.brokerage.core.testsupport.InMemoryAgreementRepository;
import vn.sunvibe.brokerage.core.testsupport.InMemoryAgreementSuspensionRepository;
import vn.sunvibe.brokerage.core.testsupport.InMemoryBasisLeadAssigneePort;
import vn.sunvibe.brokerage.core.testsupport.InMemoryBasisLeadPort;
import vn.sunvibe.brokerage.core.testsupport.InMemoryBasisLeadScopePort;
import vn.sunvibe.brokerage.core.testsupport.InMemoryIntakeOriginPort;
import vn.sunvibe.brokerage.core.testsupport.InMemoryOwnerEligibilityPort;
import vn.sunvibe.brokerage.core.testsupport.InMemorySourceTermsPort;
import vn.sunvibe.brokerage.core.testsupport.InMemoryUnitBasisPort;
import vn.sunvibe.brokerage.core.testsupport.InMemoryUnitLocationPort;
import vn.sunvibe.brokerage.core.testsupport.RecordingAgreementListRepository;
import vn.sunvibe.brokerage.core.testsupport.RecordingAgreementTrail;
import vn.sunvibe.brokerage.core.testsupport.RecordingOwnerAccountPort;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The staff detail of a stop (brokerage-agreement BR-14 to BR-17; E05-S8 spec S8-D11, EX-1, RL-3 at service level): the
 * end, the termination record, the newest 20 suspension records, and the stops the viewer may take from the stop rule;
 * an agreement past its end reads EXPIRED in the detail, the list, the owner's read and the standings alike. The reads
 * take their clock from AppClock, so the fixtures are dated from the JVM's today in Vietnam.
 */
class AgreementStopDetailServiceTest {

    private static final String AGREEMENT = "01JDETAILAGREEMENT00000001";
    private static final String LEAD = "L1";
    private static final String LEADER_LEAD = "L2";
    private static final String SALE = "S";
    private static final String OWNER = "O";
    private static final String UNIT = "01JQ7T9Z8XW4H3M5N6P2R0S001";
    private static final StaffViewer SALE_VIEWER = new StaffViewer(SALE, Set.of("ROLE_SALE"));
    private static final StaffViewer MANAGER_VIEWER = new StaffViewer("M", Set.of("ROLE_SALES_MANAGER",
            "ROLE_SALE_LEADER", "ROLE_SALE"));
    private static final StaffViewer LEADER_VIEWER = new StaffViewer("LD", Set.of("ROLE_SALE_LEADER", "ROLE_SALE"));
    private static final Instant WALL = Instant.now().truncatedTo(ChronoUnit.MICROS);
    private static final LocalDate TODAY = AgreementVersion.todayInVietnam(WALL);
    private static final Instant CREATED = WALL.minus(Duration.ofDays(400));
    private static final AgreementPeriod RUNNING = new AgreementPeriod(TODAY.minusDays(10), TODAY.plusMonths(6));

    private final List<String> calls = new ArrayList<>();
    private final InMemoryBasisLeadPort leads = new InMemoryBasisLeadPort(calls).lead(LEAD, SALE, SALE, "M", "LD")
            .lead(LEADER_LEAD, "LD", "LD", "M");
    private final InMemoryAgreementRepository agreements = new InMemoryAgreementRepository();
    private final RecordingAgreementTrail trail = new RecordingAgreementTrail();
    private final InMemoryUnitBasisPort unitBases = new InMemoryUnitBasisPort();
    private final InMemoryOwnerEligibilityPort eligibility = new InMemoryOwnerEligibilityPort();
    private final AdvertisingRightAssembler assembler = new AdvertisingRightAssembler(unitBases, eligibility);
    private final InMemoryAgreementSuspensionRepository suspensions = new InMemoryAgreementSuspensionRepository();
    private final AgreementQueryService service = new AgreementQueryService(agreements, leads, trail,
            new InMemoryIntakeOriginPort(calls), new InMemorySourceTermsPort(calls),
            new RecordingOwnerAccountPort(calls).answer(OWNER, true), new InMemoryUnitLocationPort(), assembler,
            suspensions);

    /** S8-D11's table through the detail: per viewer and effective state, in the order of StopAction. */
    @Test
    void detail_viewerStopActions_perViewerAndState() {
        agreements.store(agreement(LEAD, AgreementStatus.DRAFTING, draft()));
        assertEquals(List.of(StopAction.CANCEL, StopAction.TERMINATE_OWNER_WITHDREW, StopAction.TERMINATE_UNIT_LET),
                service.detail(AGREEMENT, SALE_VIEWER).viewerStopActions());

        agreements.store(agreement(LEAD, AgreementStatus.ACTIVE, effective(RUNNING)));
        assertEquals(List.of(StopAction.SUSPEND, StopAction.TERMINATE_OWNER_WITHDREW, StopAction.TERMINATE_SUNVIBE),
                service.detail(AGREEMENT, MANAGER_VIEWER).viewerStopActions());

        agreements.store(agreement(LEAD, AgreementStatus.SUSPENDED, effective(RUNNING)));
        assertEquals(List.of(StopAction.RELEASE_SUSPENSION, StopAction.TERMINATE_OWNER_WITHDREW,
                StopAction.TERMINATE_SUNVIBE), service.detail(AGREEMENT, MANAGER_VIEWER).viewerStopActions());

        agreements.store(agreement(LEADER_LEAD, AgreementStatus.SUSPENDED, effective(RUNNING, LEADER_LEAD)));
        assertEquals(List.of(StopAction.TERMINATE_OWNER_WITHDREW, StopAction.TERMINATE_UNIT_LET,
                StopAction.TERMINATE_SUNVIBE), service.detail(AGREEMENT, LEADER_VIEWER).viewerStopActions());

        Instant ended = WALL.minus(Duration.ofDays(1));
        agreements.store(BrokerageAgreement.load(AGREEMENT, UNIT, "P101", "P1", TransactionType.LONG_TERM_RENT, OWNER,
                LEAD, AgreementStatus.CANCELLED, SALE, CREATED, ended, 3L, List.of(version(VersionStatus.CANCELLED,
                        RUNNING, LEAD)), ended, null));
        for (StaffViewer viewer : List.of(SALE_VIEWER, MANAGER_VIEWER, LEADER_VIEWER)) {
            assertEquals(List.of(), service.detail(AGREEMENT, viewer).viewerStopActions(), viewer.userId());
        }
    }

    @Test
    void detail_terminated_endedAtAndTermination() {
        Instant ended = WALL.minus(Duration.ofHours(3));
        AgreementTermination termination = new AgreementTermination(TerminationType.OWNER_WITHDREW,
                TODAY.minusDays(1), WithdrawalChannel.MESSAGE, null, "Sold the unit.", ended);
        agreements.store(BrokerageAgreement.load(AGREEMENT, UNIT, "P101", "P1", TransactionType.LONG_TERM_RENT, OWNER,
                LEAD, AgreementStatus.TERMINATED, SALE, CREATED, ended, 4L, List.of(effective(RUNNING)), ended,
                termination));

        AgreementDetailView view = service.detail(AGREEMENT, SALE_VIEWER);

        assertEquals(AgreementStatus.TERMINATED, view.status());
        assertEquals(ended, view.endedAtOrNull());
        assertEquals(new TerminationView(TerminationType.OWNER_WITHDREW, TODAY.minusDays(1), WithdrawalChannel.MESSAGE,
                null, "Sold the unit.", ended), view.terminationOrNull());
        assertEquals(List.of(), view.viewerStopActions());
        assertEquals(List.of("ENDED"), view.advertising().reasonGroups());
    }

    /**
     * EX-1 at service level: an agreement in effect whose version ended yesterday in Vietnam, its expiry not stored,
     * reads EXPIRED with its end in the detail, the list row, the owner's item and the standings.
     */
    @Test
    void detail_pastEndNotStored_expiredWithEndedAtEndsAt() {
        AgreementPeriod endedYesterday = new AgreementPeriod(TODAY.minusMonths(6), TODAY.minusDays(1));
        AgreementVersion version = effective(endedYesterday);
        agreements.store(agreement(LEAD, AgreementStatus.ACTIVE, version));

        AgreementDetailView view = service.detail(AGREEMENT, SALE_VIEWER);

        assertEquals(AgreementStatus.EXPIRED, view.status());
        assertEquals(version.endsAt(), view.endedAtOrNull());
        assertEquals(List.of("ENDED"), view.advertising().reasonGroups());
        assertEquals(AgreementStatus.EXPIRED, listRow().status());
        assertEquals(AgreementStatus.EXPIRED, ownerItem().status());
        assertTrue(standing().ended());
    }

    /** EX-1, its other clause: an end date of today in Vietnam still reads ACTIVE everywhere. */
    @Test
    void detail_endingToday_activeEverywhere() {
        agreements.store(agreement(LEAD, AgreementStatus.ACTIVE, effective(new AgreementPeriod(TODAY.minusMonths(6),
                TODAY))));

        AgreementDetailView view = service.detail(AGREEMENT, SALE_VIEWER);

        assertEquals(AgreementStatus.ACTIVE, view.status());
        assertNull(view.endedAtOrNull());
        assertEquals(AgreementStatus.ACTIVE, listRow().status());
        assertEquals(AgreementStatus.ACTIVE, ownerItem().status());
        assertFalse(standing().ended());
    }

    /** RL-3 at service level: 21 records answer the newest 20 and moreSuspensions. */
    @Test
    void detail_suspensions_newestFirstTwentyAndMore() {
        agreements.store(agreement(LEAD, AgreementStatus.SUSPENDED, effective(RUNNING)));
        Instant first = WALL.minus(Duration.ofDays(30));
        for (int index = 0; index < 21; index++) {
            Instant at = first.plus(Duration.ofHours(index));
            AgreementSuspension record = AgreementSuspension.open(String.format("01JSUSPENSION%013d", index), AGREEMENT,
                    SuspensionReasonType.COMPLAINT, "Note " + index, "M", at);
            suspensions.store(index == 20 ? record : record.released("AD", at.plus(Duration.ofMinutes(30)),
                    "Lifted " + index));
        }

        AgreementDetailView view = service.detail(AGREEMENT, MANAGER_VIEWER);

        assertEquals(20, view.suspensions().size());
        assertTrue(view.moreSuspensions());
        Instant newest = first.plus(Duration.ofHours(20));
        assertEquals(new SuspensionView(SuspensionReasonType.COMPLAINT, "Note 20", newest, null, null),
                view.suspensions().getFirst());
        assertEquals(new SuspensionView(SuspensionReasonType.COMPLAINT, "Note 1", first.plus(Duration.ofHours(1)),
                first.plus(Duration.ofHours(1)).plus(Duration.ofMinutes(30)), "Lifted 1"), view.suspensions().getLast());

        agreements.store(agreement(LEAD, AgreementStatus.ACTIVE, effective(RUNNING)));
        InMemoryAgreementSuspensionRepository none = new InMemoryAgreementSuspensionRepository();
        AgreementDetailView empty = new AgreementQueryService(agreements, leads, trail,
                new InMemoryIntakeOriginPort(calls), new InMemorySourceTermsPort(calls),
                new RecordingOwnerAccountPort(calls).answer(OWNER, true), new InMemoryUnitLocationPort(), assembler,
                none).detail(AGREEMENT, MANAGER_VIEWER);
        assertEquals(List.of(), empty.suspensions());
        assertFalse(empty.moreSuspensions());
    }

    /** brokerage-agreement BR-17 (S2-D9): the detail still writes exactly one access row, whatever it now holds. */
    @Test
    void detail_stillOneAccessRow() {
        agreements.store(agreement(LEAD, AgreementStatus.SUSPENDED, effective(RUNNING)));
        suspensions.store(AgreementSuspension.open("01JSUSPENSION0000000000001", AGREEMENT,
                SuspensionReasonType.DISPUTE, null, "M", WALL.minus(Duration.ofDays(1))));

        service.detail(AGREEMENT, MANAGER_VIEWER);

        assertEquals(1, trail.accesses().size());
    }

    // ==================== the other reads (EX-1) ====================

    private AgreementListItemView listRow() {
        InMemoryBasisLeadScopePort scopes = new InMemoryBasisLeadScopePort();
        scopes.answer(SALE, new LeadScopeFacts(true, Set.of()));
        RecordingAgreementListRepository lists = new RecordingAgreementListRepository();
        lists.answer(new AgreementIdPage(List.of(AGREEMENT), 1));
        unitBases.unit(new UnitBasisFacts(UNIT, "P1", List.of(new UnitBasisFacts.Entry("I1", "USABLE", List.of()))));
        unitBases.owner(new PropertyOwnerFacts("P1", OWNER, List.of(new IntakeBasisFacts.Holding(OWNER, "OWNER",
                "UNVERIFIED"))));
        return new AgreementListService(scopes, lists, agreements, assembler)
                .list(new AgreementListQuery(Set.of(), false, 0, 20), SALE_VIEWER).items().getFirst();
    }

    private OwnerAgreementView ownerItem() {
        return service.ownerAgreements(OWNER).items().getFirst();
    }

    private AgreementStandingView standing() {
        return new AdvertisingRightService(agreements, assembler, new InMemoryBasisLeadAssigneePort().lead(LEAD, SALE))
                .standings(List.of(AGREEMENT)).getFirst();
    }

    // ==================== fixtures ====================

    private static BrokerageAgreement agreement(String leadId, AgreementStatus status, AgreementVersion version) {
        return BrokerageAgreement.load(AGREEMENT, UNIT, "P101", "P1", TransactionType.LONG_TERM_RENT, OWNER, leadId,
                status, SALE, CREATED, CREATED, 3L, List.of(version));
    }

    private static AgreementVersion draft() {
        return AgreementVersion.load("V1", 1, VersionStatus.DRAFT, RUNNING, LEAD, "I1", SALE, CREATED, CREATED);
    }

    private static AgreementVersion effective(AgreementPeriod period) {
        return effective(period, LEAD);
    }

    private static AgreementVersion effective(AgreementPeriod period, String leadId) {
        return version(VersionStatus.EFFECTIVE, period, leadId);
    }

    private static AgreementVersion version(VersionStatus status, AgreementPeriod period, String leadId) {
        return AgreementVersion.load("V1", 1, status, period, leadId, "I1", SALE, CREATED, CREATED,
                new PhotoConsent(true, false), VersionTerms.NONE, BrokerageFee.NONE, CREATED.plusSeconds(60),
                OwnerAnswer.confirmed(OWNER, AgreementStatement.VERSION, CREATED.plusSeconds(120)),
                ActivationDecision.activated("AD", CREATED.plusSeconds(180), ActivationStatement.VERSION,
                        new SignedDocument("HDMG-1", CREATED.atZone(AgreementVersion.VIETNAM).toLocalDate(), "A",
                                "B")));
    }
}
