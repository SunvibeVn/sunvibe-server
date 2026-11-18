package vn.sunvibe.brokerage.core.application.service;

import org.junit.jupiter.api.Test;
import vn.sunvibe.brokerage.core.application.shared.external.property.IntakeBasisFacts;
import vn.sunvibe.brokerage.core.application.shared.external.property.PropertyOwnerFacts;
import vn.sunvibe.brokerage.core.application.shared.external.property.UnitBasisFacts;
import vn.sunvibe.brokerage.core.application.usecase.AdvertisingPair;
import vn.sunvibe.brokerage.core.application.usecase.AdvertisingRightView;
import vn.sunvibe.brokerage.core.application.usecase.AgreementStandingView;
import vn.sunvibe.brokerage.core.application.usecase.UnfinishedAgreementView;
import vn.sunvibe.brokerage.core.domain.model.agreement.ActivationDecision;
import vn.sunvibe.brokerage.core.domain.model.agreement.ActivationStatement;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementPeriod;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementStatement;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementStatus;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementVersion;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageAgreement;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageFee;
import vn.sunvibe.brokerage.core.domain.model.agreement.EarlyLeaseEndRefund;
import vn.sunvibe.brokerage.core.domain.model.agreement.EarlyLeaseEndType;
import vn.sunvibe.brokerage.core.domain.model.agreement.FeeFormula;
import vn.sunvibe.brokerage.core.domain.model.agreement.FeeFormulaType;
import vn.sunvibe.brokerage.core.domain.model.agreement.OwnerAnswer;
import vn.sunvibe.brokerage.core.domain.model.agreement.PhotoConsent;
import vn.sunvibe.brokerage.core.domain.model.agreement.SignedDocument;
import vn.sunvibe.brokerage.core.domain.model.agreement.TransactionType;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionStatus;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionTerms;
import vn.sunvibe.brokerage.core.testsupport.InMemoryAgreementRepository;
import vn.sunvibe.brokerage.core.testsupport.InMemoryBasisLeadAssigneePort;
import vn.sunvibe.brokerage.core.testsupport.InMemoryOwnerEligibilityPort;
import vn.sunvibe.brokerage.core.testsupport.InMemoryUnitBasisPort;
import vn.sunvibe.shared.time.AppClock;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The published, viewer-less answer for listing (brokerage-agreement BR-12, BR-13, US-4 AC-1, AC-2; listing section 7,
 * LM-16; E05-S6 spec S6-D9 to S6-D11; criteria SA-1, G2-1, LK-2, RT-2 in their unit parts; Review Focus 2, 3). The
 * service object is called without Spring, so with no transaction.
 */
class AdvertisingRightServiceTest {

    private static final String OWNER = "O";
    private static final String LEAD = "L1";
    private static final String A1 = "01JAGREEMENT00000000000001";
    private static final String A2 = "01JAGREEMENT00000000000002";
    private static final String A3 = "01JAGREEMENT00000000000003";
    private static final VersionTerms TERMS = new VersionTerms(8_000_000L, null, 12, 1, List.of());
    private static final BrokerageFee FEE = new BrokerageFee(FeeFormula.of(FeeFormulaType.FIXED_AMOUNT, null,
            2_000_000L, null), 7, FeeFormula.of(FeeFormulaType.NOT_CHARGED, null, null, null),
            new EarlyLeaseEndRefund(EarlyLeaseEndType.NO_REFUND, null, null), 90);

    private final InMemoryAgreementRepository agreements = new InMemoryAgreementRepository();
    private final InMemoryUnitBasisPort unitBases = new InMemoryUnitBasisPort();
    private final InMemoryOwnerEligibilityPort eligibility = new InMemoryOwnerEligibilityPort().eligible(OWNER);
    private final InMemoryBasisLeadAssigneePort assignees = new InMemoryBasisLeadAssigneePort().lead(LEAD, "S");
    private final AdvertisingRightService service = new AdvertisingRightService(agreements,
            new AdvertisingRightAssembler(unitBases, eligibility), assignees);

