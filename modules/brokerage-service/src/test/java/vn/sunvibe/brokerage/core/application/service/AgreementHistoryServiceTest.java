package vn.sunvibe.brokerage.core.application.service;

import org.junit.jupiter.api.Test;
import vn.sunvibe.brokerage.core.application.exception.BrokerageNotFoundException;
import vn.sunvibe.brokerage.core.application.query.AgreementHistoryEntryView;
import vn.sunvibe.brokerage.core.application.query.AgreementHistoryView;
import vn.sunvibe.brokerage.core.application.usecase.AgreementHistoryQuery;
import vn.sunvibe.brokerage.core.application.usecase.StaffViewer;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementActorType;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementEventType;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementPeriod;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementStatus;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementVersion;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageAgreement;
import vn.sunvibe.brokerage.core.domain.model.agreement.TransactionType;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionStatus;
import vn.sunvibe.brokerage.core.domain.repository.AgreementHistoryRow;
import vn.sunvibe.brokerage.core.testsupport.InMemoryAgreementHistoryRepository;
import vn.sunvibe.brokerage.core.testsupport.InMemoryAgreementRepository;
import vn.sunvibe.brokerage.core.testsupport.InMemoryBasisLeadPort;
import vn.sunvibe.brokerage.core.testsupport.RecordingStaffDirectoryPort;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The history of an agreement (brokerage-agreement US-4 AC-4, BR-16, BR-17; E05-S7 spec S7-D7, S7-D8; criteria HS-1,
 * HS-3, HS-6 in their unit parts): the detail's readers and one not-found answer, each row's type, version, actor type
 * and staff name, the owner never asked for, and no reason.
 */
class AgreementHistoryServiceTest {

    private static final String AGREEMENT = "01JAGREEMENT00000000000001";
    private static final String LEAD = "L1";
    private static final String SALE = "S";
    private static final String OWNER = "O";
    private static final String STAFF_A = "A";
    private static final String STAFF_B = "B";
    private static final StaffViewer VIEWER = new StaffViewer(SALE, Set.of("ROLE_SALE"));
    private static final Instant T0 = Instant.parse("2026-11-20T03:15:00.123456Z");
    private static final AgreementPeriod PERIOD =
            new AgreementPeriod(LocalDate.parse("2027-01-01"), LocalDate.parse("2027-12-31"));

    private final List<String> calls = new ArrayList<>();
    private final InMemoryAgreementRepository agreements = new InMemoryAgreementRepository().store(
            BrokerageAgreement.load(AGREEMENT, "U1", "P101", "P1", TransactionType.LONG_TERM_RENT, OWNER, LEAD,
                    AgreementStatus.DRAFTING, SALE, T0, T0, 0L, List.of(AgreementVersion.load("V1", 1,
                            VersionStatus.DRAFT, PERIOD, LEAD, "I1", SALE, T0, T0))));
    private final InMemoryBasisLeadPort leads = new InMemoryBasisLeadPort(calls).lead(LEAD, SALE, SALE, "M");
    private final InMemoryAgreementHistoryRepository histories = new InMemoryAgreementHistoryRepository();
    private final RecordingStaffDirectoryPort staff = new RecordingStaffDirectoryPort();
    private final AgreementHistoryService service = new AgreementHistoryService(agreements, leads, histories, staff);

    /** S2-D10: a malformed id is not found before any read. */
    @Test
    void history_malformedId_notFoundWithoutRead() {
        for (String malformed : List.of(AGREEMENT.toLowerCase(), AGREEMENT.substring(1))) {
            assertThrows(BrokerageNotFoundException.class,
                    () -> service.history(new AgreementHistoryQuery(malformed, 0, 50), VIEWER));
        }

        assertEquals(List.of(), agreements.calls());
        assertEquals(List.of(), calls);
        assertEquals(0, histories.calls());
    }

    @Test
    void history_unknownId_notFound() {
        assertThrows(BrokerageNotFoundException.class,
                () -> service.history(new AgreementHistoryQuery("01JAGREEMENT00000000000009", 0, 50), VIEWER));

        assertEquals(List.of(), calls);
        assertEquals(0, histories.calls());
    }

