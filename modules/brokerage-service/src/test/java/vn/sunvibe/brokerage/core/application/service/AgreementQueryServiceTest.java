package vn.sunvibe.brokerage.core.application.service;

import org.junit.jupiter.api.Test;
import vn.sunvibe.brokerage.core.application.exception.AgreementRefusedException;
import vn.sunvibe.brokerage.core.application.exception.BrokerageNotFoundException;
import vn.sunvibe.brokerage.core.application.query.AgreementDetailView;
import vn.sunvibe.brokerage.core.application.query.AgreementDetailView.VersionView;
import vn.sunvibe.brokerage.core.application.query.SurveyTermsView;
import vn.sunvibe.brokerage.core.application.shared.external.property.IntakeOriginFacts;
import vn.sunvibe.brokerage.core.application.shared.external.survey.SourceTermsFacts;
import vn.sunvibe.brokerage.core.application.usecase.StaffViewer;
import vn.sunvibe.brokerage.core.domain.model.agreement.AdvertisingScope;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementAccessType;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementPeriod;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementStatus;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementVersion;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageAgreement;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageFee;
import vn.sunvibe.brokerage.core.domain.model.agreement.EarlyLeaseEndRefund;
import vn.sunvibe.brokerage.core.domain.model.agreement.EarlyLeaseEndType;
import vn.sunvibe.brokerage.core.domain.model.agreement.FeeFormula;
import vn.sunvibe.brokerage.core.domain.model.agreement.FeeFormulaType;
import vn.sunvibe.brokerage.core.domain.model.agreement.PhotoConsent;
import vn.sunvibe.brokerage.core.domain.model.agreement.TransactionType;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionStatus;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionTerms;
import vn.sunvibe.brokerage.core.testsupport.InMemoryAgreementRepository;
import vn.sunvibe.brokerage.core.testsupport.InMemoryBasisLeadPort;
import vn.sunvibe.brokerage.core.testsupport.InMemoryIntakeOriginPort;
import vn.sunvibe.brokerage.core.testsupport.InMemorySourceTermsPort;
import vn.sunvibe.brokerage.core.testsupport.RecordingAgreementTrail;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The detail of an agreement and its access log (brokerage-agreement US-4 AC-3 part, BR-16, BR-17; E05-S2 spec 3.3,
 * S2-D9, S2-D10, AD-8).
 */
class AgreementQueryServiceTest {

    private static final String AGREEMENT = "01JAGREEMENT00000000000001";
    private static final String LEAD = "L1";
    private static final String SALE = "S";
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
    private final AgreementQueryService service = new AgreementQueryService(agreements, leads, trail, origins,
            sourceTerms);

    @Test
    void detail_visibleBasisLead_viewAndOneAccessRow() {
        AgreementDetailView view = service.detail(AGREEMENT, VIEWER);

        assertEquals(new AgreementDetailView(AGREEMENT, AgreementStatus.DRAFTING, TransactionType.LONG_TERM_RENT, "U1",
                "P101", LEAD, CREATED, List.of(new VersionView(1, VersionStatus.DRAFT, PERIOD.startDate(),
                PERIOD.endDate(), CREATED, AdvertisingScope.SUNVIBE_CHANNELS, PhotoConsent.NONE, VersionTerms.NONE,
                BrokerageFee.NONE, null)),
                0L, true), view);
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
        Instant sent = Instant.parse("2026-11-22T05:30:00.987654Z");
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
                AdvertisingScope.SUNVIBE_CHANNELS, consent, terms, fee, sent), version);
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
                sourceTerms);

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
        AgreementQueryService read = new AgreementQueryService(moved, seen, trail, origins, sourceTerms);

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

    private static SourceTermsFacts source(String revisionId) {
        return new SourceTermsFacts(revisionId, LocalDate.of(2026, 11, 9),
                List.of(new SourceTermsFacts.Space("SA", "P101"), new SourceTermsFacts.Space("SB", "P102")),
                List.of(new SourceTermsFacts.Term("monthlyRent", "P101", 8_000_000L, "VND", "MONTH", null,
                                "OWNER_STATED", LocalDate.of(2026, 11, 10)),
                        new SourceTermsFacts.Term("monthlyRent", "P102", 9_000_000L, "VND", "MONTH", null,
                                "OBSERVED_ON_SITE", null)));
    }
}