    @Test
    void answers_more250Distinct_refusedBeforeAnyRead() {
        List<AdvertisingPair> pairs = new ArrayList<>();
        for (int index = 0; index < 251; index++) {
            pairs.add(new AdvertisingPair("U" + index, String.format("01JAGREEMENTX%013d", index)));
        }

        assertThrows(IllegalArgumentException.class, () -> service.answers(pairs));

        assertEquals(List.of(), agreements.calls());
        assertEquals(List.of(), unitBases.calls());
    }

    /** Review Focus 2: 300 elements with repeats and nulls that are 250 distinct pairs are answered. */
    @Test
    void answers_repeatsAndNullsDropped_countedAfter() {
        storeInEffect(A1, "U1");
        List<AdvertisingPair> pairs = new ArrayList<>();
        pairs.add(new AdvertisingPair("U1", A1));
        for (int index = 1; index < 250; index++) {
            pairs.add(new AdvertisingPair("U" + index, String.format("01JAGREEMENTX%013d", index)));
        }
        for (int index = 0; index < 40; index++) {
            pairs.add(new AdvertisingPair("U1", A1));
        }
        for (int index = 0; index < 10; index++) {
            pairs.add(null);
        }
        assertEquals(300, pairs.size());

        List<AdvertisingRightView> answers = service.answers(pairs);

        assertEquals(1, answers.size());
        assertTrue(answers.getFirst().allowed());
        assertEquals(1, agreements.count("findAllById"));
    }

    /** SA-1 (unit part): an unknown agreement and a pair of another unit are left out (fail closed). */
    @Test
    void answers_unknownOrOtherUnit_leftOut() {
        storeInEffect(A1, "U1");
        storeDrafting(A2, "U2");

        List<AdvertisingRightView> answers = service.answers(List.of(new AdvertisingPair("U1", A1),
                new AdvertisingPair("U2", A2), new AdvertisingPair("U2", A1), new AdvertisingPair("U1", A3)));

        assertEquals(List.of(A1, A2), answers.stream().map(AdvertisingRightView::agreementId).toList());
        assertEquals(List.of("U1", "U2"), answers.stream().map(AdvertisingRightView::unitId).toList());
        assertTrue(answers.get(0).allowed());
        assertEquals(List.of("NOT_IN_EFFECT"), answers.get(1).reasonGroups());
        assertFalse(answers.get(0).ended());
    }

    /** RT-2 (unit part): an agreement not in effect is answered without any read of another module. */
    @Test
    void answers_notInEffect_noPortCall() {
        storeDrafting(A2, "U2");

        AdvertisingRightView answer = service.answers(List.of(new AdvertisingPair("U2", A2))).getFirst();

        assertEquals(List.of("NOT_IN_EFFECT"), answer.reasonGroups());
        assertFalse(answer.allowed());
        assertEquals(List.of(), unitBases.calls());
        assertEquals(List.of(), eligibility.calls());
        assertEquals(List.of(), assignees.calls());
    }

    /** Review Focus 3, ADR-012 "fails closed": a failing port reaches the caller, never an allowed answer. */
    @Test
    void answers_portFails_exceptionNeverAllowed() {
        storeInEffect(A1, "U1");
        unitBases.failWith(new IllegalStateException("read 1 failed"));

        assertThrows(IllegalStateException.class, () -> service.answers(List.of(new AdvertisingPair("U1", A1))));
    }

    /** G2-1 (unit part): the unfinished agreement of each unit and type; an unknown type answers empty. */
    @Test
    void unfinishedOf_unknownType_empty() {
        storeInEffect(A1, "U1");
        storeDrafting(A2, "U2");

        assertEquals(List.of(new UnfinishedAgreementView("U1", A1, "LONG_TERM_RENT"),
                        new UnfinishedAgreementView("U2", A2, "LONG_TERM_RENT")),
                service.unfinishedOf(List.of("U1", "U2", "U9"), "LONG_TERM_RENT"));
        assertEquals(List.of(), service.unfinishedOf(List.of("U1"), "SALE"));
        assertEquals(List.of(), service.unfinishedOf(List.of("U1"), null));
        assertEquals(List.of("unfinishedOf"), agreements.calls());
    }

