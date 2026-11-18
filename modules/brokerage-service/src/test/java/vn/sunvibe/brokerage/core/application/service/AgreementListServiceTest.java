package vn.sunvibe.brokerage.core.application.service;

import org.junit.jupiter.api.Test;
import vn.sunvibe.brokerage.core.application.query.AgreementListItemView;
import vn.sunvibe.brokerage.core.application.query.AgreementPageView;
import vn.sunvibe.brokerage.core.application.shared.external.lead.LeadScopeFacts;
import vn.sunvibe.brokerage.core.application.shared.external.property.IntakeBasisFacts;
import vn.sunvibe.brokerage.core.application.shared.external.property.PropertyOwnerFacts;
import vn.sunvibe.brokerage.core.application.shared.external.property.UnitBasisFacts;
import vn.sunvibe.brokerage.core.application.usecase.AdvertisingRightView;
import vn.sunvibe.brokerage.core.application.usecase.AgreementListQuery;
import vn.sunvibe.brokerage.core.application.usecase.StaffViewer;
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
import vn.sunvibe.brokerage.core.domain.repository.AgreementIdPage;
import vn.sunvibe.brokerage.core.domain.repository.AgreementListCriteria;
import vn.sunvibe.brokerage.core.testsupport.InMemoryAgreementRepository;
import vn.sunvibe.brokerage.core.testsupport.InMemoryBasisLeadScopePort;
import vn.sunvibe.brokerage.core.testsupport.InMemoryOwnerEligibilityPort;
import vn.sunvibe.brokerage.core.testsupport.InMemoryUnitBasisPort;
import vn.sunvibe.brokerage.core.testsupport.RecordingAgreementListRepository;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The agreement list (brokerage-agreement US-4 AC-3, BR-6, BR-12, BR-16; E05-S7 spec S7-D1 to S7-D5; criteria LS-1,
 * LS-2, LS-4 in their unit parts; plan E05S7-P3): the caller's scope, the criteria in Vietnam dates, the id page's
 * order, each row's state at the read and its advertising answer from the assembler. Called without Spring, so with no
 * transaction; {@code listAt} takes a fixed clock.
 */
class AgreementListServiceTest {

    private static final String SALE = "S";
    private static final String OWNER = "O";
    private static final String LEAD = "L1";
    private static final StaffViewer VIEWER = new StaffViewer(SALE, Set.of("ROLE_SALE"));
    /** Noon of 1 December 2026 in Vietnam. */
    private static final Instant NOW = Instant.parse("2026-12-01T05:00:00Z");
    private static final LocalDate TODAY = LocalDate.parse("2026-12-01");
    private static final Instant CREATED = Instant.parse("2026-11-20T03:15:00.123456Z");
    private static final AgreementPeriod PERIOD =
            new AgreementPeriod(LocalDate.parse("2027-01-01"), LocalDate.parse("2027-12-31"));
    private static final PhotoConsent CONSENT = new PhotoConsent(true, false);
    private static final VersionTerms TERMS = new VersionTerms(8_000_000L, null, 12, 1, List.of());
    private static final BrokerageFee FEE = new BrokerageFee(FeeFormula.of(FeeFormulaType.FIXED_AMOUNT, null,
            2_000_000L, null), 7, FeeFormula.of(FeeFormulaType.NOT_CHARGED, null, null, null),
            new EarlyLeaseEndRefund(EarlyLeaseEndType.NO_REFUND, null, null), 90);
    private static final AgreementListQuery FIRST_PAGE = new AgreementListQuery(Set.of(), false, 0, 20);

    private final InMemoryBasisLeadScopePort scopes = new InMemoryBasisLeadScopePort();
    private final RecordingAgreementListRepository lists = new RecordingAgreementListRepository();
    private final InMemoryAgreementRepository agreements = new InMemoryAgreementRepository();
    private final InMemoryUnitBasisPort unitBases = new InMemoryUnitBasisPort();
    private final InMemoryOwnerEligibilityPort eligibility = new InMemoryOwnerEligibilityPort().eligible(OWNER);
    private final AdvertisingRightAssembler assembler = new AdvertisingRightAssembler(unitBases, eligibility);
    private final AgreementListService service = new AgreementListService(scopes, lists, agreements, assembler);

    // ==================== the criteria (S7-D2 to S7-D4) ====================