    /** HS-3 (BR-16): a caller who may not see the basis lead gets the unknown id's answer, and no row is read. */
    @Test
    void history_leadNotVisible_notFoundNoRowRead() {
        histories.add(AGREEMENT, new AgreementHistoryRow("CREATED", 1, SALE, T0));

        assertThrows(BrokerageNotFoundException.class, () -> service.history(new AgreementHistoryQuery(AGREEMENT, 0, 50),
                new StaffViewer("OTHER", Set.of("ROLE_SALE"))));

        assertEquals(List.of("visible " + LEAD + " OTHER"), calls);
        assertEquals(0, histories.calls());
    }

    /**
     * HS-1 (BR-17): each row's type, version and time as stored; staff named once per page through one directory call
     * with only the staff ids; the owner and a job never named and never asked for.
     */
    @Test
    void history_page_entriesWithActorsAndOneDirectoryCall() {
        histories.add(AGREEMENT, new AgreementHistoryRow("DRAFT_EDITED", 1, STAFF_B, T0));
        histories.add(AGREEMENT, new AgreementHistoryRow("VERSION_SENT", 1, STAFF_A, T0.plusSeconds(1)));
        histories.add(AGREEMENT, new AgreementHistoryRow("VERSION_LAPSED", 1, null, T0.plusSeconds(2)));
        histories.add(AGREEMENT, new AgreementHistoryRow("OWNER_REJECTED", 1, OWNER, T0.plusSeconds(3)));
        histories.add(AGREEMENT, new AgreementHistoryRow("DRAFT_OPENED", 2, STAFF_A, T0.plusSeconds(4)));
        histories.add(AGREEMENT, new AgreementHistoryRow("CREATED", 1, SALE, T0.minusSeconds(1)));
        staff.name(STAFF_A, "Tran Thi A");
        staff.name(OWNER, "Owner Name");

        AgreementHistoryView view = service.history(new AgreementHistoryQuery(AGREEMENT, 0, 5), VIEWER);

        assertEquals(List.of(
                new AgreementHistoryEntryView("DRAFT_OPENED", 2, AgreementActorType.STAFF, "Tran Thi A", T0.plusSeconds(4)),
                new AgreementHistoryEntryView("OWNER_REJECTED", 1, AgreementActorType.OWNER, null, T0.plusSeconds(3)),
                new AgreementHistoryEntryView("VERSION_LAPSED", 1, AgreementActorType.SYSTEM, null, T0.plusSeconds(2)),
                new AgreementHistoryEntryView("VERSION_SENT", 1, AgreementActorType.STAFF, "Tran Thi A", T0.plusSeconds(1)),
                new AgreementHistoryEntryView("DRAFT_EDITED", 1, AgreementActorType.STAFF, null, T0)), view.entries());
        assertEquals(List.of(Set.of(STAFF_A, STAFF_B)), staff.calls());
        assertEquals(0, view.page());
        assertEquals(5, view.size());
        assertEquals(6, view.totalElements());
        assertEquals(List.of("visible " + LEAD + " " + SALE), calls);
    }

    /** A page without a staff row asks the directory nothing. */
    @Test
    void history_noStaffRow_noDirectoryCall() {
        histories.add(AGREEMENT, new AgreementHistoryRow("OWNER_CONFIRMED", 1, OWNER, T0));
        histories.add(AGREEMENT, new AgreementHistoryRow("VERSION_STARTED", 1, null, T0.plusSeconds(1)));

        AgreementHistoryView view = service.history(new AgreementHistoryQuery(AGREEMENT, 0, 50), VIEWER);

        assertEquals(List.of(AgreementActorType.SYSTEM, AgreementActorType.OWNER),
                view.entries().stream().map(AgreementHistoryEntryView::actorType).toList());
        assertEquals(List.of(), staff.calls());
    }

    /** HS-6 (S7-D8): a stored code the enum does not hold is OTHER; the ten codes of the enum are themselves. */
    @Test
    void history_unknownCode_other() {
        List<String> codes = new ArrayList<>(Arrays.stream(AgreementEventType.values()).map(Enum::name).toList());
        codes.add("SOMETHING_NEW");
        for (int index = 0; index < codes.size(); index++) {
            histories.add(AGREEMENT, new AgreementHistoryRow(codes.get(index), 1, null, T0.plusSeconds(index)));
        }

        List<String> types = service.history(new AgreementHistoryQuery(AGREEMENT, 0, 50), VIEWER).entries().stream()
                .map(AgreementHistoryEntryView::type).toList();

        List<String> expected = new ArrayList<>(codes.subList(0, 10));
        expected.add("OTHER");
        assertEquals(expected.reversed(), types);
        assertEquals(10, AgreementEventType.values().length);
    }
}
