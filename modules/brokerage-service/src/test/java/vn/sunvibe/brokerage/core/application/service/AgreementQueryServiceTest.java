package vn.sunvibe.brokerage.core.application.service;

import org.junit.jupiter.api.Test;
import vn.sunvibe.brokerage.core.application.exception.AgreementRefusedException;
import vn.sunvibe.brokerage.core.application.exception.BrokerageNotFoundException;
import vn.sunvibe.brokerage.core.application.query.AgreementDetailView;
import vn.sunvibe.brokerage.core.application.query.AgreementDetailView.VersionView;
import vn.sunvibe.brokerage.core.application.query.OwnerAgreementView;
import vn.sunvibe.brokerage.core.application.query.OwnerAgreementsView;
import vn.sunvibe.brokerage.core.application.query.SurveyTermsView;
import vn.sunvibe.brokerage.core.application.shared.external.property.IntakeBasisFacts;
import vn.sunvibe.brokerage.core.application.shared.external.property.IntakeOriginFacts;
import vn.sunvibe.brokerage.core.application.shared.external.property.PropertyOwnerFacts;
import vn.sunvibe.brokerage.core.application.shared.external.property.UnitBasisFacts;
import vn.sunvibe.brokerage.core.application.shared.external.property.UnitLocationFacts;
import vn.sunvibe.brokerage.core.application.shared.external.survey.SourceTermsFacts;
import vn.sunvibe.brokerage.core.application.usecase.AdvertisingRightView;
import vn.sunvibe.brokerage.core.application.usecase.StaffViewer;
import vn.sunvibe.brokerage.core.domain.model.agreement.ActivationDecision;
import vn.sunvibe.brokerage.core.domain.model.agreement.ActivationDecisionType;
import vn.sunvibe.brokerage.core.domain.model.agreement.ActivationStatement;
import vn.sunvibe.brokerage.core.domain.model.agreement.AdvertisingScope;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementAccessType;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementEventType;
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
import vn.sunvibe.brokerage.core.testsupport.InMemoryBasisLeadPort;
import vn.sunvibe.brokerage.core.testsupport.InMemoryIntakeOriginPort;
import vn.sunvibe.brokerage.core.testsupport.InMemoryOwnerEligibilityPort;
import vn.sunvibe.brokerage.core.testsupport.InMemorySourceTermsPort;
import vn.sunvibe.brokerage.core.testsupport.InMemoryUnitBasisPort;
import vn.sunvibe.brokerage.core.testsupport.InMemoryUnitLocationPort;
import vn.sunvibe.brokerage.core.testsupport.RecordingAgreementTrail;
import vn.sunvibe.brokerage.core.testsupport.RecordingOwnerAccountPort;
import vn.sunvibe.shared.time.AppClock;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The detail of an agreement and its access log (brokerage-agreement US-4 AC-3 part, BR-16, BR-17; E05-S2 spec 3.3,
 * S2-D9, S2-D10, AD-8).
 */
class AgreementQueryServiceTest {

    private static final String AGREEMENT = "01JAGREEMENT00000000000001";
    private static final String LEAD = "L1";
    private static final String SALE = "S";
    private static final String OWNER = "O";
    private static final StaffViewer VIEWER = new StaffViewer(SALE, Set.of("ROLE_SALE"));
    private static final Instant CREATED = Instant.parse("2026-11-20T03:15:00.123456Z");
    private static final AgreementPeriod PERIOD =
            new AgreementPeriod(LocalDate.parse("2027-01-01"), LocalDate.parse("2027-12-31"));
    private static final BrokerageAgreement STORED = BrokerageAgreement.load(AGREEMENT, "U1", "P101", "P1",
            TransactionType.LONG_TERM_RENT, "O", LEAD, AgreementStatus.DRAFTING, SALE, CREATED, CREATED, 0L,
            List.of(AgreementVersion.load("V1", 1, VersionStatus.DRAFT, PERIOD, LEAD, "I1", SALE, CREATED, CREATED)));

    private final List<String> calls = new ArrayList<>();
    private final InMemoryBasisLeadPort leads = new InMemoryBasisLeadPort(calls).lead(LEAD, SALE, SALE);
    private final InMemoryAgreementRepository agreements = new InMemoryAgreementRepository().store(STORED);
    private final RecordingAgreementTrail trail = new RecordingAgreementTrail();
    private final InMemoryIntakeOriginPort origins = new InMemoryIntakeOriginPort(calls);
    private final InMemorySourceTermsPort sourceTerms = new InMemorySourceTermsPort(calls);
    private final RecordingOwnerAccountPort accounts = new RecordingOwnerAccountPort(calls).answer(OWNER, true);
    private final InMemoryUnitLocationPort locations = new InMemoryUnitLocationPort();
    private final InMemoryUnitBasisPort unitBases = new InMemoryUnitBasisPort();
    private final InMemoryOwnerEligibilityPort eligibility = new InMemoryOwnerEligibilityPort();
    private final AdvertisingRightAssembler assembler = new AdvertisingRightAssembler(unitBases, eligibility);
    private final AgreementQueryService service = new AgreementQueryService(agreements, leads, trail, origins,
            sourceTerms, accounts, locations, assembler);

