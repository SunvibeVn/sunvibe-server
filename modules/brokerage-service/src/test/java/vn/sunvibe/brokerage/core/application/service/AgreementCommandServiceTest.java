package vn.sunvibe.brokerage.core.application.service;

import org.junit.jupiter.api.Test;
import vn.sunvibe.brokerage.core.application.command.AgreementCreateCommand;
import vn.sunvibe.brokerage.core.application.exception.AgreementRefusedException;
import vn.sunvibe.brokerage.core.application.exception.AgreementRefusedException.Entry;
import vn.sunvibe.brokerage.core.application.exception.AgreementRefusedException.Reason;
import vn.sunvibe.brokerage.core.application.exception.BrokerageNotFoundException;
import vn.sunvibe.brokerage.core.application.shared.external.property.IntakeBasisFacts;
import vn.sunvibe.brokerage.core.application.shared.external.property.IntakeBasisFacts.Holding;
import vn.sunvibe.brokerage.core.application.shared.external.property.IntakeBasisFacts.Unit;
import vn.sunvibe.brokerage.core.application.usecase.AgreementCreateResult;
import vn.sunvibe.brokerage.core.application.usecase.StaffViewer;
import vn.sunvibe.brokerage.core.domain.exception.AgreementPeriodException;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementPeriod;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementStatus;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementVersion;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageAgreement;
import vn.sunvibe.brokerage.core.domain.model.agreement.TransactionType;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionStatus;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementEventType;
import vn.sunvibe.brokerage.core.testsupport.InMemoryAgreementRepository;
import vn.sunvibe.brokerage.core.testsupport.InMemoryBasisLeadPort;
import vn.sunvibe.brokerage.core.testsupport.InMemoryIntakeBasisPort;
import vn.sunvibe.brokerage.core.testsupport.RecordingAgreementTrail;
import vn.sunvibe.brokerage.core.testsupport.RecordingOwnerAccountPort;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Creating a draft agreement (brokerage-agreement US-1 AC-1 to AC-4; BR-1 to BR-6, BR-16, BR-18; E05-S2 spec 3.1,
 * S2-D2, S2-D5, S2-D6, S2-D7): the steps run in the spec's order and the first failing one answers.
 */
class AgreementCommandServiceTest {

    private static final String LEAD = "L1";
    private static final String SALE = "S";
    private static final String OWNER = "O";
    private static final StaffViewer VIEWER = new StaffViewer(SALE, Set.of("ROLE_SALE"));
    private static final LocalDate START = LocalDate.parse("2027-01-01");
    private static final LocalDate END = LocalDate.parse("2027-12-31");
    private static final List<Holding> HOLDINGS = List.of(new Holding(OWNER, "OWNER", "UNVERIFIED"));
    private static final IntakeBasisFacts BASIS = basis("USABLE",
            new Unit("U1", "P101", List.of()), new Unit("U2", "P102", List.of("UNIT_STOP")));

    private final List<String> calls = new ArrayList<>();
    private final InMemoryBasisLeadPort leads = new InMemoryBasisLeadPort(calls).lead(LEAD, SALE, SALE, "M", "S2");
    private final InMemoryIntakeBasisPort bases = new InMemoryIntakeBasisPort(calls).basis(LEAD, BASIS);
    private final RecordingOwnerAccountPort accounts = new RecordingOwnerAccountPort(calls).answer(OWNER, true);
    private final InMemoryAgreementRepository agreements = new InMemoryAgreementRepository();
    private final RecordingAgreementTrail trail = new RecordingAgreementTrail();
    private final Iterator<String> ids = List.of("A1", "V1", "A2", "V2", "A3", "V3").iterator();

    private AgreementCommandService service() {
        return new AgreementCommandService(leads, bases, accounts, agreements, trail, ids::next);
    }