    /** S7-D4: "ends within the next 30 days" is today to today + 29 in the Vietnam calendar. */
    @Test
    void criteria_ending_todayToTodayPlus29InVietnam() {
        AgreementListCriteria criteria = AgreementListService.criteria(new AgreementListQuery(Set.of(), true, 0, 20),
                new LeadScopeFacts(true, Set.of()), NOW);

        assertEquals(LocalDate.parse("2026-12-01"), criteria.endingFromOrNull());
        assertEquals(LocalDate.parse("2026-12-30"), criteria.endingToOrNull());
    }

    /** Review Focus 1: just before and at Vietnam midnight the window follows the Vietnam date, never the server's. */
    @Test
    void criteria_vietnamMidnight_followsTheVietnamDate() {
        AgreementListQuery ending = new AgreementListQuery(Set.of(), true, 0, 20);
        LeadScopeFacts all = new LeadScopeFacts(true, Set.of());

        AgreementListCriteria before = AgreementListService.criteria(ending, all,
                Instant.parse("2026-11-30T16:59:59.999999Z"));
        AgreementListCriteria at = AgreementListService.criteria(ending, all, Instant.parse("2026-11-30T17:00:00Z"));

        assertEquals(LocalDate.parse("2026-11-30"), before.endingFromOrNull());
        assertEquals(LocalDate.parse("2026-12-29"), before.endingToOrNull());
        assertEquals(LocalDate.parse("2026-12-01"), at.endingFromOrNull());
        assertEquals(LocalDate.parse("2026-12-30"), at.endingToOrNull());
    }

    /** S7-D2: every lead is no lead filter; a Sale's leads are the set; the states as asked; no ending, no dates. */
    @Test
    void criteria_scope_allLeadsNullElseTheSet() {
        AgreementListQuery query = new AgreementListQuery(Set.of(AgreementStatus.ACTIVE), false, 0, 20);

        AgreementListCriteria all = AgreementListService.criteria(query, new LeadScopeFacts(true, Set.of()), NOW);
        AgreementListCriteria own = AgreementListService.criteria(query, new LeadScopeFacts(false, Set.of("L1", "L2")),
                NOW);

        assertNull(all.basisLeadIdsOrNull());
        assertEquals(Set.of("L1", "L2"), own.basisLeadIdsOrNull());
        assertEquals(Set.of(AgreementStatus.ACTIVE), own.statuses());
        assertNull(own.endingFromOrNull());
        assertNull(own.endingToOrNull());
    }

    // ==================== the page (S7-D1, S7-D3) ====================

    /** S7-D2, fail closed: no access, or a Sale with no lead, is the empty page with no other read. */
    @Test
    void list_emptyScope_emptyPageNoRead() {
        scopes.answer("OWN", new LeadScopeFacts(false, Set.of()));
        AgreementListQuery query = new AgreementListQuery(Set.of(), false, 3, 7);

        AgreementPageView none = service.listAt(query, new StaffViewer("NOBODY", Set.of("ROLE_CUSTOMER")), NOW);
        AgreementPageView own = service.listAt(query, new StaffViewer("OWN", Set.of("ROLE_SALE")), NOW);

        for (AgreementPageView page : List.of(none, own)) {
            assertEquals(List.of(), page.items());
            assertEquals(0, page.totalElements());
            assertEquals(3, page.page());
            assertEquals(7, page.size());
        }
        assertEquals(List.of("NOBODY", "OWN"), scopes.askedFor());
        assertEquals(0, lists.calls());
        assertEquals(List.of(), agreements.calls());
    }