    @Test
    void detail_visibleBasisLead_viewAndOneAccessRow() {
        AgreementDetailView view = service.detail(AGREEMENT, VIEWER);

        assertEquals(new AgreementDetailView(AGREEMENT, AgreementStatus.DRAFTING, TransactionType.LONG_TERM_RENT, "U1",
                "P101", LEAD, CREATED, List.of(new VersionView(1, VersionStatus.DRAFT, PERIOD.startDate(),
                PERIOD.endDate(), CREATED, AdvertisingScope.SUNVIBE_CHANNELS, PhotoConsent.NONE, VersionTerms.NONE,
                BrokerageFee.NONE, null, null, null, null, null, null)),
                0L, true, new AdvertisingRightView("U1", AGREEMENT, false, false, List.of("NOT_IN_EFFECT"), null, null,
                false, false, null, null, null, null), false, null), view);
        assertEquals(1, trail.accesses().size());
        RecordingAgreementTrail.Access access = trail.accesses().get(0);
        assertEquals(new RecordingAgreementTrail.Access(SALE, AGREEMENT, AgreementAccessType.DETAIL_READ, access.at()),
                access);

        service.detail(AGREEMENT, VIEWER);

        assertEquals(2, trail.accesses().size());
    }

    /** brokerage-agreement BR-7 (E05-S4 spec S4-D10): each version's fee policy and sending time reach the view. */
    @Test
    void detail_versionFeePolicyAndSentAt_inTheView() {
        Instant sent = AppClock.currentUTC().truncatedTo(ChronoUnit.MICROS).minus(1, ChronoUnit.HOURS);
        BrokerageFee fee = new BrokerageFee(FeeFormula.of(FeeFormulaType.FIXED_AMOUNT, null, 2_000_000L, null), 7,
                FeeFormula.of(FeeFormulaType.NOT_CHARGED, null, null, null),
                new EarlyLeaseEndRefund(EarlyLeaseEndType.NO_REFUND, null, null), 90);
        PhotoConsent consent = new PhotoConsent(true, false);
        VersionTerms terms = new VersionTerms(8_000_000L, null, 12, 1, List.of());
        agreements.store(BrokerageAgreement.load(AGREEMENT, "U1", "P101", "P1", TransactionType.LONG_TERM_RENT, "O", LEAD,
                AgreementStatus.AWAITING_CONFIRMATION, SALE, CREATED, sent, 1L, List.of(AgreementVersion.load("V1", 1,
                        VersionStatus.AWAITING_OWNER, PERIOD, LEAD, "I1", SALE, CREATED, sent, consent, terms, fee,
                        sent))));

        VersionView version = service.detail(AGREEMENT, VIEWER).versions().getFirst();

        assertEquals(new VersionView(1, VersionStatus.AWAITING_OWNER, PERIOD.startDate(), PERIOD.endDate(), CREATED,
                AdvertisingScope.SUNVIBE_CHANNELS, consent, terms, fee, sent, sent.plus(Duration.ofDays(7)), null, null,
                null, null), version);
    }

    @Test
    void detail_basisLeadNotVisible_notFoundNoAccessRow() {
        assertThrows(BrokerageNotFoundException.class,
                () -> service.detail(AGREEMENT, new StaffViewer("S2", Set.of("ROLE_SALE"))));

        assertEquals(List.of(), trail.accesses());
    }

    @Test
    void detail_malformedId_notFoundWithoutQuery() {
        for (String id : List.of("01jfabc", AGREEMENT.substring(1), AGREEMENT.toLowerCase(Locale.ROOT))) {
            assertThrows(BrokerageNotFoundException.class, () -> service.detail(id, VIEWER), id);
        }

        assertEquals(0, agreements.count("findById"));
        assertEquals(List.of(), calls);
        assertEquals(List.of(), trail.accesses());
    }

    @Test
    void detail_unknownId_notFoundNoAccessRow() {
        assertThrows(BrokerageNotFoundException.class, () -> service.detail("01JUNKNOWN0000000000000000", VIEWER));

        assertEquals(List.of(), calls);
        assertEquals(List.of(), trail.accesses());
    }

    @Test
    void detail_accessWriteFails_noViewAnswered() {
        AgreementQueryService failing = new AgreementQueryService(agreements, leads, trail.failAccess(), origins,
                sourceTerms, accounts, locations, assembler);

        assertThrows(IllegalStateException.class, () -> failing.detail(AGREEMENT, VIEWER));
        assertEquals(List.of(), trail.accesses());
    }