    @Test
    void create_assignedSaleEligibleUnit_writesAgreementVersionAndOneEvent() {
        AgreementCreateResult result = service().create(command("U1"), VIEWER);

        assertEquals(new AgreementCreateResult("A1"), result);
        BrokerageAgreement stored = agreements.all().get(0);
        assertEquals("A1", stored.getId());
        assertEquals(AgreementStatus.DRAFTING, stored.getStatus());
        assertEquals("U1", stored.getUnitId());
        assertEquals("P101", stored.getUnitCodeOrNull());
        assertEquals("P1", stored.getPropertyId());
        assertEquals(TransactionType.LONG_TERM_RENT, stored.getTransactionType());
        assertEquals(OWNER, stored.getOwnerUserId());
        assertEquals(LEAD, stored.getBasisLeadId());
        assertEquals(SALE, stored.getCreatedByUserId());
        assertNull(stored.getRowVersionOrNull());
        AgreementVersion version = stored.getVersions().get(0);
        assertEquals(1, stored.getVersions().size());
        assertEquals("V1", version.getId());
        assertEquals(1, version.getVersionNo());
        assertEquals(VersionStatus.DRAFT, version.getStatus());
        assertEquals(new AgreementPeriod(START, END), version.getPeriod());
        assertEquals(LEAD, version.getBasisLeadId());
        assertEquals("I1", version.getBasisIntakeId());
        assertEquals(SALE, version.getCreatedByUserId());
        assertEquals(1, trail.events().size());
        RecordingAgreementTrail.Event event = trail.events().get(0);
        assertEquals(new RecordingAgreementTrail.Event("A1", 1, AgreementEventType.CREATED, SALE, event.at()), event);
        Instant at = event.at();
        assertEquals(at, stored.getCreatedAt());
        assertEquals(at, stored.getUpdatedAt());
        assertEquals(at, version.getCreatedAt());
        assertEquals(at, version.getUpdatedAt());
        assertEquals(0, at.getNano() % 1_000);
        assertEquals(List.of(), trail.accesses());
    }

    @Test
    void create_steps_lockedLeadThenBasisThenOwnerThenLookup() {
        service().create(command("U1"), VIEWER);

        assertEquals(List.of("forUpdate " + LEAD + " " + SALE, "basis " + LEAD, "account " + OWNER), calls);
        assertEquals(List.of("unfinishedOf", "create"), agreements.calls());
    }

    @Test
    void create_periodOrTypeRefused_beforeAnyRead() {
        AgreementPeriodException period = assertThrows(AgreementPeriodException.class, () -> service().create(
                new AgreementCreateCommand(LEAD, "U1", "LONG_TERM_RENT", START, LocalDate.parse("2028-01-02")), VIEWER));
        assertEquals("endDate", period.field());

        AgreementRefusedException type = assertThrows(AgreementRefusedException.class, () -> service().create(
                new AgreementCreateCommand(LEAD, "U1", "SALE", START, END), VIEWER));
        assertEquals(Reason.VALIDATION_FAILED, type.reason());
        assertEquals(List.of(new Entry("transactionType", List.of("LONG_TERM_RENT"))), type.entries());

        AgreementRefusedException both = assertThrows(AgreementRefusedException.class, () -> service().create(
                new AgreementCreateCommand(LEAD, "U1", "SALE", START, LocalDate.parse("2028-01-02")), VIEWER));
        assertEquals(List.of(new Entry("transactionType", List.of("LONG_TERM_RENT"))), both.entries());

        assertEquals(List.of(), calls);
        assertEquals(List.of(), agreements.calls());
        assertEquals(List.of(), trail.events());
    }

    @Test
    void create_leadNotVisible_notFound() {
        InMemoryBasisLeadPort hidden = new InMemoryBasisLeadPort(calls).lead(LEAD, SALE, "M");
        AgreementCommandService service = new AgreementCommandService(hidden, bases, accounts, agreements, trail,
                ids::next);

        assertThrows(BrokerageNotFoundException.class, () -> service.create(command("U1"), VIEWER));

        assertEquals(List.of("forUpdate " + LEAD + " " + SALE), calls);
        assertEquals(List.of(), agreements.all());
    }

    @Test
    void create_notAssignee_refused() {
        for (StaffViewer viewer : List.of(new StaffViewer("M", Set.of("ROLE_SALES_MANAGER")),
                new StaffViewer("S2", Set.of("ROLE_ADMIN", "ROLE_SUPER_ADMIN")),
                new StaffViewer(SALE, Set.of("ROLE_SALES_MANAGER")))) {
            AgreementRefusedException refused = assertThrows(AgreementRefusedException.class,
                    () -> service().create(command("U1"), viewer), viewer.toString());

            assertEquals(Reason.AGREEMENT_NOT_ASSIGNEE, refused.reason());
            assertEquals(List.of(), refused.entries());
        }
        assertEquals(0, calls.stream().filter(call -> call.startsWith("basis")).count());
        assertEquals(List.of(), agreements.all());
        assertEquals(List.of(), trail.events());
    }

