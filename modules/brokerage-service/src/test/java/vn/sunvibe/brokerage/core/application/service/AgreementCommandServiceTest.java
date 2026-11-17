package vn.sunvibe.brokerage.core.application.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;
import vn.sunvibe.brokerage.core.application.command.AgreementConfirmCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementCreateCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementDraftCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementRejectCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementSendCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementVersionInput;
import vn.sunvibe.brokerage.core.application.exception.AgreementRefusedException;
import vn.sunvibe.brokerage.core.application.exception.AgreementRefusedException.Entry;
import vn.sunvibe.brokerage.core.application.exception.AgreementRefusedException.Reason;
import vn.sunvibe.brokerage.core.application.exception.BrokerageNotFoundException;
import vn.sunvibe.brokerage.core.application.shared.external.account.PasswordCheck;
import vn.sunvibe.brokerage.core.application.shared.external.property.IntakeBasisFacts;
import vn.sunvibe.brokerage.core.application.shared.external.property.IntakeBasisFacts.Holding;
import vn.sunvibe.brokerage.core.application.shared.external.property.IntakeBasisFacts.Unit;
import vn.sunvibe.brokerage.core.application.usecase.AgreementCreateResult;
import vn.sunvibe.brokerage.core.application.usecase.StaffViewer;
import vn.sunvibe.brokerage.core.domain.exception.AgreementContentException;
import vn.sunvibe.brokerage.core.domain.exception.AgreementPeriodException;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementPeriod;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementStatement;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementStatus;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementVersion;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageAgreement;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageFee;
import vn.sunvibe.brokerage.core.domain.model.agreement.ChargeUnit;
import vn.sunvibe.brokerage.core.domain.model.agreement.DepositBasis;
import vn.sunvibe.brokerage.core.domain.model.agreement.EarlyLeaseEndRefund;
import vn.sunvibe.brokerage.core.domain.model.agreement.EarlyLeaseEndType;
import vn.sunvibe.brokerage.core.domain.model.agreement.FeeBasis;
import vn.sunvibe.brokerage.core.domain.model.agreement.FeeFormula;
import vn.sunvibe.brokerage.core.domain.model.agreement.FeeFormulaType;
import vn.sunvibe.brokerage.core.domain.model.agreement.FeeTier;
import vn.sunvibe.brokerage.core.domain.model.agreement.MandatoryFee;
import vn.sunvibe.brokerage.core.domain.model.agreement.OwnerAnswer;
import vn.sunvibe.brokerage.core.domain.model.agreement.PhotoConsent;
import vn.sunvibe.brokerage.core.domain.model.agreement.TermsDeposit;
import vn.sunvibe.brokerage.core.domain.model.agreement.TransactionType;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionStatus;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionTerms;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementEventType;
import vn.sunvibe.brokerage.core.testsupport.FixedAgreementSwitches;
import vn.sunvibe.brokerage.core.testsupport.InMemoryAgreementRepository;
import vn.sunvibe.brokerage.core.testsupport.InMemoryBasisLeadPort;
import vn.sunvibe.brokerage.core.testsupport.InMemoryIntakeBasisPort;
import vn.sunvibe.brokerage.core.testsupport.NoDatabaseTransactions;
import vn.sunvibe.brokerage.core.testsupport.RecordingAccountPasswordPort;
import vn.sunvibe.brokerage.core.testsupport.RecordingAgreementTrail;
import vn.sunvibe.brokerage.core.testsupport.RecordingOwnerAccountPort;
import vn.sunvibe.brokerage.core.testsupport.RecordingOwnerNoticePort;
import vn.sunvibe.shared.time.AppClock;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
    private static final String AGREEMENT = "01JAGRE0000000000000000001";
    private static final PhotoConsent CONSENT = new PhotoConsent(true, false);
    private static final VersionTerms TERMS = new VersionTerms(8_000_000L,
            new TermsDeposit(DepositBasis.MONTHS, 2, null), 12, 1,
            List.of(new MandatoryFee("Electricity", FeeBasis.AMOUNT, 3_500L, ChargeUnit.PER_KWH),
                    new MandatoryFee("Water", FeeBasis.PROVIDER_TARIFF, null, null)));

    private final List<String> calls = new ArrayList<>();
    private final InMemoryBasisLeadPort leads = new InMemoryBasisLeadPort(calls).lead(LEAD, SALE, SALE, "M", "S2");
    private final InMemoryIntakeBasisPort bases = new InMemoryIntakeBasisPort(calls).basis(LEAD, BASIS);
    private final RecordingOwnerAccountPort accounts = new RecordingOwnerAccountPort(calls).answer(OWNER, true);
    private final InMemoryAgreementRepository agreements = new InMemoryAgreementRepository();
    private final RecordingAgreementTrail trail = new RecordingAgreementTrail();
    private final Iterator<String> ids = List.of("A1", "V1", "A2", "V2", "A3", "V3").iterator();
    private final RecordingOwnerNoticePort notices = new RecordingOwnerNoticePort();
    private final RecordingAccountPasswordPort passwords = new RecordingAccountPasswordPort();
    private FixedAgreementSwitches switches = new FixedAgreementSwitches();

    private AgreementCommandService service() {
        return new AgreementCommandService(leads, bases, accounts, agreements, trail, ids::next, notices, switches,
                passwords, new TransactionTemplate(new NoDatabaseTransactions()));
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
                ids::next, notices, switches, passwords, new TransactionTemplate(new NoDatabaseTransactions()));

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
                ids::next, notices, switches, passwords, new TransactionTemplate(new NoDatabaseTransactions()));

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
                ids::next, notices, switches, passwords, new TransactionTemplate(new NoDatabaseTransactions()));
        assertEquals(new AgreementCreateResult("A1"), service.create(command("U1"), VIEWER));
        assertEquals(2, cancelled.all().size());
    }

    @Test
    void create_unitWithoutCode_keepsNullCode() {
        bases.basis(LEAD, basis("USABLE", new Unit("U3", null, List.of())));

        service().create(command("U3"), VIEWER);

        assertNull(agreements.all().get(0).getUnitCodeOrNull());
    }

    // ==================== E05-S3: editing the draft (spec 3.1, S3-D2 to S3-D5, S3-D10) ====================

    @Test
    void editDraft_assignedSale_writesContentAndOneEvent() {
        BrokerageAgreement before = storeDraft("U1", AgreementStatus.DRAFTING, VersionStatus.DRAFT);

        service().editDraft(draftCommand(0L), VIEWER);

        BrokerageAgreement stored = agreements.findById(AGREEMENT).orElseThrow();
        assertEquals(1L, stored.getRowVersionOrNull());
        assertEquals(before.getUnitId(), stored.getUnitId());
        assertEquals(before.getOwnerUserId(), stored.getOwnerUserId());
        assertEquals(before.getBasisLeadId(), stored.getBasisLeadId());
        assertEquals(before.getStatus(), stored.getStatus());
        assertEquals(before.getCreatedByUserId(), stored.getCreatedByUserId());
        assertEquals(before.getCreatedAt(), stored.getCreatedAt());
        AgreementVersion version = stored.getVersions().get(0);
        assertEquals(VersionStatus.DRAFT, version.getStatus());
        assertEquals("I1", version.getBasisIntakeId());
        assertEquals("S9", version.getCreatedByUserId());
        assertEquals(new AgreementPeriod(START, END), version.getPeriod());
        assertEquals(CONSENT, version.getPhotoConsent());
        assertEquals(TERMS, version.getTerms());
        assertEquals(1, trail.events().size());
        RecordingAgreementTrail.Event event = trail.events().get(0);
        assertEquals(new RecordingAgreementTrail.Event(AGREEMENT, 1, AgreementEventType.DRAFT_EDITED, SALE, event.at()),
                event);
        assertEquals(event.at(), stored.getUpdatedAt());
        assertEquals(event.at(), version.getUpdatedAt());
        assertEquals(0, event.at().getNano() % 1_000);
        assertEquals(List.of(), trail.accesses());
    }

    @Test
    void editDraft_steps_agreementThenLockedLeadThenBasisThenOwner() {
        storeDraft("U1", AgreementStatus.DRAFTING, VersionStatus.DRAFT);

        service().editDraft(draftCommand(0L), VIEWER);

        assertEquals(List.of("findById", "update"), agreements.calls());
        assertEquals(List.of("forUpdate " + LEAD + " " + SALE, "basis " + LEAD, "account " + OWNER), calls);
    }

    @Test
    void editDraft_typeOrPeriodRefused_beforeAnyRead() {
        storeDraft("U1", AgreementStatus.DRAFTING, VersionStatus.DRAFT);

        AgreementRefusedException type = assertThrows(AgreementRefusedException.class, () -> service().editDraft(
                new AgreementDraftCommand(AGREEMENT, 0L, "SALE", START, END, CONSENT, TERMS, BrokerageFee.NONE), VIEWER));
        assertEquals(Reason.VALIDATION_FAILED, type.reason());
        assertEquals(List.of(new Entry("transactionType", List.of("LONG_TERM_RENT"))), type.entries());

        AgreementPeriodException period = assertThrows(AgreementPeriodException.class, () -> service().editDraft(
                new AgreementDraftCommand(AGREEMENT, 0L, "LONG_TERM_RENT", START, LocalDate.parse("2028-01-02"),
                        CONSENT, TERMS, BrokerageFee.NONE), VIEWER));
        assertEquals("endDate", period.field());

        assertEquals(List.of(), calls);
        assertEquals(List.of(), agreements.calls());
        assertEquals(List.of(), trail.events());
    }

    @Test
    void editDraft_unknownMalformedOrHiddenLead_notFound() {
        storeDraft("U1", AgreementStatus.DRAFTING, VersionStatus.DRAFT);

        for (String id : List.of("a1", "01jagre0000000000000000001", "01JAGRE0000000000000000009")) {
            assertThrows(BrokerageNotFoundException.class, () -> service().editDraft(
                    new AgreementDraftCommand(id, 0L, "LONG_TERM_RENT", START, END, CONSENT, TERMS, BrokerageFee.NONE),
                    VIEWER), id);
        }
        assertEquals(List.of(), calls);
        assertEquals(List.of("findById"), agreements.calls());

        StaffViewer stranger = new StaffViewer("S3", Set.of("ROLE_SALE"));
        assertThrows(BrokerageNotFoundException.class, () -> service().editDraft(draftCommand(0L), stranger));

        assertEquals(List.of("forUpdate " + LEAD + " S3"), calls);
        assertEquals(0, agreements.count("update"));
        assertEquals(List.of(), trail.events());
    }

    @Test
    void editDraft_notAssignee_refused() {
        storeDraft("U1", AgreementStatus.DRAFTING, VersionStatus.DRAFT);

        for (StaffViewer viewer : List.of(new StaffViewer("M", Set.of("ROLE_SALES_MANAGER")),
                new StaffViewer("S2", Set.of("ROLE_ADMIN", "ROLE_SUPER_ADMIN")),
                new StaffViewer(SALE, Set.of("ROLE_SALES_MANAGER")))) {
            AgreementRefusedException refused = assertThrows(AgreementRefusedException.class,
                    () -> service().editDraft(draftCommand(0L), viewer), viewer.toString());

            assertEquals(Reason.AGREEMENT_NOT_ASSIGNEE, refused.reason());
            assertEquals(List.of(), refused.entries());
        }
        assertEquals(0, calls.stream().filter(call -> call.startsWith("basis")).count());
        assertEquals(0, agreements.count("update"));
        assertEquals(List.of(), trail.events());
    }

    @Test
    void editDraft_noDraft_notDraft() {
        for (AgreementStatus status : List.of(AgreementStatus.DRAFTING, AgreementStatus.CANCELLED)) {
            VersionStatus versionStatus = status == AgreementStatus.DRAFTING ? VersionStatus.AWAITING_OWNER
                    : VersionStatus.CANCELLED;
            storeDraft("U1", status, versionStatus);

            AgreementRefusedException refused = assertThrows(AgreementRefusedException.class,
                    () -> service().editDraft(draftCommand(0L), VIEWER), status.name());

            assertEquals(Reason.AGREEMENT_NOT_DRAFT, refused.reason());
            assertEquals("This agreement has no draft to edit.", refused.getMessage());
            assertEquals(List.of(), refused.entries());
        }
        assertEquals(0, calls.stream().filter(call -> call.startsWith("basis")).count());
        assertEquals(0, agreements.count("update"));
        assertEquals(List.of(), trail.events());
    }

    @Test
    void editDraft_staleDataVersion_concurrentModificationWithEntry() {
        storeDraft("U1", AgreementStatus.DRAFTING, VersionStatus.DRAFT);

        AgreementRefusedException refused = assertThrows(AgreementRefusedException.class,
                () -> service().editDraft(draftCommand(1L), VIEWER));

        assertEquals(Reason.CONCURRENT_MODIFICATION, refused.reason());
        assertEquals("Something changed at the same time. Try again.", refused.getMessage());
        assertEquals(List.of(new Entry("expectedDataVersion", null)), refused.entries());
        assertEquals(0, calls.stream().filter(call -> call.startsWith("basis")).count());
        assertEquals(0, agreements.count("update"));
        assertEquals(List.of(), trail.events());
    }

    @Test
    void editDraft_basisNotTheDraftsOrPolicyGroups_notEligibleInOrder() {
        storeDraft("U1", AgreementStatus.DRAFTING, VersionStatus.DRAFT);
        InMemoryIntakeBasisPort none = new InMemoryIntakeBasisPort(calls);
        assertEquals(List.of("reasonGroups.BASIS"), refusedDraftFields(
                new AgreementCommandService(leads, none, accounts, agreements, trail, ids::next, notices, switches,
                        passwords, new TransactionTemplate(new NoDatabaseTransactions()))));

        calls.clear();
        bases.basis(LEAD, new IntakeBasisFacts("I2", "P1", "USABLE", BASIS.units(), OWNER, HOLDINGS));
        assertEquals(List.of("reasonGroups.BASIS"), refusedDraftFields(service()));
        assertEquals(0, calls.stream().filter(call -> call.startsWith("account")).count());

        bases.basis(LEAD, BASIS);
        storeDraft("U2", AgreementStatus.DRAFTING, VersionStatus.DRAFT);
        assertEquals(List.of("reasonGroups.UNIT"), refusedDraftFields(service()));

        bases.basis(LEAD, basis("SUSPENDED", BASIS.units().toArray(Unit[]::new)));
        accounts.answer(OWNER, false);
        assertEquals(List.of("reasonGroups.BASIS", "reasonGroups.UNIT", "reasonGroups.OWNER"),
                refusedDraftFields(service()));

        bases.basis(LEAD, basis("USABLE", new Unit("U2", "P102", List.of("PROPERTY_STOP"))));
        accounts.answer(OWNER, true);
        assertEquals(List.of("reasonGroups.PROPERTY"), refusedDraftFields(service()));

        assertEquals(0, agreements.count("update"));
        assertEquals(List.of(), trail.events());
    }

    @Test
    void editDraft_ownerPartyNoLongerDeclared_owner() {
        storeDraft("U1", AgreementStatus.DRAFTING, VersionStatus.DRAFT);
        IntakeBasisFacts declaredO2 = new IntakeBasisFacts("I1", "P1", "USABLE", BASIS.units(), "O2",
                List.of(new Holding("O2", "OWNER", "UNVERIFIED")));
        bases.basis(LEAD, declaredO2);
        accounts.answer("O2", true);

        assertEquals(List.of("reasonGroups.OWNER"), refusedDraftFields(service()));

        storeDraft("U2", AgreementStatus.DRAFTING, VersionStatus.DRAFT);
        assertEquals(List.of("reasonGroups.UNIT", "reasonGroups.OWNER"), refusedDraftFields(service()));
        assertEquals(0, agreements.count("update"));
        assertEquals(List.of(), trail.events());
    }

    @Test
    void editDraft_availabilityDiffersOnly_saved() {
        storeDraft("U1", AgreementStatus.DRAFTING, VersionStatus.DRAFT);
        bases.basis(LEAD, basis("USABLE", new Unit("U1", "P101", List.of("AVAILABILITY_DIFFERS"))));

        service().editDraft(draftCommand(0L), VIEWER);

        assertEquals(1, agreements.count("update"));
        assertEquals(1, trail.events().size());
    }

    @Test
    void editDraft_lostGuardedUpdate_propagates() {
        storeDraft("U1", AgreementStatus.DRAFTING, VersionStatus.DRAFT);
        agreements.moveAfterNextRead();

        assertThrows(OptimisticLockingFailureException.class, () -> service().editDraft(draftCommand(0L), VIEWER));

        assertEquals(List.of("findById", "update"), agreements.calls());
        assertEquals(List.of(), trail.events());
    }

    // ==================== E05-S4: the fee policy through the edit (spec S4-D1, 5.2) ====================

    private static final BrokerageFee FEE = new BrokerageFee(
            FeeFormula.of(FeeFormulaType.PERCENT_OF_RENT, List.of(new FeeTier(1, 50), new FeeTier(12, 100)), null, null),
            7, FeeFormula.of(FeeFormulaType.NOT_CHARGED, null, null, null),
            new EarlyLeaseEndRefund(EarlyLeaseEndType.PARTIAL_REFUND, 50, 3), 90);

    @Test
    void editDraft_feePolicy_passedToTheDraft() {
        storeDraft("U1", AgreementStatus.DRAFTING, VersionStatus.DRAFT);

        service().editDraft(new AgreementDraftCommand(AGREEMENT, 0L, "LONG_TERM_RENT", START, END, CONSENT, TERMS, FEE),
                VIEWER);

        assertEquals(FEE, agreements.findById(AGREEMENT).orElseThrow().getVersions().get(0).getFee());

        service().editDraft(draftCommand(1L), VIEWER);

        assertEquals(BrokerageFee.NONE, agreements.findById(AGREEMENT).orElseThrow().getVersions().get(0).getFee());
        assertEquals(2, trail.events().size());
    }

    // ==================== E05-S4: sending the draft (spec 3.2, S4-D5 to S4-D9, S4-D11, S4-D13) ====================

    private static final List<String> ELEVEN = List.of("photoConsent", "terms.monthlyRent", "terms.deposit",
            "terms.minimumTermMonths", "terms.paymentPeriodMonths", "terms.mandatoryFees", "feePolicy.formula",
            "feePolicy.dueDays", "feePolicy.renewal", "feePolicy.earlyLeaseEnd", "feePolicy.protectionDays");

    @Test
    void sendDraft_complete_sentNoticeAndOneEvent() {
        BrokerageAgreement before = storeComplete("U1", BrokerageFeeOf.COMPLETE);

        service().sendDraft(new AgreementSendCommand(AGREEMENT, 0L), VIEWER);

        BrokerageAgreement stored = agreements.findById(AGREEMENT).orElseThrow();
        assertEquals(AgreementStatus.AWAITING_CONFIRMATION, stored.getStatus());
        assertEquals(1L, stored.getRowVersionOrNull());
        assertEquals(before.getUnitId(), stored.getUnitId());
        assertEquals(before.getOwnerUserId(), stored.getOwnerUserId());
        assertEquals(before.getBasisLeadId(), stored.getBasisLeadId());
        assertEquals(before.getCreatedByUserId(), stored.getCreatedByUserId());
        assertEquals(before.getCreatedAt(), stored.getCreatedAt());
        AgreementVersion version = stored.getVersions().get(0);
        assertEquals(VersionStatus.AWAITING_OWNER, version.getStatus());
        assertEquals(CONSENT, version.getPhotoConsent());
        assertEquals(TERMS, version.getTerms());
        assertEquals(FEE, version.getFee());
        assertEquals("S9", version.getCreatedByUserId());
        assertEquals(1, trail.events().size());
        RecordingAgreementTrail.Event event = trail.events().get(0);
        assertEquals(new RecordingAgreementTrail.Event(AGREEMENT, 1, AgreementEventType.VERSION_SENT, SALE, event.at()),
                event);
        assertEquals(event.at(), version.getSentAtOrNull());
        assertEquals(event.at(), version.getUpdatedAt());
        assertEquals(event.at(), stored.getUpdatedAt());
        assertEquals(0, event.at().getNano() % 1_000);
        assertEquals(List.of(OWNER), notices.agreementPending());
        assertEquals(List.of(), trail.accesses());
        assertEquals(List.of("findById", "update", "findById"), agreements.calls());
        assertEquals(List.of("forUpdate " + LEAD + " " + SALE, "basis " + LEAD, "account " + OWNER), calls);
    }

    @Test
    void sendDraft_stepOrder_firstFailingStepAnswers() {
        StaffViewer manager = new StaffViewer("M", Set.of("ROLE_SALES_MANAGER"));
        storeDraft("U1", AgreementStatus.DRAFTING, VersionStatus.AWAITING_OWNER);
        assertEquals(Reason.AGREEMENT_NOT_ASSIGNEE, sendRefused(new AgreementSendCommand(AGREEMENT, 5L), manager).reason());
        assertEquals(Reason.AGREEMENT_NOT_DRAFT, sendRefused(new AgreementSendCommand(AGREEMENT, 5L), VIEWER).reason());

        storeDraft("U2", AgreementStatus.DRAFTING, VersionStatus.DRAFT);
        assertEquals(Reason.CONCURRENT_MODIFICATION,
                sendRefused(new AgreementSendCommand(AGREEMENT, 5L), VIEWER).reason());
        assertEquals(Reason.AGREEMENT_VERSION_INCOMPLETE,
                sendRefused(new AgreementSendCommand(AGREEMENT, 0L), VIEWER).reason());

        storeComplete("U2", BrokerageFeeOf.COMPLETE);
        switches = FixedAgreementSwitches.off();
        assertEquals(Reason.AGREEMENT_NOT_ELIGIBLE, sendRefused(new AgreementSendCommand(AGREEMENT, 0L), VIEWER).reason());

        assertNothingSent();
    }

    @Test
    void sendDraft_notAssignee_refused() {
        storeComplete("U1", BrokerageFeeOf.COMPLETE);

        for (StaffViewer viewer : List.of(new StaffViewer("M", Set.of("ROLE_SALES_MANAGER")),
                new StaffViewer("S2", Set.of("ROLE_ADMIN", "ROLE_SUPER_ADMIN")),
                new StaffViewer(SALE, Set.of("ROLE_SALES_MANAGER")))) {
            AgreementRefusedException refused = sendRefused(new AgreementSendCommand(AGREEMENT, 0L), viewer);

            assertEquals(Reason.AGREEMENT_NOT_ASSIGNEE, refused.reason());
            assertEquals(List.of(), refused.entries());
        }
        assertEquals(0, calls.stream().filter(call -> call.startsWith("basis")).count());
        assertNothingSent();

        StaffViewer stranger = new StaffViewer("S3", Set.of("ROLE_SALE"));
        assertThrows(BrokerageNotFoundException.class,
                () -> service().sendDraft(new AgreementSendCommand(AGREEMENT, 0L), stranger));
        for (String id : List.of("a1", "01jagre0000000000000000001", "01JAGRE0000000000000000009")) {
            assertThrows(BrokerageNotFoundException.class,
                    () -> service().sendDraft(new AgreementSendCommand(id, 0L), VIEWER), id);
        }
        assertNothingSent();
    }

    @Test
    void sendDraft_noDraft_notDraft() {
        for (AgreementStatus status : List.of(AgreementStatus.DRAFTING, AgreementStatus.AWAITING_CONFIRMATION,
                AgreementStatus.CANCELLED)) {
            VersionStatus versionStatus = status == AgreementStatus.CANCELLED ? VersionStatus.CANCELLED
                    : VersionStatus.AWAITING_OWNER;
            storeDraft("U1", status, versionStatus);

            AgreementRefusedException refused = sendRefused(new AgreementSendCommand(AGREEMENT, 0L), VIEWER);

            assertEquals(Reason.AGREEMENT_NOT_DRAFT, refused.reason(), status.name());
            assertEquals("This agreement has no draft to edit.", refused.getMessage());
            assertEquals(List.of(), refused.entries());
        }
        assertEquals(0, calls.stream().filter(call -> call.startsWith("basis")).count());
        assertNothingSent();
    }

    @Test
    void sendDraft_staleDataVersion_concurrentModification() {
        storeComplete("U1", BrokerageFeeOf.COMPLETE);

        AgreementRefusedException refused = sendRefused(new AgreementSendCommand(AGREEMENT, 1L), VIEWER);

        assertEquals(Reason.CONCURRENT_MODIFICATION, refused.reason());
        assertEquals(List.of(new Entry("expectedDataVersion", null)), refused.entries());
        assertEquals(0, calls.stream().filter(call -> call.startsWith("basis")).count());
        assertNothingSent();
    }

    @Test
    void sendDraft_incomplete_partsInOrder() {
        storeDraft("U1", AgreementStatus.DRAFTING, VersionStatus.DRAFT);

        AgreementRefusedException created = sendRefused(new AgreementSendCommand(AGREEMENT, 0L), VIEWER);

        assertEquals(Reason.AGREEMENT_VERSION_INCOMPLETE, created.reason());
        assertEquals(409, created.reason().status());
        assertEquals("This version cannot be sent yet: a part is missing.", created.getMessage());
        assertEquals("This part is missing.", created.reason().entryMessageOrNull());
        assertEquals(ELEVEN, created.entries().stream().map(Entry::field).toList());
        assertTrue(created.entries().stream().allMatch(entry -> entry.allowedValuesOrNull() == null));

        storeComplete("U1", BrokerageFeeOf.WITHOUT_DUE_DAYS);
        assertEquals(List.of(new Entry("feePolicy.dueDays", null)),
                sendRefused(new AgreementSendCommand(AGREEMENT, 0L), VIEWER).entries());
        assertEquals(0, calls.stream().filter(call -> call.startsWith("basis")).count());
        assertNothingSent();
    }

    @Test
    void sendDraft_notEligible_groups() {
        storeComplete("U2", BrokerageFeeOf.COMPLETE);
        assertEquals(List.of("reasonGroups.UNIT"), sendRefusedFields());

        storeComplete("U1", BrokerageFeeOf.COMPLETE);
        bases.basis(LEAD, new IntakeBasisFacts("I2", "P1", "USABLE", BASIS.units(), OWNER, HOLDINGS));
        assertEquals(List.of("reasonGroups.BASIS"), sendRefusedFields());

        bases.basis(LEAD, basis("SUSPENDED", BASIS.units().toArray(Unit[]::new)));
        accounts.answer(OWNER, false);
        assertEquals(List.of("reasonGroups.BASIS", "reasonGroups.OWNER"), sendRefusedFields());

        bases.basis(LEAD, new IntakeBasisFacts("I1", "P1", "USABLE", BASIS.units(), "O2",
                List.of(new Holding("O2", "OWNER", "UNVERIFIED"))));
        accounts.answer("O2", true);
        assertEquals(List.of("reasonGroups.OWNER"), sendRefusedFields());

        assertNothingSent();

        bases.basis(LEAD, basis("USABLE", new Unit("U1", "P101", List.of("AVAILABILITY_DIFFERS"))));
        accounts.answer(OWNER, true);
        service().sendDraft(new AgreementSendCommand(AGREEMENT, 0L), VIEWER);
        assertEquals(List.of(OWNER), notices.agreementPending());
    }

    /** brokerage-agreement BR-19 (spec S4-D8): the switch answers last, so every other refusal still tells the Sale. */
    @Test
    void sendDraft_switchOff_refusedLastNothingWritten() {
        storeComplete("U1", BrokerageFeeOf.COMPLETE);
        switches = FixedAgreementSwitches.off();

        AgreementRefusedException refused = sendRefused(new AgreementSendCommand(AGREEMENT, 0L), VIEWER);

        assertEquals(Reason.AGREEMENT_SENDING_NOT_AVAILABLE, refused.reason());
        assertEquals(409, refused.reason().status());
        assertEquals("Sending to owners is not open on this server.", refused.getMessage());
        assertEquals(List.of(), refused.entries());
        assertEquals(List.of("forUpdate " + LEAD + " " + SALE, "basis " + LEAD, "account " + OWNER), calls);
        assertNothingSent();
    }

    @Test
    void sendDraft_lostGuardedUpdate_propagatesNoEventNoNotice() {
        storeComplete("U1", BrokerageFeeOf.COMPLETE);
        agreements.moveAfterNextRead();

        assertThrows(OptimisticLockingFailureException.class,
                () -> service().sendDraft(new AgreementSendCommand(AGREEMENT, 0L), VIEWER));

        assertEquals(List.of("findById", "update"), agreements.calls());
        assertEquals(List.of(), trail.events());
        assertEquals(List.of(), notices.agreementPending());
    }

    /**
     * Pin (E05-S5 spec section 10, U-1, U-5): a DRAFTING agreement whose version 1 the owner answered and whose version 2
     * is a complete draft sends version 2 as it sends a first draft, and keeps version 1 as it was.
     */
    @Test
    void sendDraft_secondVersionAfterAnAnsweredOne_sent() {
        Instant at = Instant.parse("2026-11-01T00:00:00Z");
        Instant firstSent = Instant.parse("2026-11-02T00:00:00Z");
        AgreementPeriod period = new AgreementPeriod(START, START.plusMonths(6));
        AgreementVersion first = AgreementVersion.load("V1", 1, VersionStatus.OWNER_REJECTED, period, LEAD, "I1", "S9",
                at, firstSent, CONSENT, TERMS, FEE, firstSent);
        AgreementVersion second = AgreementVersion.load("V2", 2, VersionStatus.DRAFT, period, LEAD, "I1", "S9", at, at,
                CONSENT, TERMS, FEE, null);
        agreements.store(BrokerageAgreement.load(AGREEMENT, "U1", "P101", "P1", TransactionType.LONG_TERM_RENT, OWNER,
                LEAD, AgreementStatus.DRAFTING, "S9", at, at, 0L, List.of(first, second)));

        service().sendDraft(new AgreementSendCommand(AGREEMENT, 0L), VIEWER);

        BrokerageAgreement stored = agreements.findById(AGREEMENT).orElseThrow();
        assertEquals(AgreementStatus.AWAITING_CONFIRMATION, stored.getStatus());
        assertEquals(2, stored.getVersions().size());
        assertEquals(1, trail.events().size());
        RecordingAgreementTrail.Event event = trail.events().get(0);
        assertEquals(new RecordingAgreementTrail.Event(AGREEMENT, 2, AgreementEventType.VERSION_SENT, SALE, event.at()),
                event);
        AgreementVersion sent = stored.getVersions().get(1);
        assertEquals("V2", sent.getId());
        assertEquals(VersionStatus.AWAITING_OWNER, sent.getStatus());
        assertEquals(event.at(), sent.getSentAtOrNull());
        assertEquals(event.at(), stored.getUpdatedAt());
        assertEquals(List.of(OWNER), notices.agreementPending());
        AgreementVersion kept = stored.getVersions().get(0);
        assertEquals("V1", kept.getId());
        assertEquals(VersionStatus.OWNER_REJECTED, kept.getStatus());
        assertEquals(firstSent, kept.getSentAtOrNull());
        assertEquals(firstSent, kept.getUpdatedAt());
        assertEquals(period, kept.getPeriod());
        assertEquals(CONSENT, kept.getPhotoConsent());
        assertEquals(TERMS, kept.getTerms());
        assertEquals(FEE, kept.getFee());
    }

    // ==================== E05-S5: the owner's confirmation (spec 3.2, S5-D4 to S5-D6) ====================

    private static final String AGREEMENT_2 = "01JAGRE0000000000000000002";

    /** OC-1, OC-2: each version recorded on its own, with its own history row, in agreement id order. */
    @Test
    void confirmByOwner_answerable_eachRecordedWithItsRow() {
        bases.basis(LEAD, basis("USABLE", new Unit("U1", "P101", List.of()), new Unit("U3", "P103", List.of())));
        storeSent(AGREEMENT, "U1", clock().minus(1, ChronoUnit.HOURS));
        storeSent(AGREEMENT_2, "U3", clock().minus(2, ChronoUnit.HOURS));

        service().confirmByOwner(confirm(AGREEMENT_2, AGREEMENT), OWNER);

        assertEquals(2, trail.events().size());
        RecordingAgreementTrail.Event first = trail.events().get(0);
        Instant at = first.at();
        assertEquals(new RecordingAgreementTrail.Event(AGREEMENT, 1, AgreementEventType.OWNER_CONFIRMED, OWNER, at),
                first);
        assertEquals(new RecordingAgreementTrail.Event(AGREEMENT_2, 1, AgreementEventType.OWNER_CONFIRMED, OWNER, at),
                trail.events().get(1));
        for (String id : List.of(AGREEMENT, AGREEMENT_2)) {
            BrokerageAgreement stored = agreements.findById(id).orElseThrow();
            assertEquals(AgreementStatus.AWAITING_CONFIRMATION, stored.getStatus(), id);
            assertEquals(1L, stored.getRowVersionOrNull(), id);
            assertEquals(at, stored.getUpdatedAt(), id);
            AgreementVersion version = stored.getVersions().get(0);
            assertEquals(VersionStatus.OWNER_CONFIRMED, version.getStatus(), id);
            assertEquals(OwnerAnswer.confirmed(OWNER, AgreementStatement.VERSION, at), version.getOwnerAnswerOrNull());
            assertEquals(CONSENT, version.getPhotoConsent());
            assertEquals(TERMS, version.getTerms());
            assertEquals(FEE, version.getFee());
        }
        assertEquals(0, at.getNano() % 1_000);
        assertEquals(List.of(), notices.agreementPending());
        assertEquals(List.of(), trail.accesses());
        assertEquals(List.of(new RecordingAccountPasswordPort.Check(OWNER, false)), passwords.checks());
    }

    /** OC-5, US-2 AC-1: one not-found for every caller and id that is not the caller's sent version, before the password. */
    @Test
    void confirmByOwner_notBound_notFoundBeforePassword() {
        storeSent(AGREEMENT, "U1", clock().minus(1, ChronoUnit.HOURS));
        storeSent(AGREEMENT_2, "U1", null);
        accounts.answer("O2", true);
        List<Executable> notFound = List.of(
                () -> service().confirmByOwner(confirm(AGREEMENT), "O2"),
                () -> service().confirmByOwner(items(new AgreementVersionInput("a1", 1)), OWNER),
                () -> service().confirmByOwner(items(new AgreementVersionInput("01jagre0000000000000000001", 1)), OWNER),
                () -> service().confirmByOwner(confirm("01JAGRE0000000000000000009"), OWNER),
                () -> service().confirmByOwner(items(new AgreementVersionInput(AGREEMENT, 2)), OWNER),
                () -> service().confirmByOwner(confirm(AGREEMENT_2), OWNER),
                () -> service().confirmByOwner(confirm(AGREEMENT, "01JAGRE0000000000000000009"), OWNER));

        for (int index = 0; index < notFound.size(); index++) {
            assertThrows(BrokerageNotFoundException.class, notFound.get(index), "case " + index);
        }
        accounts.answer(OWNER, false);
        assertThrows(BrokerageNotFoundException.class, () -> service().confirmByOwner(confirm(AGREEMENT), OWNER));

        assertEquals(List.of(), passwords.checks());
        assertEquals(0, agreements.count("update"));
        assertEquals(List.of(), trail.events());
    }

    @Test
    void confirmByOwner_outdatedStatement_conflictBeforePassword() {
        storeSent(AGREEMENT, "U1", clock().minus(1, ChronoUnit.HOURS));

        AgreementRefusedException refused = assertThrows(AgreementRefusedException.class,
                () -> service().confirmByOwner(new AgreementConfirmCommand("secret", "old",
                        List.of(new AgreementVersionInput(AGREEMENT, 1))), OWNER));

        assertEquals(Reason.AGREEMENT_STATEMENT_OUTDATED, refused.reason());
        assertEquals(409, refused.reason().status());
        assertEquals("The confirmation text has changed. Read it again.", refused.getMessage());
        assertEquals(List.of(), refused.entries());
        assertEquals(List.of(), passwords.checks());
        assertEquals(0, agreements.count("update"));
    }

    /** OC-3: a wrong password answers on the field alone and writes nothing. */
    @Test
    void confirmByOwner_wrongPassword_validationOnCurrentPassword() {
        storeSent(AGREEMENT, "U1", clock().minus(1, ChronoUnit.HOURS));
        passwords.answer(PasswordCheck.Outcome.WRONG);

        AgreementRefusedException refused = assertThrows(AgreementRefusedException.class,
                () -> service().confirmByOwner(confirm(AGREEMENT), OWNER));

        assertEquals(Reason.VALIDATION_FAILED, refused.reason());
        assertEquals(List.of(new Entry("currentPassword", null)), refused.entries());
        assertEquals(List.of(new RecordingAccountPasswordPort.Check(OWNER, false)), passwords.checks());
        assertEquals(0, agreements.count("update"));
        assertEquals(List.of(), trail.events());
    }

    @Test
    void confirmByOwner_locked_accountLocked() {
        storeSent(AGREEMENT, "U1", clock().minus(1, ChronoUnit.HOURS));
        passwords.answer(PasswordCheck.Outcome.LOCKED);

        AgreementRefusedException refused = assertThrows(AgreementRefusedException.class,
                () -> service().confirmByOwner(confirm(AGREEMENT), OWNER));

        assertEquals(Reason.ACCOUNT_LOCKED, refused.reason());
        assertEquals(423, refused.reason().status());
        assertEquals("Too many wrong passwords. Try again later.", refused.getMessage());
        assertEquals(List.of(), refused.entries());
        assertEquals(0, agreements.count("update"));
        assertEquals(List.of(), trail.events());
    }

    /** E05-S5 spec S5-D4: the password runs outside any transaction, so a refused command keeps auth's count. */
    @Test
    void confirmByOwner_passwordCheckedOutsideTransaction() {
        storeSent(AGREEMENT, "U1", clock().minus(1, ChronoUnit.HOURS));
        NoDatabaseTransactions transactions = new NoDatabaseTransactions();
        AgreementCommandService service = new AgreementCommandService(leads, bases, accounts, agreements, trail,
                ids::next, notices, switches, passwords, new TransactionTemplate(transactions));

        service.confirmByOwner(confirm(AGREEMENT), OWNER);

        assertEquals(List.of(new RecordingAccountPasswordPort.Check(OWNER, false)), passwords.checks());
        assertEquals(2, transactions.begun);
    }

    /** OC-2, OC-6: a batch with one version past its deadline refuses on that item alone and writes nothing at all. */
    @Test
    void confirmByOwner_notAwaiting_entryPerFailingItem() {
        storeSent(AGREEMENT, "U1", clock().minus(1, ChronoUnit.HOURS));
        storeSent(AGREEMENT_2, "U1", clock().minus(Duration.ofDays(7)).minusSeconds(1));

        AgreementRefusedException refused = assertThrows(AgreementRefusedException.class,
                () -> service().confirmByOwner(confirm(AGREEMENT, AGREEMENT_2), OWNER));

        assertEquals(Reason.AGREEMENT_NOT_AWAITING_OWNER, refused.reason());
        assertEquals("This version is not waiting for your answer.", refused.getMessage());
        assertEquals(List.of(new Entry("versions[1]", null)), refused.entries());
        assertEquals("This version cannot be answered now.", refused.reason().entryMessageOrNull());
        assertEquals(0, agreements.count("update"));
        assertEquals(List.of(), trail.events());

        Instant answered = clock().minus(30, ChronoUnit.MINUTES);
        agreements.store(BrokerageAgreement.load(AGREEMENT, "U1", "P101", "P1", TransactionType.LONG_TERM_RENT, OWNER,
                LEAD, AgreementStatus.AWAITING_CONFIRMATION, "S9", answered, answered, 1L, List.of(AgreementVersion.load(
                        "V1", 1, VersionStatus.OWNER_CONFIRMED, new AgreementPeriod(START, START.plusMonths(6)), LEAD,
                        "I1", "S9", answered, answered, CONSENT, TERMS, FEE, clock().minus(1, ChronoUnit.HOURS),
                        OwnerAnswer.confirmed(OWNER, AgreementStatement.VERSION, answered)))));
        AgreementRefusedException answeredRefusal = assertThrows(AgreementRefusedException.class,
                () -> service().confirmByOwner(confirm(AGREEMENT), OWNER));
        assertEquals(List.of(new Entry("versions[0]", null)), answeredRefusal.entries());
        assertEquals(OwnerAnswer.confirmed(OWNER, AgreementStatement.VERSION, answered),
                agreements.findById(AGREEMENT).orElseThrow().getVersions().get(0).getOwnerAnswerOrNull());
    }

    /** OC-4, US-2 AC-5: the basis, the stops and the owner are checked again at confirmation, per item. */
    @Test
    void confirmByOwner_stopped_reasonGroupsPerItem() {
        storeSent(AGREEMENT, "U1", clock().minus(1, ChronoUnit.HOURS));
        bases.basis(LEAD, basis("SUSPENDED", new Unit("U1", "P101", List.of())));

        AgreementRefusedException refused = assertThrows(AgreementRefusedException.class,
                () -> service().confirmByOwner(confirm(AGREEMENT), OWNER));

        assertEquals(Reason.AGREEMENT_NOT_ELIGIBLE, refused.reason());
        assertEquals(List.of(new Entry("versions[0].reasonGroups.BASIS", null)), refused.entries());
        assertEquals(0, agreements.count("update"));
        assertEquals(List.of(), trail.events());

        bases.basis(LEAD, basis("USABLE", new Unit("U1", "P101", List.of("AVAILABILITY_DIFFERS"))));
        service().confirmByOwner(confirm(AGREEMENT), OWNER);
        assertEquals(VersionStatus.OWNER_CONFIRMED,
                agreements.findById(AGREEMENT).orElseThrow().getVersions().get(0).getStatus());
    }

    // ==================== E05-S5: the owner's rejection (spec 3.3, S5-D7, S5-D9) ====================

    /** RJ-1: declined with the trimmed reason, the agreement drafting again, no password asked. */
    @Test
    void rejectByOwner_answerable_rejectedDraftingNoPassword() {
        storeSent(AGREEMENT, "U1", clock().minus(1, ChronoUnit.HOURS));

        service().rejectByOwner(new AgreementRejectCommand(AGREEMENT, 1, "  The rent is too low.  "), OWNER);

        BrokerageAgreement stored = agreements.findById(AGREEMENT).orElseThrow();
        assertEquals(1, trail.events().size());
        RecordingAgreementTrail.Event event = trail.events().get(0);
        Instant at = event.at();
        assertEquals(new RecordingAgreementTrail.Event(AGREEMENT, 1, AgreementEventType.OWNER_REJECTED, OWNER, at), event);
        assertEquals(AgreementStatus.DRAFTING, stored.getStatus());
        assertEquals(1L, stored.getRowVersionOrNull());
        AgreementVersion version = stored.getVersions().get(0);
        assertEquals(VersionStatus.OWNER_REJECTED, version.getStatus());
        assertEquals(OwnerAnswer.rejected(OWNER, "The rent is too low.", at), version.getOwnerAnswerOrNull());
        assertEquals(TERMS, version.getTerms());
        assertEquals(List.of(), passwords.checks());
        assertEquals(List.of(), notices.agreementPending());
        assertEquals(List.of(), trail.accesses());

        storeSent(AGREEMENT_2, "U1", clock().minus(1, ChronoUnit.HOURS));
        service().rejectByOwner(new AgreementRejectCommand(AGREEMENT_2, 1, "   "), OWNER);
        assertEquals(null, agreements.findById(AGREEMENT_2).orElseThrow().getVersions().get(0).getOwnerAnswerOrNull()
                .rejectionReasonOrNull());
    }

    /** RJ-1, US-2 AC-1: another owner, a non-owner and every id that is not the caller's sent version get one not-found. */
    @Test
    void rejectByOwner_notBound_notFound() {
        storeSent(AGREEMENT, "U1", clock().minus(1, ChronoUnit.HOURS));
        storeSent(AGREEMENT_2, "U1", null);
        accounts.answer("O2", true);
        List<Executable> notFound = List.of(
                () -> service().rejectByOwner(new AgreementRejectCommand(AGREEMENT, 1, null), "O2"),
                () -> service().rejectByOwner(new AgreementRejectCommand("a1", 1, null), OWNER),
                () -> service().rejectByOwner(new AgreementRejectCommand("01JAGRE0000000000000000009", 1, null), OWNER),
                () -> service().rejectByOwner(new AgreementRejectCommand(AGREEMENT, 2, null), OWNER),
                () -> service().rejectByOwner(new AgreementRejectCommand(AGREEMENT_2, 1, null), OWNER));

        for (int index = 0; index < notFound.size(); index++) {
            assertThrows(BrokerageNotFoundException.class, notFound.get(index), "case " + index);
        }
        accounts.answer(OWNER, false);
        assertThrows(BrokerageNotFoundException.class,
                () -> service().rejectByOwner(new AgreementRejectCommand(AGREEMENT, 1, null), OWNER));

        assertEquals(0, agreements.count("update"));
        assertEquals(List.of(), trail.events());
    }

    @Test
    void rejectByOwner_lapsedOrAnswered_notAwaitingNoEntry() {
        storeSent(AGREEMENT, "U1", clock().minus(Duration.ofDays(7)).minusSeconds(1));
        Instant answered = clock().minus(30, ChronoUnit.MINUTES);
        agreements.store(BrokerageAgreement.load(AGREEMENT_2, "U1", "P101", "P1", TransactionType.LONG_TERM_RENT, OWNER,
                LEAD, AgreementStatus.AWAITING_CONFIRMATION, "S9", answered, answered, 1L, List.of(AgreementVersion.load(
                        "V2", 1, VersionStatus.OWNER_CONFIRMED, new AgreementPeriod(START, START.plusMonths(6)), LEAD,
                        "I1", "S9", answered, answered, CONSENT, TERMS, FEE, clock().minus(1, ChronoUnit.HOURS),
                        OwnerAnswer.confirmed(OWNER, AgreementStatement.VERSION, answered)))));

        for (String id : List.of(AGREEMENT, AGREEMENT_2)) {
            AgreementRefusedException refused = assertThrows(AgreementRefusedException.class,
                    () -> service().rejectByOwner(new AgreementRejectCommand(id, 1, "No"), OWNER), id);

            assertEquals(Reason.AGREEMENT_NOT_AWAITING_OWNER, refused.reason());
            assertEquals(List.of(), refused.entries());
        }
        assertEquals(0, agreements.count("update"));
        assertEquals(List.of(), trail.events());
    }

    /** spec 3.3, S5-D18: the reason's rule is part of the body's shape, so it answers first, for any agreement id. */
    @Test
    void rejectByOwner_badReasonOnAnyAgreement_validationFirst() {
        storeSent(AGREEMENT, "U1", clock().minus(1, ChronoUnit.HOURS));

        for (String id : List.of(AGREEMENT, "01JAGRE0000000000000000009")) {
            AgreementContentException refused = assertThrows(AgreementContentException.class,
                    () -> service().rejectByOwner(new AgreementRejectCommand(id, 1, "Too‮low"), OWNER), id);

            assertEquals("reason", refused.field());
        }
        assertEquals(List.of(), agreements.calls());
        assertEquals(List.of(), calls);
        assertEquals(List.of(), trail.events());
    }

    /** The command confirming version 1 of each agreement, with the current statement. */
    private static AgreementConfirmCommand confirm(String... agreementIds) {
        return items(java.util.Arrays.stream(agreementIds).map(id -> new AgreementVersionInput(id, 1))
                .toArray(AgreementVersionInput[]::new));
    }

    private static AgreementConfirmCommand items(AgreementVersionInput... versions) {
        return new AgreementConfirmCommand("secret", AgreementStatement.VERSION, List.of(versions));
    }

    /**
     * Stores an agreement of {@link #OWNER} on the unit, AWAITING_CONFIRMATION, its version 1 complete, AWAITING_OWNER and
     * sent at {@code sentAtOrNull} (never sent when null), row version 0.
     */
    private BrokerageAgreement storeSent(String agreementId, String unitId, Instant sentAtOrNull) {
        Instant at = (sentAtOrNull == null ? clock() : sentAtOrNull).minus(1, ChronoUnit.HOURS);
        Instant updated = sentAtOrNull == null ? at : sentAtOrNull;
        BrokerageAgreement agreement = BrokerageAgreement.load(agreementId, unitId, "P101", "P1",
                TransactionType.LONG_TERM_RENT, OWNER, LEAD, AgreementStatus.AWAITING_CONFIRMATION, "S9", at, updated, 0L,
                List.of(AgreementVersion.load("V" + agreementId, 1, VersionStatus.AWAITING_OWNER,
                        new AgreementPeriod(START, START.plusMonths(6)), LEAD, "I1", "S9", at, updated, CONSENT, TERMS,
                        FEE, sentAtOrNull)));
        agreements.store(agreement);
        return agreement;
    }

    /** The service's clock, as it reads it (E05-S5 spec S5-D19). */
    private static Instant clock() {
        return AppClock.currentUTC().truncatedTo(ChronoUnit.MICROS);
    }

    private enum BrokerageFeeOf { COMPLETE, WITHOUT_DUE_DAYS }

    /** Stores {@link #AGREEMENT} DRAFTING on the unit, its version 1 a DRAFT with every part of the content. */
    private BrokerageAgreement storeComplete(String unitId, BrokerageFeeOf fee) {
        Instant at = Instant.parse("2026-11-01T00:00:00Z");
        BrokerageFee policy = fee == BrokerageFeeOf.COMPLETE ? FEE : new BrokerageFee(FEE.formulaOrNull(), null,
                FEE.renewalOrNull(), FEE.earlyLeaseEndOrNull(), FEE.protectionDaysOrNull());
        BrokerageAgreement agreement = BrokerageAgreement.load(AGREEMENT, unitId, "P101", "P1",
                TransactionType.LONG_TERM_RENT, OWNER, LEAD, AgreementStatus.DRAFTING, "S9", at, at, 0L,
                List.of(AgreementVersion.load("V1", 1, VersionStatus.DRAFT, new AgreementPeriod(START, START.plusMonths(6)),
                        LEAD, "I1", "S9", at, at, CONSENT, TERMS, policy, null)));
        agreements.store(agreement);
        return agreement;
    }

    private AgreementRefusedException sendRefused(AgreementSendCommand command, StaffViewer viewer) {
        return assertThrows(AgreementRefusedException.class, () -> service().sendDraft(command, viewer));
    }

    private List<String> sendRefusedFields() {
        AgreementRefusedException refused = sendRefused(new AgreementSendCommand(AGREEMENT, 0L), VIEWER);
        assertEquals(Reason.AGREEMENT_NOT_ELIGIBLE, refused.reason());
        assertTrue(refused.entries().stream().allMatch(entry -> entry.allowedValuesOrNull() == null));
        return refused.entries().stream().map(Entry::field).toList();
    }

    /** A refused send writes nothing: no update, no history row, no notice. */
    private void assertNothingSent() {
        assertEquals(0, agreements.count("update"));
        assertEquals(List.of(), trail.events());
        assertEquals(List.of(), notices.agreementPending());
    }

    private List<String> refusedDraftFields(AgreementCommandService service) {
        AgreementRefusedException refused = assertThrows(AgreementRefusedException.class,
                () -> service.editDraft(draftCommand(0L), VIEWER));
        assertEquals(Reason.AGREEMENT_NOT_ELIGIBLE, refused.reason());
        assertTrue(refused.entries().stream().allMatch(entry -> entry.allowedValuesOrNull() == null));
        return refused.entries().stream().map(Entry::field).toList();
    }

    private static AgreementDraftCommand draftCommand(long expectedDataVersion) {
        return new AgreementDraftCommand(AGREEMENT, expectedDataVersion, "LONG_TERM_RENT", START, END, CONSENT, TERMS,
                BrokerageFee.NONE);
    }

    /** Stores agreement {@link #AGREEMENT} on the unit, its version 1 on basis L1 / I1, owner O, row version 0. */
    private BrokerageAgreement storeDraft(String unitId, AgreementStatus status, VersionStatus versionStatus) {
        Instant at = Instant.parse("2026-11-01T00:00:00Z");
        BrokerageAgreement agreement = BrokerageAgreement.load(AGREEMENT, unitId, "P101", "P1",
                TransactionType.LONG_TERM_RENT, OWNER, LEAD, status, "S9", at, at, 0L,
                List.of(AgreementVersion.load("V1", 1, versionStatus, new AgreementPeriod(START, START.plusMonths(6)),
                        LEAD, "I1", "S9", at, at)));
        agreements.store(agreement);
        return agreement;
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