    @Test
    void detail_reads_basisLeadWithoutLock() {
        service.detail(AGREEMENT, VIEWER);

        assertEquals(List.of("visible " + LEAD + " " + SALE), calls);
    }

    // ==================== E05-S3: the detail's content and the survey reference ====================

    /** E05-S3 spec S3-D4, S3-D14: the data version is the row version; the flag is a hint, the server decides. */
    @Test
    void detail_viewerFlagAndDataVersion() {
        InMemoryBasisLeadPort seen = new InMemoryBasisLeadPort(calls).lead(LEAD, SALE, SALE, "M");
        InMemoryAgreementRepository moved = new InMemoryAgreementRepository().store(BrokerageAgreement.load(AGREEMENT,
                "U1", "P101", "P1", TransactionType.LONG_TERM_RENT, "O", LEAD, AgreementStatus.DRAFTING, SALE, CREATED,
                CREATED, 3L, STORED.getVersions()));
        AgreementQueryService read = new AgreementQueryService(moved, seen, trail, origins, sourceTerms, accounts,
                locations, assembler);

        AgreementDetailView forSale = read.detail(AGREEMENT, VIEWER);
        AgreementDetailView forManager = read.detail(AGREEMENT, new StaffViewer("M", Set.of("ROLE_SALES_MANAGER")));

        assertEquals(3L, forSale.dataVersion());
        assertEquals(true, forSale.viewerIsAssignedSale());
        assertEquals(3L, forManager.dataVersion());
        assertEquals(false, forManager.viewerIsAssignedSale());
        assertEquals(2, trail.accesses().size());
    }

    @Test
    void detail_versionContent_asStored() {
        PhotoConsent consent = new PhotoConsent(true, true);
        VersionTerms terms = new VersionTerms(8_000_000L, null, 12, 1, List.of());
        agreements.store(BrokerageAgreement.load(AGREEMENT, "U1", "P101", "P1", TransactionType.LONG_TERM_RENT, "O",
                LEAD, AgreementStatus.DRAFTING, SALE, CREATED, CREATED, 0L, List.of(AgreementVersion.load("V1", 1,
                        VersionStatus.DRAFT, PERIOD, LEAD, "I1", SALE, CREATED, CREATED, consent, terms))));

        VersionView version = service.detail(AGREEMENT, VIEWER).versions().get(0);

        assertEquals(AdvertisingScope.SUNVIBE_CHANNELS, version.advertisingScope());
        assertEquals(consent, version.photoConsent());
        assertEquals(terms, version.terms());
    }

    @Test
    void surveyTerms_draft_proposalOfTheUnitsSpace() {
        SourceTermsFacts source = source("01REV");
        origins.origin("I1", new IntakeOriginFacts(LEAD, 1, "01REV", Map.of("U1", "SA", "U2", "SB")));
        sourceTerms.terms(LEAD, 1, source);

        SurveyTermsView view = service.surveyTerms(AGREEMENT, VIEWER);

        assertEquals(TermsProposalAssembler.proposal(source, "SA"), view);
        assertEquals(8_000_000L, view.terms().monthlyRentOrNull());
        assertEquals(List.of("findById"), agreements.calls());
        assertEquals(List.of("visible " + LEAD + " " + SALE, "origin I1", "terms " + LEAD + " 1"), calls);
    }

    @Test
    void surveyTerms_noOriginNoSpaceNoSourceOrOtherRevision_empty() {
        assertEquals(SurveyTermsView.EMPTY, service.surveyTerms(AGREEMENT, VIEWER));

        origins.origin("I1", new IntakeOriginFacts(LEAD, 1, "01REV", Map.of("U2", "SB")));
        assertEquals(SurveyTermsView.EMPTY, service.surveyTerms(AGREEMENT, VIEWER));

        origins.origin("I1", new IntakeOriginFacts(LEAD, 1, "01REV", Map.of("U1", "SA")));
        assertEquals(SurveyTermsView.EMPTY, service.surveyTerms(AGREEMENT, VIEWER));

        sourceTerms.terms(LEAD, 1, source("01OTHER"));
        assertEquals(SurveyTermsView.EMPTY, service.surveyTerms(AGREEMENT, VIEWER));

        // an origin of another lead than the one whose visibility was checked is never read
        origins.origin("I1", new IntakeOriginFacts("L2", 1, "01REV", Map.of("U1", "SA")));
        sourceTerms.terms("L2", 1, source("01REV"));
        calls.clear();
        assertEquals(SurveyTermsView.EMPTY, service.surveyTerms(AGREEMENT, VIEWER));
        assertEquals(List.of("visible " + LEAD + " " + SALE, "origin I1"), calls);
    }