    /** S7-D1: the rows in the id page's order, the latest version the highest number at the read, the total as counted. */
    @Test
    void list_rows_idPageOrderLatestVersionAndCount() {
        scopes.answer(SALE, new LeadScopeFacts(false, Set.of(LEAD)));
        String a = "01JAGREEMENT0000000000000A";
        String b = "01JAGREEMENT0000000000000B";
        Instant sentAt = NOW.minus(Duration.ofDays(2));
        Instant answered = NOW.minus(Duration.ofDays(1));
        agreements.store(agreement(a, "U-A", "P101", AgreementStatus.DRAFTING, List.of(draft(1))));
        agreements.store(agreement(b, "U-B", null, AgreementStatus.DRAFTING, List.of(
                AgreementVersion.load("V1", 1, VersionStatus.OWNER_REJECTED, PERIOD, LEAD, "I1", SALE, CREATED, answered,
                        CONSENT, TERMS, FEE, sentAt, OwnerAnswer.rejected(OWNER, "Too low", answered)),
                draft(2))));
        lists.answer(new AgreementIdPage(List.of(b, a), 7));

        AgreementPageView page = service.listAt(new AgreementListQuery(Set.of(AgreementStatus.DRAFTING), false, 1, 2),
                VIEWER, NOW);

        assertEquals(List.of(b, a), page.items().stream().map(AgreementListItemView::agreementId).toList());
        AgreementListItemView first = page.items().getFirst();
        assertNull(first.unitCodeOrNull());
        assertEquals(TransactionType.LONG_TERM_RENT, first.transactionType());
        assertEquals(AgreementStatus.DRAFTING, first.status());
        assertEquals(2, first.latestVersionNo());
        assertEquals(VersionStatus.DRAFT, first.latestVersionStatus());
        assertEquals(CREATED, first.createdAt());
        assertEquals("P101", page.items().get(1).unitCodeOrNull());
        assertEquals(1, page.items().get(1).latestVersionNo());
        assertEquals(7, page.totalElements());
        assertEquals(1, page.page());
        assertEquals(2, page.size());
        assertEquals(List.of(new AgreementListCriteria(Set.of(AgreementStatus.DRAFTING), Set.of(LEAD), null, null)),
                lists.criteria());
        assertEquals(List.of("findAllById"), agreements.calls());
    }

    /** LS-2 (S7-D3): a confirmed version whose 14 days of activation have passed reads LAPSED, the agreement DRAFTING. */
    @Test
    void list_lapseDueNotStored_rowAnswersTheStateAtTheRead() {
        scopes.answer(SALE, new LeadScopeFacts(true, Set.of()));
        String id = "01JAGREEMENT0000000000000C";
        Instant confirmedAt = NOW.minus(Duration.ofDays(14)).minusSeconds(1);
        agreements.store(agreement(id, "U-C", "P103", AgreementStatus.AWAITING_CONFIRMATION, List.of(
                AgreementVersion.load("V1", 1, VersionStatus.OWNER_CONFIRMED, PERIOD, LEAD, "I1", SALE, CREATED,
                        confirmedAt, CONSENT, TERMS, FEE, confirmedAt.minus(1, ChronoUnit.HOURS),
                        OwnerAnswer.confirmed(OWNER, AgreementStatement.VERSION, confirmedAt)))));
        lists.answer(new AgreementIdPage(List.of(id), 1));

        AgreementListItemView row = service.listAt(FIRST_PAGE, VIEWER, NOW).items().getFirst();

        assertEquals(AgreementStatus.DRAFTING, row.status());
        assertEquals(VersionStatus.LAPSED, row.latestVersionStatus());
        assertEquals(0, agreements.count("update"));
    }

