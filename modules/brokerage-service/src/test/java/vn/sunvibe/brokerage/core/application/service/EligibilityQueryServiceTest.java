package vn.sunvibe.brokerage.core.application.service;

import org.junit.jupiter.api.Test;
import vn.sunvibe.brokerage.core.application.exception.BrokerageNotFoundException;
import vn.sunvibe.brokerage.core.application.query.EligibilityView;
import vn.sunvibe.brokerage.core.application.query.EligibilityView.UnitEligibility;
import vn.sunvibe.brokerage.core.application.shared.external.account.OwnerAccountPort;
import vn.sunvibe.brokerage.core.application.shared.external.lead.BasisLeadFacts;
import vn.sunvibe.brokerage.core.application.shared.external.lead.BasisLeadPort;
import vn.sunvibe.brokerage.core.application.shared.external.property.IntakeBasisFacts;
import vn.sunvibe.brokerage.core.application.shared.external.property.IntakeBasisFacts.Holding;
import vn.sunvibe.brokerage.core.application.shared.external.property.IntakeBasisFacts.Unit;
import vn.sunvibe.brokerage.core.application.shared.external.property.IntakeBasisPort;
import vn.sunvibe.brokerage.core.application.usecase.StaffViewer;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementPeriod;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementStatus;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementVersion;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageAgreement;
import vn.sunvibe.brokerage.core.domain.model.agreement.TransactionType;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionStatus;
import vn.sunvibe.brokerage.core.testsupport.InMemoryAgreementRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.brokerage.core.domain.model.eligibility.ReasonGroup.OWNER;
import static vn.sunvibe.brokerage.core.domain.model.eligibility.ReasonGroup.UNIT;

/**
 * The eligibility of every unit of a lead's basis, read through the three ports (brokerage-agreement US-1 AC-1, AC-2;
 * E05-S1 spec 5.2, S1-D2, S1-D5, S1-D6, S1-D7).
 */
class EligibilityQueryServiceTest {

    private static final String LEAD = "01LEAD";
    private static final String SALE = "S";
    private static final String DECLARED_OWNER = "O";
    private static final StaffViewer VIEWER = new StaffViewer(SALE, Set.of("ROLE_SALE"));
    private static final IntakeBasisFacts BASIS = new IntakeBasisFacts("01INTAKE", "01PROPERTY", "USABLE",
            List.of(new Unit("U1", "P101", List.of()), new Unit("U2", "P102", List.of("UNIT_STOP"))), DECLARED_OWNER,
            List.of(new Holding(DECLARED_OWNER, "OWNER", "UNVERIFIED")));

    private final List<String> calls = new ArrayList<>();

    private BasisLeadPort lead(String assigneeOrNull) {
        return new BasisLeadPort() {
            @Override
            public Optional<BasisLeadFacts> findVisible(String leadId, String userId, Set<String> authorities) {
                calls.add("lead " + leadId + " " + userId + " " + authorities);
                return Optional.of(new BasisLeadFacts(leadId, assigneeOrNull));
            }

            @Override
            public Optional<BasisLeadFacts> findVisibleForUpdate(String leadId, String userId, Set<String> authorities) {
                throw new UnsupportedOperationException("the eligibility read never locks a lead");
            }
        };
    }

    private IntakeBasisPort basis(IntakeBasisFacts factsOrNull) {
        return leadId -> {
            calls.add("basis " + leadId);
            return Optional.ofNullable(factsOrNull);
        };
    }

    private OwnerAccountPort account(boolean eligible) {
        return userId -> {
            calls.add("account " + userId);
            return eligible;
        };
    }

    private long callsStartingWith(String prefix) {
        return calls.stream().filter(call -> call.startsWith(prefix)).count();
    }

    @Test
    void forLead_notVisible_notFound() {
        BasisLeadPort hidden = new BasisLeadPort() {
            @Override
            public Optional<BasisLeadFacts> findVisible(String leadId, String userId, Set<String> authorities) {
                calls.add("lead " + leadId);
                return Optional.empty();
            }

            @Override
            public Optional<BasisLeadFacts> findVisibleForUpdate(String leadId, String userId, Set<String> authorities) {
                throw new UnsupportedOperationException("the eligibility read never locks a lead");
            }
        };
        EligibilityQueryService service = new EligibilityQueryService(hidden, basis(BASIS), account(true),
                new InMemoryAgreementRepository());

        assertThrows(BrokerageNotFoundException.class, () -> service.forLead(LEAD, VIEWER));
        assertEquals(List.of("lead " + LEAD), calls);
    }

    @Test
    void forLead_noBasis_noneWithoutUnits() {
        EligibilityQueryService service = new EligibilityQueryService(lead(SALE), basis(null), account(true),
                new InMemoryAgreementRepository());

        EligibilityView view = service.forLead(LEAD, VIEWER);

        assertEquals(new EligibilityView("NONE", true, List.of()), view);
        assertEquals(0, callsStartingWith("account"));
    }

    @Test
    void forLead_basisUnits_groupsPerUnitInOrder() {
        EligibilityQueryService service = new EligibilityQueryService(lead(SALE), basis(BASIS), account(true),
                new InMemoryAgreementRepository());

        EligibilityView view = service.forLead(LEAD, VIEWER);

        assertEquals("USABLE", view.basisState());
        assertEquals(List.of(new UnitEligibility("U1", "P101", List.of(), false, null),
                        new UnitEligibility("U2", "P102", List.of(UNIT), false, null)),
                view.units());
        assertTrue(view.units().get(0).eligible());
        assertFalse(view.units().get(1).eligible());
        assertEquals(List.of("lead " + LEAD + " " + SALE + " [ROLE_SALE]", "basis " + LEAD, "account " + DECLARED_OWNER),
                calls);
    }