    @Test
    void surveyTerms_hiddenLead_notFound() {
        origins.origin("I1", new IntakeOriginFacts(LEAD, 1, "01REV", Map.of("U1", "SA")));

        assertThrows(BrokerageNotFoundException.class,
                () -> service.surveyTerms(AGREEMENT, new StaffViewer("S2", Set.of("ROLE_SALE"))));
        assertThrows(BrokerageNotFoundException.class, () -> service.surveyTerms("01JUNKNOWN0000000000000000", VIEWER));
        assertThrows(BrokerageNotFoundException.class, () -> service.surveyTerms("a1", VIEWER));

        assertEquals(List.of("visible " + LEAD + " S2"), calls);
    }

    @Test
    void surveyTerms_noDraft_notDraft() {
        agreements.store(BrokerageAgreement.load(AGREEMENT, "U1", "P101", "P1", TransactionType.LONG_TERM_RENT, "O",
                LEAD, AgreementStatus.AWAITING_CONFIRMATION, SALE, CREATED, CREATED, 1L, List.of(AgreementVersion.load(
                        "V1", 1, VersionStatus.AWAITING_OWNER, PERIOD, LEAD, "I1", SALE, CREATED, CREATED))));

        AgreementRefusedException refused = assertThrows(AgreementRefusedException.class,
                () -> service.surveyTerms(AGREEMENT, VIEWER));

        assertEquals(AgreementRefusedException.Reason.AGREEMENT_NOT_DRAFT, refused.reason());
        assertEquals(List.of("visible " + LEAD + " " + SALE), calls);
    }

    /** E05-S3 spec S3-D11: the survey reference opens no agreement detail, so it writes nothing. */
    @Test
    void surveyTerms_writesNothing() {
        origins.origin("I1", new IntakeOriginFacts(LEAD, 1, "01REV", Map.of("U1", "SA")));
        sourceTerms.terms(LEAD, 1, source("01REV"));

        service.surveyTerms(AGREEMENT, VIEWER);

        assertEquals(List.of(), trail.accesses());
        assertEquals(List.of(), trail.events());
        assertEquals(0, agreements.count("update"));
        assertEquals(0, agreements.count("create"));
    }

    // ==================== E05-S5: the owner's read and the effective state (spec 3.1, 3.6, S5-D8, S5-D13) ====================

    private static final PhotoConsent OWNER_CONSENT = new PhotoConsent(true, false);
    private static final VersionTerms OWNER_TERMS = new VersionTerms(8_000_000L, null, 12, 1, List.of());
    private static final BrokerageFee OWNER_FEE = new BrokerageFee(FeeFormula.of(FeeFormulaType.FIXED_AMOUNT, null,
            2_000_000L, null), 7, FeeFormula.of(FeeFormulaType.NOT_CHARGED, null, null, null),
            new EarlyLeaseEndRefund(EarlyLeaseEndType.NO_REFUND, null, null), 90);

    /** OR-1: the owner's agreements with their sent versions, the statement of the answerable one, one access row each. */
    @Test
    void ownerAgreements_owner_sentVersionsStatementAndAccessRows() {
        Instant now = now();
        Instant firstSent = now.minus(1, ChronoUnit.HOURS);
        Instant secondSent = now.minus(3, ChronoUnit.HOURS);
        Instant answered = now.minus(2, ChronoUnit.HOURS);
        InMemoryAgreementRepository owned = new InMemoryAgreementRepository()
                .store(owned("01JAGREEMENTA0000000000001", "U1", "P101", AgreementStatus.AWAITING_CONFIRMATION,
                        List.of(sent(1, VersionStatus.AWAITING_OWNER, firstSent, null))))
                .store(owned("01JAGREEMENTA0000000000002", "U2", "P102", AgreementStatus.DRAFTING,
                        List.of(sent(1, VersionStatus.OWNER_REJECTED, secondSent,
                                        OwnerAnswer.rejected(OWNER, "Too low", answered)),
                                AgreementVersion.load("V2", 2, VersionStatus.DRAFT, PERIOD, LEAD, "I1", SALE, CREATED,
                                        CREATED, OWNER_CONSENT, OWNER_TERMS, OWNER_FEE, null))));
        locations.location(new UnitLocationFacts("U1", "P101", "P101 · Floor 1", "12 Tran Phu", "Hai Chau"))
                .location(new UnitLocationFacts("U2", "P102", "P102 · Floor 1", "12 Tran Phu", "Hai Chau"));

        OwnerAgreementsView view = ownerService(owned).ownerAgreements(OWNER);

        assertEquals(false, view.more());
        assertEquals(2, view.items().size());
        OwnerAgreementView first = view.items().get(0);
        assertEquals(new OwnerAgreementView("01JAGREEMENTA0000000000001", AgreementStatus.AWAITING_CONFIRMATION,
                TransactionType.LONG_TERM_RENT, "P101", "P101 · Floor 1", "12 Tran Phu", "Hai Chau",
                List.of(new VersionView(1, VersionStatus.AWAITING_OWNER, PERIOD.startDate(), PERIOD.endDate(), CREATED,
                        AdvertisingScope.SUNVIBE_CHANNELS, OWNER_CONSENT, OWNER_TERMS, OWNER_FEE, firstSent,
                        firstSent.plus(Duration.ofDays(7)), null, null, null, null)),
                new OwnerAgreementsView.StatementView(AgreementStatement.VERSION, AgreementStatement.text(1, "P101"))),
                first);
        assertTrue(first.statementOrNull().text().contains("Version 1"));
        assertTrue(first.statementOrNull().text().contains("P101"));
        OwnerAgreementView second = view.items().get(1);
        assertEquals("01JAGREEMENTA0000000000002", second.agreementId());
        assertEquals(AgreementStatus.DRAFTING, second.status());
        assertEquals(List.of(1), second.versions().stream().map(VersionView::versionNo).toList());
        assertEquals(answered, second.versions().getFirst().ownerAnsweredAtOrNull());
        assertEquals("Too low", second.versions().getFirst().ownerRejectionReasonOrNull());
        assertNull(second.statementOrNull());
        assertEquals(List.of("01JAGREEMENTA0000000000001", "01JAGREEMENTA0000000000002"),
                trail.accesses().stream().map(RecordingAgreementTrail.Access::agreementId).toList());
        assertTrue(trail.accesses().stream().allMatch(access -> access.userId().equals(OWNER)
                && access.action() == AgreementAccessType.OWNER_DETAIL_READ));
        assertEquals(List.of(List.of("U1", "U2")), locations.calls());
        assertEquals(List.of(), trail.events());
    }