    /** G2-1 (unit part): each agreement's unit, type, ended, and the basis lead's current assignee. */
    @Test
    void standings_endedAndCurrentAssignee() {
        storeInEffect(A1, "U1");
        storeDrafting(A2, "U2");
        agreements.store(BrokerageAgreement.load(A3, "U3", "P103", "P1", TransactionType.LONG_TERM_RENT, OWNER, "L9",
                AgreementStatus.EXPIRED, "S", now(), now(), 0L, List.of(AgreementVersion.load("V3", 1,
                        VersionStatus.EFFECTIVE, period(), "L9", "I1", "S", now(), now()))));

        List<AgreementStandingView> standings = service.standings(List.of(A1, A2, A3, "01JUNKNOWN0000000000000000"));

        assertEquals(List.of(new AgreementStandingView(A1, "U1", "LONG_TERM_RENT", false, "S"),
                new AgreementStandingView(A2, "U2", "LONG_TERM_RENT", false, "S"),
                new AgreementStandingView(A3, "U3", "LONG_TERM_RENT", true, null)), standings);
        assertEquals(List.of("assigneesOf [L1, L9]"), assignees.calls());

        assignees.lead(LEAD, "S2");
        assertEquals("S2", service.standings(List.of(A1)).getFirst().basisLeadAssigneeIdOrNull());
    }

    /** LK-2 (unit part), S6-D11: with no transaction the locking read throws its own exception before any read. */
    @Test
    void answerUnderShareLock_withoutTransaction_illegalStateNoRead() {
        storeInEffect(A1, "U1");

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> service.answerUnderShareLock(new AdvertisingPair("U1", A1)));

        assertEquals("the locking read of an agreement needs a read-write transaction", refused.getMessage());
        assertEquals(List.of(), agreements.calls());
        assertEquals(List.of(), unitBases.calls());
    }

    /** Stores an ACTIVE agreement on the unit with its version 1 in effect now, its facts holding in the ports. */
    private void storeInEffect(String agreementId, String unitId) {
        Instant confirmedAt = now().minus(Duration.ofDays(3));
        agreements.store(BrokerageAgreement.load(agreementId, unitId, "P101", "P1", TransactionType.LONG_TERM_RENT,
                OWNER, LEAD, AgreementStatus.ACTIVE, "S", confirmedAt, confirmedAt, 1L, List.of(AgreementVersion.load(
                        "V" + agreementId, 1, VersionStatus.EFFECTIVE, period(), LEAD, "I1", "S", confirmedAt,
                        confirmedAt, new PhotoConsent(true, false), TERMS, FEE, confirmedAt.minus(1, ChronoUnit.HOURS),
                        OwnerAnswer.confirmed(OWNER, AgreementStatement.VERSION, confirmedAt),
                        ActivationDecision.activated("M", confirmedAt.plusSeconds(60), ActivationStatement.VERSION,
                                new SignedDocument("HDMG-1", LocalDate.parse("2026-11-01"), "A", "B"))))));
        unitBases.unit(new UnitBasisFacts(unitId, "P1", List.of(new UnitBasisFacts.Entry("I1", "USABLE", List.of()))))
                .owner(new PropertyOwnerFacts("P1", OWNER, List.of(new IntakeBasisFacts.Holding(OWNER, "OWNER",
                        "UNVERIFIED"))));
    }

    private void storeDrafting(String agreementId, String unitId) {
        agreements.store(BrokerageAgreement.load(agreementId, unitId, "P102", "P1", TransactionType.LONG_TERM_RENT,
                OWNER, LEAD, AgreementStatus.DRAFTING, "S", now(), now(), 0L, List.of(AgreementVersion.load(
                        "V" + agreementId, 1, VersionStatus.DRAFT, period(), LEAD, "I1", "S", now(), now()))));
    }

    /** A period holding today in Vietnam. */
    private static AgreementPeriod period() {
        LocalDate today = AgreementVersion.todayInVietnam(now());
        return new AgreementPeriod(today.minusDays(30), today.plusDays(150));
    }

    private static Instant now() {
        return AppClock.currentUTC().truncatedTo(ChronoUnit.MICROS);
    }
}