    @Test
    void forLead_declaredOwner_accountReadOnce() {
        EligibilityQueryService service = new EligibilityQueryService(lead(SALE), basis(BASIS), account(false),
                new InMemoryAgreementRepository());

        EligibilityView view = service.forLead(LEAD, VIEWER);

        assertEquals(List.of(OWNER), view.units().get(0).reasonGroups());
        assertEquals(List.of(UNIT, OWNER), view.units().get(1).reasonGroups());
        assertEquals(List.of("account " + DECLARED_OWNER), calls.stream().filter(call -> call.startsWith("account")).toList());
    }

    @Test
    void forLead_noDeclaredOwner_ownerWithoutAccountRead() {
        IntakeBasisFacts ownerless = new IntakeBasisFacts("01INTAKE", "01PROPERTY", "USABLE", BASIS.units(), null,
                BASIS.holdings());
        EligibilityQueryService service = new EligibilityQueryService(lead(SALE), basis(ownerless), account(true),
                new InMemoryAgreementRepository());

        EligibilityView view = service.forLead(LEAD, VIEWER);

        view.units().forEach(unit -> assertTrue(unit.reasonGroups().contains(OWNER), unit.toString()));
        assertEquals(0, callsStartingWith("account"));
    }

    @Test
    void forLead_unitWithoutCode_keepsNullCode() {
        IntakeBasisFacts noCode = new IntakeBasisFacts("01INTAKE", "01PROPERTY", "USABLE",
                List.of(new Unit("U3", null, List.of())), DECLARED_OWNER, BASIS.holdings());
        EligibilityQueryService service = new EligibilityQueryService(lead(SALE), basis(noCode), account(true),
                new InMemoryAgreementRepository());

        EligibilityView view = service.forLead(LEAD, VIEWER);

        assertEquals("U3", view.units().get(0).unitId());
        assertNull(view.units().get(0).unitCodeOrNull());
    }

    @Test
    void forLead_viewer_assignedSaleFlag() {
        EligibilityQueryService service = new EligibilityQueryService(lead(SALE), basis(BASIS), account(true),
                new InMemoryAgreementRepository());

        assertFalse(service.forLead(LEAD, new StaffViewer("M", Set.of("ROLE_SALES_MANAGER"))).viewerIsAssignedSale());
        assertTrue(service.forLead(LEAD, VIEWER).viewerIsAssignedSale());
    }

    /** E05-S2 spec S2-D11, AD-10: the unit says it has an agreement in progress, with its id when it rests on this lead. */
    @Test
    void forLead_unfinishedAgreementOfThisLead_flagAndId() {
        InMemoryAgreementRepository agreements = new InMemoryAgreementRepository().store(drafting("A1", "U1", LEAD));
        EligibilityQueryService service = new EligibilityQueryService(lead(SALE), basis(BASIS), account(true),
                agreements);

        EligibilityView view = service.forLead(LEAD, VIEWER);

        assertEquals(List.of(new UnitEligibility("U1", "P101", List.of(), true, "A1"),
                new UnitEligibility("U2", "P102", List.of(UNIT), false, null)), view.units());
        assertTrue(view.units().get(0).eligible());
        assertFalse(view.units().get(1).eligible());
        assertEquals(1, agreements.count("unfinishedOf"));
    }

    /** brokerage-agreement BR-17: an agreement that rests on another lead is said to exist, never named. */
    @Test
    void forLead_unfinishedAgreementFromAnotherLead_flagWithoutId() {
        InMemoryAgreementRepository agreements = new InMemoryAgreementRepository().store(drafting("A1", "U1", "01OTHER"));
        EligibilityQueryService service = new EligibilityQueryService(lead(SALE), basis(BASIS), account(true),
                agreements);

        EligibilityView view = service.forLead(LEAD, VIEWER);

        assertEquals(new UnitEligibility("U1", "P101", List.of(), true, null), view.units().get(0));
        assertEquals(new UnitEligibility("U2", "P102", List.of(UNIT), false, null), view.units().get(1));
    }

    @Test
    void forLead_noBasis_noAgreementLookup() {
        InMemoryAgreementRepository agreements = new InMemoryAgreementRepository();
        EligibilityQueryService service = new EligibilityQueryService(lead(SALE), basis(null), account(true), agreements);

        service.forLead(LEAD, VIEWER);

        assertEquals(List.of(), agreements.calls());
    }

    private static BrokerageAgreement drafting(String id, String unitId, String basisLeadId) {
        Instant at = Instant.parse("2026-11-01T00:00:00Z");
        AgreementPeriod period = new AgreementPeriod(LocalDate.parse("2027-01-01"), LocalDate.parse("2027-12-31"));
        return BrokerageAgreement.load(id, unitId, "P101", "01PROPERTY", TransactionType.LONG_TERM_RENT, DECLARED_OWNER,
                basisLeadId, AgreementStatus.DRAFTING, SALE, at, at, 0L, List.of(AgreementVersion.load("V" + id, 1,
                        VersionStatus.DRAFT, period, basisLeadId, "01INTAKE", SALE, at, at)));
    }
}