    /** OR-2, US-2 AC-1: a caller who is not an eligible owner gets what an owner with nothing gets, and nothing is read. */
    @Test
    void ownerAgreements_ineligibleCaller_emptyAndNothingWritten() {
        InMemoryAgreementRepository owned = new InMemoryAgreementRepository()
                .store(owned("01JAGREEMENTA0000000000001", "U1", "P101", AgreementStatus.AWAITING_CONFIRMATION,
                        List.of(sent(1, VersionStatus.AWAITING_OWNER, now().minus(1, ChronoUnit.HOURS), null))));
        accounts.answer(OWNER, false);

        OwnerAgreementsView view = ownerService(owned).ownerAgreements(OWNER);

        assertEquals(new OwnerAgreementsView(List.of(), false), view);
        assertEquals(List.of("account " + OWNER), calls);
        assertEquals(List.of(), owned.calls());
        assertEquals(List.of(), locations.calls());
        assertEquals(List.of(), trail.accesses());
    }

    @Test
    void ownerAgreements_fiftyOne_fiftyAndMore() {
        InMemoryAgreementRepository owned = new InMemoryAgreementRepository();
        Instant now = now();
        for (int number = 1; number <= 51; number++) {
            owned.store(owned(String.format("01JAGREEMENTB%013d", number), "U" + number, "P" + number,
                    AgreementStatus.AWAITING_CONFIRMATION, List.of(sent(1, VersionStatus.AWAITING_OWNER,
                            now.minus(number, ChronoUnit.MINUTES), null))));
        }

        OwnerAgreementsView view = ownerService(owned).ownerAgreements(OWNER);

        assertEquals(true, view.more());
        assertEquals(50, view.items().size());
        assertEquals(String.format("01JAGREEMENTB%013d", 1), view.items().getFirst().agreementId());
        assertEquals(String.format("01JAGREEMENTB%013d", 50), view.items().getLast().agreementId());
        assertEquals(50, trail.accesses().size());
        assertEquals(1, locations.calls().size());
        assertEquals(50, locations.calls().getFirst().size());
    }

    /** LP-1 (read part), S5-D8: past its deadline a version reads LAPSED and its agreement DRAFTING; nothing is stored. */
    @Test
    void ownerAgreements_lapseDue_effectiveLapsedNoStatement() {
        Instant sentAt = now().minus(Duration.ofDays(7)).minusSeconds(1);
        InMemoryAgreementRepository owned = new InMemoryAgreementRepository()
                .store(owned("01JAGREEMENTA0000000000001", "U1", "P101", AgreementStatus.AWAITING_CONFIRMATION,
                        List.of(sent(1, VersionStatus.AWAITING_OWNER, sentAt, null))));

        OwnerAgreementView item = ownerService(owned).ownerAgreements(OWNER).items().getFirst();

        assertEquals(AgreementStatus.DRAFTING, item.status());
        assertEquals(VersionStatus.LAPSED, item.versions().getFirst().status());
        assertEquals(sentAt.plus(Duration.ofDays(7)), item.versions().getFirst().answerByOrNull());
        assertNull(item.statementOrNull());
        assertEquals(0, owned.count("update"));
        assertEquals(AgreementStatus.AWAITING_CONFIRMATION, owned.all().getFirst().getStatus());
        assertEquals(List.of(), trail.events());
    }