    @Test
    void create_noBasis_notEligibleBasisOnly() {
        InMemoryIntakeBasisPort none = new InMemoryIntakeBasisPort(calls);
        AgreementCommandService service = new AgreementCommandService(leads, none, accounts, agreements, trail,
                ids::next);

        AgreementRefusedException refused = assertThrows(AgreementRefusedException.class,
                () -> service.create(command("U1"), VIEWER));

        assertEquals(Reason.AGREEMENT_NOT_ELIGIBLE, refused.reason());
        assertEquals(List.of(new Entry("reasonGroups.BASIS", null)), refused.entries());
        assertEquals(0, calls.stream().filter(call -> call.startsWith("account")).count());
        assertEquals(List.of(), agreements.calls());
    }

    @Test
    void create_policyGroups_notEligibleInOrder() {
        assertEquals(List.of("reasonGroups.UNIT"), refusedFields(service(), "U2"));

        accounts.answer(OWNER, false);
        assertEquals(List.of("reasonGroups.OWNER"), refusedFields(service(), "U1"));

        bases.basis(LEAD, basis("SUSPENDED", BASIS.units().toArray(Unit[]::new)));
        assertEquals(List.of("reasonGroups.BASIS", "reasonGroups.UNIT", "reasonGroups.OWNER"),
                refusedFields(service(), "U2"));

        bases.basis(LEAD, BASIS);
        accounts.answer(OWNER, true);
        assertEquals(List.of("reasonGroups.BASIS"), refusedFields(service(), "U9"));

        assertEquals(List.of(), agreements.all());
        assertEquals(0, agreements.count("create"));
        assertEquals(List.of(), trail.events());
    }

    @Test
    void create_availabilityDiffersOnly_created() {
        bases.basis(LEAD, basis("USABLE", new Unit("U1", "P101", List.of("AVAILABILITY_DIFFERS"))));

        assertEquals(new AgreementCreateResult("A1"), service().create(command("U1"), VIEWER));
        assertEquals(1, agreements.all().size());
    }

    @Test
    void create_unfinishedAgreementOnTheUnit_exists() {
        agreements.store(stored("A9", "L9", AgreementStatus.DRAFTING, VersionStatus.DRAFT));

        AgreementRefusedException refused = assertThrows(AgreementRefusedException.class,
                () -> service().create(command("U1"), VIEWER));

        assertEquals(Reason.AGREEMENT_EXISTS, refused.reason());
        assertEquals(List.of(), refused.entries());
        assertEquals(0, agreements.count("create"));
        assertEquals(List.of(), trail.events());

        InMemoryAgreementRepository cancelled = new InMemoryAgreementRepository()
                .store(stored("A8", "L9", AgreementStatus.CANCELLED, VersionStatus.CANCELLED));
        AgreementCommandService service = new AgreementCommandService(leads, bases, accounts, cancelled, trail,
                ids::next);
        assertEquals(new AgreementCreateResult("A1"), service.create(command("U1"), VIEWER));
        assertEquals(2, cancelled.all().size());
    }

    @Test
    void create_unitWithoutCode_keepsNullCode() {
        bases.basis(LEAD, basis("USABLE", new Unit("U3", null, List.of())));

        service().create(command("U3"), VIEWER);

        assertNull(agreements.all().get(0).getUnitCodeOrNull());
    }

    private List<String> refusedFields(AgreementCommandService service, String unitId) {
        AgreementRefusedException refused = assertThrows(AgreementRefusedException.class,
                () -> service.create(command(unitId), VIEWER), unitId);
        assertEquals(Reason.AGREEMENT_NOT_ELIGIBLE, refused.reason());
        assertTrue(refused.entries().stream().allMatch(entry -> entry.allowedValuesOrNull() == null));
        return refused.entries().stream().map(Entry::field).toList();
    }

    private static AgreementCreateCommand command(String unitId) {
        return new AgreementCreateCommand(LEAD, unitId, "LONG_TERM_RENT", START, END);
    }

    private static IntakeBasisFacts basis(String state, Unit... units) {
        return new IntakeBasisFacts("I1", "P1", state, List.of(units), OWNER, HOLDINGS);
    }

    private static BrokerageAgreement stored(String id, String basisLeadId, AgreementStatus status,
                                             VersionStatus versionStatus) {
        Instant at = Instant.parse("2026-11-01T00:00:00Z");
        return BrokerageAgreement.load(id, "U1", "P101", "P1", TransactionType.LONG_TERM_RENT, OWNER, basisLeadId,
                status, "S9", at, at, 0L, List.of(AgreementVersion.load("V" + id, 1, versionStatus,
                        new AgreementPeriod(START, END), basisLeadId, "I9", "S9", at, at)));
    }
}