    /**
     * LS-4 (S7-D5): each row keeps the five values of the assembler's answer for the same agreement and clock: drafting,
     * activated not started (with its start date), allowed (with its end), past its end.
     */
    @Test
    void list_rowAdvertising_fiveFieldsOfTheAssemblerAnswer() {
        scopes.answer(SALE, new LeadScopeFacts(true, Set.of()));
        String drafting = "01JAGREEMENT0000000000000D";
        String notStarted = "01JAGREEMENT0000000000000E";
        String allowed = "01JAGREEMENT0000000000000F";
        String ended = "01JAGREEMENT0000000000000G";
        agreements.store(agreement(drafting, "U-D", "P104", AgreementStatus.DRAFTING, List.of(draft(1))));
        agreements.store(agreement(notStarted, "U-E", "P105", AgreementStatus.ACTIVE, List.of(activated(VersionStatus
                .ACTIVATED, new AgreementPeriod(TODAY.plusDays(10), TODAY.plusDays(200))))));
        agreements.store(agreement(allowed, "U-F", "P106", AgreementStatus.ACTIVE, List.of(activated(VersionStatus
                .EFFECTIVE, new AgreementPeriod(TODAY.minusDays(30), TODAY.plusDays(150))))));
        agreements.store(agreement(ended, "U-G", "P107", AgreementStatus.ACTIVE, List.of(activated(VersionStatus
                .EFFECTIVE, new AgreementPeriod(TODAY.minusDays(200), TODAY.minusDays(1))))));
        for (String unit : List.of("U-E", "U-F", "U-G")) {
            unitBases.unit(new UnitBasisFacts(unit, "P1", List.of(new UnitBasisFacts.Entry("I1", "USABLE", List.of()))));
        }
        unitBases.owner(new PropertyOwnerFacts("P1", OWNER, List.of(new IntakeBasisFacts.Holding(OWNER, "OWNER",
                "UNVERIFIED"))));
        lists.answer(new AgreementIdPage(List.of(drafting, notStarted, allowed, ended), 4));

        List<AgreementListItemView> rows = service.listAt(FIRST_PAGE, VIEWER, NOW).items();

        assertEquals(List.of("NOT_IN_EFFECT"), rows.get(0).reasonGroups());
        assertNull(rows.get(0).endDateOrNull());
        assertNull(rows.get(0).startDateOrNull());
        assertEquals(List.of("NOT_STARTED"), rows.get(1).reasonGroups());
        assertEquals(TODAY.plusDays(10), rows.get(1).startDateOrNull());
        assertTrue(rows.get(2).advertisingAllowed());
        assertEquals(TODAY.plusDays(150), rows.get(2).endDateOrNull());
        assertEquals(TODAY.plusDays(151).atStartOfDay(AgreementVersion.VIETNAM).toInstant(), rows.get(2).endsAtOrNull());
        assertNull(rows.get(2).startDateOrNull());
        assertEquals(List.of("ENDED"), rows.get(3).reasonGroups());
        assertEquals(TODAY.minusDays(1), rows.get(3).endDateOrNull());
        for (AgreementListItemView row : rows) {
            AdvertisingRightView answer = new AdvertisingRightAssembler(unitBases, eligibility).answers(List.of(
                    agreements.findById(row.agreementId()).orElseThrow().effectiveAt(NOW)), NOW).getFirst();
            assertEquals(answer.allowed(), row.advertisingAllowed(), row.agreementId());
            assertEquals(answer.reasonGroups(), row.reasonGroups(), row.agreementId());
            assertEquals(answer.endDateOrNull(), row.endDateOrNull(), row.agreementId());
            assertEquals(answer.endsAtOrNull(), row.endsAtOrNull(), row.agreementId());
            assertEquals(answer.pendingStartDateOrNull(), row.startDateOrNull(), row.agreementId());
        }
        assertFalse(rows.get(1).advertisingAllowed());
    }

    /** An id the set load does not return (gone since the page) gives no row; the count stays as counted. */
    @Test
    void list_idNotLoaded_leftOut() {
        scopes.answer(SALE, new LeadScopeFacts(true, Set.of()));
        String stored = "01JAGREEMENT0000000000000H";
        agreements.store(agreement(stored, "U-H", "P108", AgreementStatus.DRAFTING, List.of(draft(1))));
        lists.answer(new AgreementIdPage(List.of("01JAGREEMENT0000000000000X", stored), 2));

        AgreementPageView page = service.listAt(FIRST_PAGE, VIEWER, NOW);

        assertEquals(List.of(stored), page.items().stream().map(AgreementListItemView::agreementId).toList());
        assertEquals(2, page.totalElements());
    }

    private static BrokerageAgreement agreement(String id, String unitId, String unitCodeOrNull, AgreementStatus status,
                                                List<AgreementVersion> versions) {
        return BrokerageAgreement.load(id, unitId, unitCodeOrNull, "P1", TransactionType.LONG_TERM_RENT, OWNER, LEAD,
                status, SALE, CREATED, CREATED, 1L, versions);
    }

    private static AgreementVersion draft(int versionNo) {
        return AgreementVersion.load("V" + versionNo, versionNo, VersionStatus.DRAFT, PERIOD, LEAD, "I1", SALE, CREATED,
                CREATED);
    }

    /** Version 1 confirmed three days before NOW and activated a minute later, in the given state and period. */
    private static AgreementVersion activated(VersionStatus status, AgreementPeriod period) {
        Instant confirmedAt = NOW.minus(Duration.ofDays(3));
        return AgreementVersion.load("V1", 1, status, period, LEAD, "I1", SALE, CREATED, confirmedAt, CONSENT, TERMS, FEE,
                confirmedAt.minus(1, ChronoUnit.HOURS), OwnerAnswer.confirmed(OWNER, AgreementStatement.VERSION,
                        confirmedAt),
                ActivationDecision.activated("M", confirmedAt.plusSeconds(60), ActivationStatement.VERSION,
                        new SignedDocument("HDMG-1", LocalDate.parse("2026-11-01"), "A", "B")));
    }
}