    /** Review Focus 1: a unit property-service no longer answers still shows its agreement, with no location lines. */
    @Test
    void ownerAgreements_unitNotLocated_nullLocationFields() {
        InMemoryAgreementRepository owned = new InMemoryAgreementRepository()
                .store(owned("01JAGREEMENTA0000000000001", "U1", "P101", AgreementStatus.AWAITING_CONFIRMATION,
                        List.of(sent(1, VersionStatus.AWAITING_OWNER, now().minus(1, ChronoUnit.HOURS), null))));

        OwnerAgreementView item = ownerService(owned).ownerAgreements(OWNER).items().getFirst();

        assertEquals("P101", item.unitCodeOrNull());
        assertNull(item.unitLocationOrNull());
        assertNull(item.streetOrNull());
        assertNull(item.wardNameOrNull());
        assertEquals(AgreementStatement.text(1, "P101"), item.statementOrNull().text());
    }

    /** LP-1 (read part): the staff detail answers the effective state with the stored row version as data version. */
    @Test
    void detail_lapseDueNotStored_effectiveStateStoredDataVersion() {
        Instant sentAt = now().minus(Duration.ofDays(7)).minusSeconds(1);
        agreements.store(BrokerageAgreement.load(AGREEMENT, "U1", "P101", "P1", TransactionType.LONG_TERM_RENT, OWNER,
                LEAD, AgreementStatus.AWAITING_CONFIRMATION, SALE, CREATED, sentAt, 3L,
                List.of(sent(1, VersionStatus.AWAITING_OWNER, sentAt, null))));

        AgreementDetailView view = service.detail(AGREEMENT, VIEWER);

        assertEquals(AgreementStatus.DRAFTING, view.status());
        assertEquals(3L, view.dataVersion());
        assertEquals(VersionStatus.LAPSED, view.versions().getFirst().status());
        assertEquals(sentAt.plus(Duration.ofDays(7)), view.versions().getFirst().answerByOrNull());
        assertEquals(0, agreements.count("update"));
    }

    @Test
    void detail_answeredVersion_answerFields() {
        Instant sentAt = now().minus(2, ChronoUnit.HOURS);
        Instant answered = now().minus(1, ChronoUnit.HOURS);
        agreements.store(BrokerageAgreement.load(AGREEMENT, "U1", "P101", "P1", TransactionType.LONG_TERM_RENT, OWNER,
                LEAD, AgreementStatus.DRAFTING, SALE, CREATED, answered, 2L,
                List.of(sent(1, VersionStatus.OWNER_REJECTED, sentAt, OwnerAnswer.rejected(OWNER, "Too low", answered)))));

        VersionView version = service.detail(AGREEMENT, VIEWER).versions().getFirst();

        assertEquals(VersionStatus.OWNER_REJECTED, version.status());
        assertEquals(sentAt.plus(Duration.ofDays(7)), version.answerByOrNull());
        assertEquals(answered, version.ownerAnsweredAtOrNull());
        assertEquals("Too low", version.ownerRejectionReasonOrNull());
    }

    // ==================== E05-S6: the answer, the activation hint and the effective state (S6-D8, S6-D21) ==========

    /** AV-1 (read part), S6-D8: an agreement in effect reads allowed with its end, terms in effect and photo consent. */
    @Test
    void detail_activeAgreement_advertisingAllowedTermsInEffect() {
        LocalDate today = AgreementVersion.todayInVietnam(now());
        AgreementPeriod period = new AgreementPeriod(today.minusDays(30), today.plusDays(150));
        agreements.store(BrokerageAgreement.load(AGREEMENT, "U1", "P101", "P1", TransactionType.LONG_TERM_RENT, OWNER,
                LEAD, AgreementStatus.ACTIVE, SALE, CREATED, CREATED, 4L, List.of(inEffect(period))));
        conditionsHold();

        AgreementDetailView view = service.detail(AGREEMENT, VIEWER);

        AdvertisingRightView advertising = view.advertising();
        assertTrue(advertising.allowed());
        assertEquals(List.of(), advertising.reasonGroups());
        assertEquals(period.endDate(), advertising.endDateOrNull());
        assertTrue(advertising.interiorPhotos());
        assertEquals(false, advertising.exteriorPhotos());
        assertEquals(new AdvertisingRightView.Terms(8_000_000L, null, 12, 1, List.of()),
                advertising.termsInEffectOrNull());
        assertNull(advertising.pendingStartDateOrNull());
        assertEquals(VersionStatus.EFFECTIVE, view.versions().getFirst().status());
        assertEquals(ActivationDecisionType.ACTIVATED, view.versions().getFirst().activationOrNull().type());
        assertEquals(List.of("forUnits [U1]", "ownersOf [P1]"), unitBases.calls());
        assertEquals(List.of("eligibleOwners [" + OWNER + "]"), eligibility.calls());
        assertEquals(1, trail.accesses().size());
    }

