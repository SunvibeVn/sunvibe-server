package vn.sunvibe.brokerage.core.application.service;

import org.junit.jupiter.api.Test;
import vn.sunvibe.brokerage.core.application.exception.BrokerageNotFoundException;
import vn.sunvibe.brokerage.core.application.query.AgreementDetailView;
import vn.sunvibe.brokerage.core.application.query.AgreementDetailView.VersionView;
import vn.sunvibe.brokerage.core.application.usecase.StaffViewer;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementAccessType;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementPeriod;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementStatus;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementVersion;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageAgreement;
import vn.sunvibe.brokerage.core.domain.model.agreement.TransactionType;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionStatus;
import vn.sunvibe.brokerage.core.testsupport.InMemoryAgreementRepository;
import vn.sunvibe.brokerage.core.testsupport.InMemoryBasisLeadPort;
import vn.sunvibe.brokerage.core.testsupport.RecordingAgreementTrail;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    private final AgreementQueryService service = new AgreementQueryService(agreements, leads, trail);

    @Test
    void detail_visibleBasisLead_viewAndOneAccessRow() {
        AgreementDetailView view = service.detail(AGREEMENT, VIEWER);

        assertEquals(new AgreementDetailView(AGREEMENT, AgreementStatus.DRAFTING, TransactionType.LONG_TERM_RENT, "U1",
                "P101", LEAD, CREATED, List.of(new VersionView(1, VersionStatus.DRAFT, PERIOD.startDate(),
                PERIOD.endDate(), CREATED))), view);
        assertEquals(1, trail.accesses().size());
        RecordingAgreementTrail.Access access = trail.accesses().get(0);
        assertEquals(new RecordingAgreementTrail.Access(SALE, AGREEMENT, AgreementAccessType.DETAIL_READ, access.at()),
                access);

        service.detail(AGREEMENT, VIEWER);

        assertEquals(2, trail.accesses().size());
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
        AgreementQueryService failing = new AgreementQueryService(agreements, leads, trail.failAccess());

        assertThrows(IllegalStateException.class, () -> failing.detail(AGREEMENT, VIEWER));
        assertEquals(List.of(), trail.accesses());
    }

    @Test
    void detail_reads_basisLeadWithoutLock() {
        service.detail(AGREEMENT, VIEWER);

        assertEquals(List.of("visible " + LEAD + " " + SALE), calls);
    }
}