    /** S6-D8, S6-D2: a manager who is neither the assignee nor an author may decide; the statement of the version. */
    @Test
    void detail_independentManager_mayDecideWithStatement() {
        Instant confirmedAt = now().minus(1, ChronoUnit.HOURS);
        storeAwaitingActivation(confirmedAt);
        InMemoryBasisLeadPort seen = new InMemoryBasisLeadPort(calls).lead(LEAD, SALE, SALE, "M");
        AgreementQueryService read = new AgreementQueryService(agreements, seen, trail, origins, sourceTerms, accounts,
                locations, assembler);

        AgreementDetailView view = read.detail(AGREEMENT, new StaffViewer("M", Set.of("ROLE_SALES_MANAGER")));

        assertTrue(view.viewerMayDecideActivation());
        assertEquals(new AgreementDetailView.ActivationStatementView(ActivationStatement.VERSION,
                ActivationStatement.text(1)), view.activationStatementOrNull());
        assertEquals(confirmedAt.plus(Duration.ofDays(14)), view.versions().getFirst().activateByOrNull());
        assertNull(view.versions().getFirst().activationOrNull());
        assertEquals(List.of("NOT_IN_EFFECT"), view.advertising().reasonGroups());
        assertEquals(List.of(AGREEMENT + "@1"), trail.authorReads());
    }

    /** S6-D8, plan E05S6-P6: a Sale reads no authors; the assignee is never independent; an author may not decide. */
    @Test
    void detail_assigneeOrSale_mayNotDecideNoAuthorsRead() {
        storeAwaitingActivation(now().minus(1, ChronoUnit.HOURS));
        InMemoryBasisLeadPort seen = new InMemoryBasisLeadPort(calls).lead(LEAD, SALE, SALE, "E1");
        AgreementQueryService read = new AgreementQueryService(agreements, seen, trail, origins, sourceTerms, accounts,
                locations, assembler);

        AgreementDetailView forSale = read.detail(AGREEMENT, VIEWER);
        AgreementDetailView forAssignee = read.detail(AGREEMENT, new StaffViewer(SALE, Set.of("ROLE_SALE_LEADER")));

        assertEquals(false, forSale.viewerMayDecideActivation());
        assertEquals(false, forAssignee.viewerMayDecideActivation());
        assertEquals(List.of(), trail.authorReads());
        assertEquals(new AgreementDetailView.ActivationStatementView(ActivationStatement.VERSION,
                ActivationStatement.text(1)), forSale.activationStatementOrNull());

        trail.recordEvent(AGREEMENT, 1, AgreementEventType.DRAFT_EDITED, "E1", now());
        assertEquals(false, read.detail(AGREEMENT, new StaffViewer("E1", Set.of("ROLE_SALE_LEADER")))
                .viewerMayDecideActivation());
    }

    /** LP-1 (unit part): past its 14 days the version reads LAPSED, the agreement DRAFTING; nothing is stored. */
    @Test
    void detail_activationLapseDue_lapsedDraftingStoredDataVersion() {
        Instant confirmedAt = now().minus(Duration.ofDays(14)).minusSeconds(1);
        storeAwaitingActivation(confirmedAt);

        AgreementDetailView view = service.detail(AGREEMENT, VIEWER);

        assertEquals(AgreementStatus.DRAFTING, view.status());
        assertEquals(2L, view.dataVersion());
        assertEquals(VersionStatus.LAPSED, view.versions().getFirst().status());
        assertEquals(confirmedAt.plus(Duration.ofDays(14)), view.versions().getFirst().activateByOrNull());
        assertNull(view.activationStatementOrNull());
        assertEquals(false, view.viewerMayDecideActivation());
        assertEquals(0, agreements.count("update"));
    }

    /** OW-1 (unit part), S6-D21: the owner reads the effective state and activateBy, never the activation record. */
    @Test
    void ownerAgreements_activatedVersion_activationNullActivateBySet() {
        LocalDate today = AgreementVersion.todayInVietnam(now());
        AgreementVersion version = inEffect(new AgreementPeriod(today.minusDays(30), today.plusDays(150)));
        InMemoryAgreementRepository owned = new InMemoryAgreementRepository()
                .store(owned("01JAGREEMENTA0000000000001", "U1", "P101", AgreementStatus.ACTIVE, List.of(version)));

        OwnerAgreementView item = ownerService(owned).ownerAgreements(OWNER).items().getFirst();

        assertEquals(AgreementStatus.ACTIVE, item.status());
        VersionView read = item.versions().getFirst();
        assertEquals(VersionStatus.EFFECTIVE, read.status());
        assertNull(read.activationOrNull());
        assertEquals(version.getOwnerAnswerOrNull().answeredAt().plus(Duration.ofDays(14)), read.activateByOrNull());
        assertEquals(List.of(), unitBases.calls());
    }

    /** Stores {@link #AGREEMENT} awaiting confirmation, version 1 confirmed at {@code confirmedAt}, row version 2. */
    private void storeAwaitingActivation(Instant confirmedAt) {
        Instant sentAt = confirmedAt.minus(1, ChronoUnit.HOURS);
        agreements.store(BrokerageAgreement.load(AGREEMENT, "U1", "P101", "P1", TransactionType.LONG_TERM_RENT, OWNER,
                LEAD, AgreementStatus.AWAITING_CONFIRMATION, SALE, CREATED, confirmedAt, 2L, List.of(
                        AgreementVersion.load("V1", 1, VersionStatus.OWNER_CONFIRMED, PERIOD, LEAD, "I1", SALE, CREATED,
                                confirmedAt, OWNER_CONSENT, OWNER_TERMS, OWNER_FEE, sentAt,
                                OwnerAnswer.confirmed(OWNER, AgreementStatement.VERSION, confirmedAt)))));
    }

    /** Version 1 EFFECTIVE over the period, confirmed three days ago and activated a minute later. */
    private static AgreementVersion inEffect(AgreementPeriod period) {
        Instant confirmedAt = now().minus(Duration.ofDays(3));
        Instant sentAt = confirmedAt.minus(1, ChronoUnit.HOURS);
        return AgreementVersion.load("V1", 1, VersionStatus.EFFECTIVE, period, LEAD, "I1", SALE, CREATED, confirmedAt,
                OWNER_CONSENT, OWNER_TERMS, OWNER_FEE, sentAt,
                OwnerAnswer.confirmed(OWNER, AgreementStatement.VERSION, confirmedAt),
                ActivationDecision.activated("M", confirmedAt.plusSeconds(60), ActivationStatement.VERSION,
                        new SignedDocument("HDMG-1", LocalDate.parse("2026-11-01"), "A", "B")));
    }

    /** Read 1 maps U1 on intake I1 (usable, no reason), read 1b gives P1's owner O, and O is an eligible owner. */
    private void conditionsHold() {
        unitBases.unit(new UnitBasisFacts("U1", "P1", List.of(new UnitBasisFacts.Entry("I1", "USABLE", List.of()))))
                .owner(new PropertyOwnerFacts("P1", OWNER, List.of(new IntakeBasisFacts.Holding(OWNER, "OWNER",
                        "UNVERIFIED"))));
        eligibility.eligible(OWNER);
    }

    private AgreementQueryService ownerService(InMemoryAgreementRepository owned) {
        return new AgreementQueryService(owned, leads, trail, origins, sourceTerms, accounts, locations, assembler);
    }

    private static BrokerageAgreement owned(String id, String unitId, String unitCode, AgreementStatus status,
                                            List<AgreementVersion> versions) {
        return BrokerageAgreement.load(id, unitId, unitCode, "P1", TransactionType.LONG_TERM_RENT, OWNER, LEAD, status,
                SALE, CREATED, CREATED, 1L, versions);
    }

    private static AgreementVersion sent(int versionNo, VersionStatus status, Instant sentAt, OwnerAnswer answerOrNull) {
        return AgreementVersion.load("V" + versionNo, versionNo, status, PERIOD, LEAD, "I1", SALE, CREATED,
                answerOrNull == null ? sentAt : answerOrNull.answeredAt(), OWNER_CONSENT, OWNER_TERMS, OWNER_FEE, sentAt,
                answerOrNull);
    }

    /** The service's clock, as it reads it (E05-S5 spec S5-D19): times of these tests are relative to it. */
    private static Instant now() {
        return AppClock.currentUTC().truncatedTo(ChronoUnit.MICROS);
    }

    private static SourceTermsFacts source(String revisionId) {
        return new SourceTermsFacts(revisionId, LocalDate.of(2026, 11, 9),
                List.of(new SourceTermsFacts.Space("SA", "P101"), new SourceTermsFacts.Space("SB", "P102")),
                List.of(new SourceTermsFacts.Term("monthlyRent", "P101", 8_000_000L, "VND", "MONTH", null,
                                "OWNER_STATED", LocalDate.of(2026, 11, 10)),
                        new SourceTermsFacts.Term("monthlyRent", "P102", 9_000_000L, "VND", "MONTH", null,
                                "OBSERVED_ON_SITE", null)));
    }
}
