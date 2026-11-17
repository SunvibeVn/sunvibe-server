package vn.sunvibe.brokerage.core.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.sunvibe.brokerage.core.application.command.AgreementConfirmCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementCreateCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementDraftCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementOpenDraftCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementRejectCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementSendCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementVersionInput;
import vn.sunvibe.brokerage.core.application.exception.AgreementRefusedException;
import vn.sunvibe.brokerage.core.application.exception.BrokerageNotFoundException;
import vn.sunvibe.brokerage.core.application.shared.AgreementSwitches;
import vn.sunvibe.brokerage.core.application.shared.external.account.AccountPasswordPort;
import vn.sunvibe.brokerage.core.application.shared.external.account.OwnerAccountPort;
import vn.sunvibe.brokerage.core.application.shared.external.account.OwnerNoticePort;
import vn.sunvibe.brokerage.core.application.shared.external.account.PasswordCheck;
import vn.sunvibe.brokerage.core.application.shared.external.lead.BasisLeadFacts;
import vn.sunvibe.brokerage.core.application.shared.external.lead.BasisLeadPort;
import vn.sunvibe.brokerage.core.application.shared.external.property.IntakeBasisFacts;
import vn.sunvibe.brokerage.core.application.shared.external.property.IntakeBasisPort;
import vn.sunvibe.brokerage.core.application.usecase.AgreementCommandUseCase;
import vn.sunvibe.brokerage.core.application.usecase.AgreementCreateResult;
import vn.sunvibe.brokerage.core.application.usecase.StaffViewer;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementEventType;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementPeriod;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementStatement;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementStatus;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementVersion;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageAgreement;
import vn.sunvibe.brokerage.core.domain.model.agreement.OwnerAnswer;
import vn.sunvibe.brokerage.core.domain.model.agreement.TransactionType;
import vn.sunvibe.brokerage.core.domain.model.eligibility.ReasonGroup;
import vn.sunvibe.brokerage.core.domain.policy.agreement.VersionSendRules;
import vn.sunvibe.brokerage.core.domain.policy.eligibility.EligibilityPolicy;
import vn.sunvibe.brokerage.core.domain.repository.AgreementDomainRepository;
import vn.sunvibe.brokerage.core.domain.repository.AgreementTrailDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.time.AppClock;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Use Case Implementation - the commands of a brokerage agreement (E05-S2 spec 3.1, 5.2). The create is one read-write
 * transaction that every port joins: one locked lead read, one basis read (the readiness of the lead's latest completed
 * intake), at most one owner standing read, one unfinished-agreement lookup, then three inserts (the agreement, its
 * version 1 and one history row). It writes nothing else: no access log, no lead change, no row of another module
 * (brokerage-agreement BR-18).
 * <p>
 * Lock order of the agreement commands: the basis lead's row first (here, through the port's FOR NO KEY UPDATE), then an
 * agreement's row; no agreement command locks a lead row while it holds an agreement row (spec S2-D4).
 */
@Service
public class AgreementCommandService implements AgreementCommandUseCase {

    private static final List<String> TRANSACTION_TYPES = Arrays.stream(TransactionType.values()).map(Enum::name)
            .toList();
    private static final String PASSWORD_FIELD = "currentPassword";

    private final BasisLeadPort leads;
    private final IntakeBasisPort bases;
    private final OwnerAccountPort accounts;
    private final AgreementDomainRepository agreements;
    private final AgreementTrailDomainRepository trail;
    private final IdGenerator<String> ids;
    private final OwnerNoticePort notices;
    private final AgreementSwitches switches;
    private final AccountPasswordPort passwords;
    /** The owner's writes, and each agreement of the lapse job, in a transaction of their own (plan E05S5-P4). */
    private final TransactionTemplate transactions;
    /** The owner's bound check, read-only, over the same transaction manager. */
    private final TransactionTemplate reads;

    public AgreementCommandService(BasisLeadPort leads, IntakeBasisPort bases, OwnerAccountPort accounts,
                                   AgreementDomainRepository agreements, AgreementTrailDomainRepository trail,
                                   IdGenerator<String> ids, OwnerNoticePort notices, AgreementSwitches switches,
                                   AccountPasswordPort passwords, TransactionTemplate transactions) {
        this.leads = leads;
        this.bases = bases;
        this.accounts = accounts;
        this.agreements = agreements;
        this.trail = trail;
        this.ids = ids;
        this.notices = notices;
        this.switches = switches;
        this.passwords = passwords;
        this.transactions = transactions;
        this.reads = new TransactionTemplate(transactions.getTransactionManager());
        this.reads.setReadOnly(true);
    }

    @Override
    @Transactional
    public AgreementCreateResult create(AgreementCreateCommand command, StaffViewer actor) {
        // brokerage-agreement BR-1: only long-term rental now; any other type is a request error, before any read.
        TransactionType type = TransactionType.of(command.transactionType())
                .orElseThrow(() -> AgreementRefusedException.invalid("transactionType", TRANSACTION_TYPES));
        // brokerage-agreement BR-6: both dates, the end at most 12 months after the start; checked before any read.
        AgreementPeriod period = new AgreementPeriod(command.startDate(), command.endDate());
        // The lead row stays locked to the commit, so a reassignment, a hold or a closing waits for this command or is
        // seen by it (spec S2-D2). A lead the actor may not see answers as an unknown one.
        BasisLeadFacts lead = leads.findVisibleForUpdate(command.leadId(), actor.userId(), actor.expandedAuthorities())
                .orElseThrow(BrokerageNotFoundException::new);
        // brokerage-agreement BR-16: only the lead's current assignee holding SALE or SALE_LEADER drafts; a manager
        // role alone does not draft for them.
        if (!EligibilityPolicy.assignedSale(lead.assignedUserIdOrNull(), actor.userId(), actor.expandedAuthorities())) {
            throw AgreementRefusedException.notAssignee();
        }
        // brokerage-agreement BR-2: an agreement rests on the lead's latest completed intake; without one, BASIS only.
        IntakeBasisFacts basis = bases.latestBasis(lead.leadId())
                .orElseThrow(() -> AgreementRefusedException.notEligible(List.of(ReasonGroup.BASIS)));
        // brokerage-agreement BR-2 to BR-4: the E05-S1 check of the unit reruns here; the command does not trust an
        // earlier eligibility read.
        List<ReasonGroup> groups = reasonGroups(command.unitId(), basis);
        if (!groups.isEmpty()) {
            throw AgreementRefusedException.notEligible(groups);
        }
        // brokerage-agreement BR-1: one unfinished agreement per unit and type. A concurrent create that passes this
        // lookup meets the database's partial unique index at the insert instead.
        if (!agreements.unfinishedOf(List.of(command.unitId()), type).isEmpty()) {
            throw AgreementRefusedException.exists();
        }
        Instant now = AppClock.currentUTC().truncatedTo(ChronoUnit.MICROS);
        String unitCodeOrNull = basis.units().stream().filter(unit -> unit.unitId().equals(command.unitId()))
                .findFirst().map(IntakeBasisFacts.Unit::unitCodeOrNull).orElse(null);
        BrokerageAgreement agreement = BrokerageAgreement.create(ids.generateId(), command.unitId(), unitCodeOrNull,
                basis.propertyId(), type, basis.declaredOwnerIdOrNull(), lead.leadId(), basis.intakeId(),
                ids.generateId(), period, actor.userId(), now);
        agreements.create(agreement);
        trail.recordEvent(agreement.getId(), 1, AgreementEventType.CREATED, actor.userId(), now);
        return new AgreementCreateResult(agreement.getId());
    }

    /**
     * Statements: one agreement read with its versions and their fee rows, one locked lead read, one basis read (the
     * readiness of the lead's latest completed intake), at most one owner standing read, then the guarded update of the
     * agreement row with the draft's row and fee rows, and one history insert. It writes nothing else: no access log,
     * no lead change, no row of another module and no outbox event (brokerage-agreement BR-18; E05-S3 spec S3-D15).
     * The agreement is read before the lead's lock, since it names the lead; a change committed in between fails the
     * guarded update (E05-S3 spec S3-D3, S3-D4).
     */
    @Override
    @Transactional
    public void editDraft(AgreementDraftCommand command, StaffViewer actor) {
        // brokerage-agreement BR-1: only long-term rental; the type of an agreement cannot change while one type exists.
        TransactionType.of(command.transactionType())
                .orElseThrow(() -> AgreementRefusedException.invalid("transactionType", TRANSACTION_TYPES));
        // brokerage-agreement BR-6: both dates, the end at most 12 months after the start; checked before any read.
        AgreementPeriod period = new AgreementPeriod(command.startDate(), command.endDate());
        if (!AgreementQueryService.AGREEMENT_ID.matcher(command.agreementId()).matches()) {
            throw new BrokerageNotFoundException();
        }
        BrokerageAgreement agreement = agreements.findById(command.agreementId())
                .orElseThrow(BrokerageNotFoundException::new);
        // The basis lead's row stays locked to the commit (lock order of spec S2-D4: the lead, then the agreement's row
        // through the guarded update). An agreement whose lead the actor may not see answers as an unknown one.
        BasisLeadFacts lead = leads.findVisibleForUpdate(agreement.getBasisLeadId(), actor.userId(),
                actor.expandedAuthorities()).orElseThrow(BrokerageNotFoundException::new);
        // brokerage-agreement BR-16 and E-10: only the lead's current assignee holding SALE or SALE_LEADER drafts.
        if (!EligibilityPolicy.assignedSale(lead.assignedUserIdOrNull(), actor.userId(), actor.expandedAuthorities())) {
            throw AgreementRefusedException.notAssignee();
        }
        AgreementVersion draft = agreement.draft().orElseThrow(AgreementRefusedException::notDraft);
        // brokerage-agreement BR-5: the change checks the data version the caller read.
        if (!command.expectedDataVersion().equals(agreement.getRowVersionOrNull())) {
            throw AgreementRefusedException.stale();
        }
        List<ReasonGroup> groups = draftGroups(agreement, draft, lead.leadId());
        if (!groups.isEmpty()) {
            throw AgreementRefusedException.notEligible(groups);
        }
        Instant now = AppClock.currentUTC().truncatedTo(ChronoUnit.MICROS);
        agreements.update(agreement.editDraft(period, command.photoConsent(), command.terms(), command.fee(), now));
        trail.recordEvent(agreement.getId(), draft.getVersionNo(), AgreementEventType.DRAFT_EDITED, actor.userId(), now);
    }

    /**
     * Statements (E05-S4 spec 5.2): one agreement read with its versions, fee rows and tier rows, one locked lead read,
     * one basis read, at most one owner standing read, then the guarded update of the agreement row and the draft's row,
     * one history insert, and auth's notice (one user read and one notice insert, in this transaction). It appends no
     * outbox event, writes no access log and changes no lead, intake or ownership row (brokerage-agreement BR-18; spec
     * S4-D9). The agreement is read before the lead's lock, since it names the lead; a change committed in between fails
     * the guarded update, which answers 409 with nothing written. Two sends, or a send and an edit, of one agreement
     * serialize on the lead's row lock.
     */
    @Override
    @Transactional
    public void sendDraft(AgreementSendCommand command, StaffViewer actor) {
        if (!AgreementQueryService.AGREEMENT_ID.matcher(command.agreementId()).matches()) {
            throw new BrokerageNotFoundException();
        }
        BrokerageAgreement agreement = agreements.findById(command.agreementId())
                .orElseThrow(BrokerageNotFoundException::new);
        // The basis lead's row stays locked to the commit (lock order of E05-S2 spec S2-D4: the lead, then the
        // agreement's row through the guarded update). An agreement whose lead the actor may not see answers as an
        // unknown one.
        BasisLeadFacts lead = leads.findVisibleForUpdate(agreement.getBasisLeadId(), actor.userId(),
                actor.expandedAuthorities()).orElseThrow(BrokerageNotFoundException::new);
        // brokerage-agreement BR-16: only the lead's current assignee holding SALE or SALE_LEADER sends.
        if (!EligibilityPolicy.assignedSale(lead.assignedUserIdOrNull(), actor.userId(), actor.expandedAuthorities())) {
            throw AgreementRefusedException.notAssignee();
        }
        AgreementVersion draft = agreement.draft().orElseThrow(AgreementRefusedException::notDraft);
        // brokerage-agreement BR-5: the Sale sends exactly the version they read.
        if (!command.expectedDataVersion().equals(agreement.getRowVersionOrNull())) {
            throw AgreementRefusedException.stale();
        }
        // brokerage-agreement BR-8 and E-13: no part of a version sent to the owner is unknown.
        List<String> missing = VersionSendRules.missingParts(draft);
        if (!missing.isEmpty()) {
            throw AgreementRefusedException.incomplete(missing);
        }
        // brokerage-agreement E-2: nothing is sent on a basis that no longer allows an agreement.
        List<ReasonGroup> groups = draftGroups(agreement, draft, lead.leadId());
        if (!groups.isEmpty()) {
            throw AgreementRefusedException.notEligible(groups);
        }
        // brokerage-agreement BR-19: no real owner is reached while the server keeps sending closed; checked last, so
        // every other answer still tells the Sale what to fix.
        if (!switches.ownerSendingEnabled()) {
            throw AgreementRefusedException.sendingNotAvailable();
        }
        Instant now = AppClock.currentUTC().truncatedTo(ChronoUnit.MICROS);
        agreements.update(agreement.sendDraft(now));
        trail.recordEvent(agreement.getId(), draft.getVersionNo(), AgreementEventType.VERSION_SENT, actor.userId(), now);
        // brokerage-agreement BR-10 and BR-17: the owner party's account gets a neutral notice by user id.
        notices.agreementPending(agreement.getOwnerUserId());
    }

    /**
     * Statements (E05-S5 spec S5-D6, plan E05S5-P4): in a read-only transaction, auth's owner standing and one agreement
     * read per item; outside any transaction, auth's password check in its own transaction; then in one transaction one
     * agreement read per item, the basis and owner reads of the E05-S1 check per item, and per item the guarded update
     * of the agreement row with its version row and one history insert. No lead row is locked: the owner's commands
     * write only the agreement rows, through the guarded update, so they never wait on a staff command in the opposite
     * order. No notice, no access row, no lead change (brokerage-agreement BR-17, BR-18).
     */
    @Override
    public void confirmByOwner(AgreementConfirmCommand command, String ownerUserId) {
        // brokerage-agreement US-2 AC-1: every other caller and id gets one not-found, before the password is counted.
        reads.executeWithoutResult(status -> command.versions()
                .forEach(item -> boundVersion(ownerUserId, item.agreementId(), item.versionNo())));
        // brokerage-agreement BR-10: the owner confirms the statement the server holds now.
        if (!AgreementStatement.VERSION.equals(command.statementVersion())) {
            throw AgreementRefusedException.statementOutdated();
        }
        // brokerage-agreement BR-10: the current password is re-entered; outside any transaction of this command, so a
        // refused confirmation keeps auth's count (E05-S5 spec S5-D4).
        PasswordCheck check = passwords.check(ownerUserId, command.currentPassword());
        if (check.outcome() == PasswordCheck.Outcome.WRONG) {
            throw AgreementRefusedException.invalid(PASSWORD_FIELD, null);
        }
        if (check.outcome() == PasswordCheck.Outcome.LOCKED) {
            throw AgreementRefusedException.accountLocked();
        }
        transactions.executeWithoutResult(status -> confirmItems(command.versions(), ownerUserId));
    }

    /**
     * Steps 5 to 7 of E05-S5 spec S5-D6 with one {@code now}: the items in agreement id order, so two batches over the
     * same agreements write their rows in one order; every refusal comes before the first write.
     */
    private void confirmItems(List<AgreementVersionInput> items, String ownerUserId) {
        Instant now = AppClock.currentUTC().truncatedTo(ChronoUnit.MICROS);
        List<Integer> order = IntStream.range(0, items.size()).boxed()
                .sorted(Comparator.comparing(index -> items.get(index).agreementId())).toList();
        Map<Integer, BrokerageAgreement> read = new LinkedHashMap<>();
        List<Integer> notAwaiting = new ArrayList<>();
        for (int index : order) {
            AgreementVersionInput item = items.get(index);
            BrokerageAgreement agreement = agreements.findById(item.agreementId())
                    .orElseThrow(BrokerageNotFoundException::new);
            read.put(index, agreement);
            // brokerage-agreement BR-5, BR-10: only the version waiting for the owner, within its 7 days.
            if (versionOf(agreement, item.versionNo()).filter(version -> version.answerableAt(now)).isEmpty()) {
                notAwaiting.add(index);
            }
        }
        if (!notAwaiting.isEmpty()) {
            throw AgreementRefusedException.notAwaitingOwner(notAwaiting);
        }
        // brokerage-agreement US-2 AC-5, E-2: the basis, the BR-3 stops and the owner are checked again for each item.
        Map<Integer, List<ReasonGroup>> stopped = new LinkedHashMap<>();
        read.forEach((index, agreement) -> {
            AgreementVersion version = versionOf(agreement, items.get(index).versionNo()).orElseThrow();
            List<ReasonGroup> groups = draftGroups(agreement, version, version.getBasisLeadId());
            if (!groups.isEmpty()) {
                stopped.put(index, groups);
            }
        });
        if (!stopped.isEmpty()) {
            throw AgreementRefusedException.notEligibleItems(stopped);
        }
        // brokerage-agreement US-2 AC-4: each version is recorded on its own, with its own history row.
        read.forEach((index, agreement) -> {
            int versionNo = items.get(index).versionNo();
            agreements.update(agreement.confirmByOwner(versionNo, ownerUserId, AgreementStatement.VERSION, now));
            trail.recordEvent(agreement.getId(), versionNo, AgreementEventType.OWNER_CONFIRMED, ownerUserId, now);
        });
    }

    /**
     * Statements (E05-S5 spec S5-D7): auth's owner standing, one agreement read, then the guarded update of the agreement
     * row with its version row and one history insert. No password (US-2 AC-3 asks none for a rejection), no lead lock,
     * no E05-S1 recheck (refusing a "no" would keep the version pending for nothing), no notice.
     */
    @Override
    @Transactional
    public void rejectByOwner(AgreementRejectCommand command, String ownerUserId) {
        Instant now = AppClock.currentUTC().truncatedTo(ChronoUnit.MICROS);
        // brokerage-agreement BR-17 (E05-S5 spec 3.3): the reason's rule is part of the body's shape, so it answers first,
        // the same for every caller and id.
        OwnerAnswer.rejected(ownerUserId, command.reasonOrNull(), now);
        BrokerageAgreement agreement = boundVersion(ownerUserId, command.agreementId(), command.versionNo());
        // brokerage-agreement BR-5, BR-10: only the version waiting for the owner, within its 7 days, is declined.
        if (versionOf(agreement, command.versionNo()).filter(version -> version.answerableAt(now)).isEmpty()) {
            throw AgreementRefusedException.notAwaitingOwner(List.of());
        }
        // brokerage-agreement US-2 AC-3, E-6: the agreement is drafting again and the Sale may open a new draft.
        agreements.update(agreement.rejectByOwner(command.versionNo(), ownerUserId, command.reasonOrNull(), now));
        trail.recordEvent(agreement.getId(), command.versionNo(), AgreementEventType.OWNER_REJECTED, ownerUserId, now);
    }

    /**
     * Statements (E05-S5 spec S5-D10): one agreement read, one locked lead read (held to the end, lock order of E05-S2
     * spec S2-D4), the basis and owner reads of the E05-S1 check; when a lapse is due and not stored yet, the guarded
     * update that stores it with its history row and a second agreement read; then the guarded update that adds the new
     * draft version and one history row.
     */
    @Override
    @Transactional
    public void openDraft(AgreementOpenDraftCommand command, StaffViewer actor) {
        if (!AgreementQueryService.AGREEMENT_ID.matcher(command.agreementId()).matches()) {
            throw new BrokerageNotFoundException();
        }
        BrokerageAgreement agreement = agreements.findById(command.agreementId())
                .orElseThrow(BrokerageNotFoundException::new);
        BasisLeadFacts lead = leads.findVisibleForUpdate(agreement.getBasisLeadId(), actor.userId(),
                actor.expandedAuthorities()).orElseThrow(BrokerageNotFoundException::new);
        // brokerage-agreement BR-16 and E-10: only the lead's current assignee holding SALE or SALE_LEADER drafts.
        if (!EligibilityPolicy.assignedSale(lead.assignedUserIdOrNull(), actor.userId(), actor.expandedAuthorities())) {
            throw AgreementRefusedException.notAssignee();
        }
        Instant now = AppClock.currentUTC().truncatedTo(ChronoUnit.MICROS);
        // brokerage-agreement BR-5: one pending version at a time; a version still waiting for the owner or for
        // activation, or an agreement in effect, keeps the slot (a new version of an agreement in effect is E05-S10's).
        BrokerageAgreement effective = agreement.withDueLapse(now);
        if (effective.getStatus() != AgreementStatus.DRAFTING
                || effective.getVersions().stream().anyMatch(version -> version.getStatus().isPending())) {
            throw AgreementRefusedException.newDraftNotAllowed();
        }
        // brokerage-agreement BR-5: the change checks the data version the caller read.
        if (!command.expectedDataVersion().equals(agreement.getRowVersionOrNull())) {
            throw AgreementRefusedException.stale();
        }
        // brokerage-agreement BR-2 to BR-4: the E05-S1 check on the latest version's basis (a newer completed intake of
        // the lead answers BASIS: a version on another basis is E05-S10's).
        AgreementVersion latest = effective.getVersions().getLast();
        List<ReasonGroup> groups = draftGroups(effective, latest, lead.leadId());
        if (!groups.isEmpty()) {
            throw AgreementRefusedException.notEligible(groups);
        }
        AgreementVersion due = agreement.dueLapseOrNull(now);
        BrokerageAgreement drafting = agreement;
        if (due != null) {
            // E05-S5 spec S5-D8: the job's backstop. The lapse is stored first, in its own flush, and the agreement read
            // again: one flush would insert the new DRAFT row before updating the old one and meet
            // uq_brokerage_agreement_versions_pending, which is not deferrable.
            agreements.update(effective);
            trail.recordEvent(agreement.getId(), due.getVersionNo(), AgreementEventType.VERSION_LAPSED, null,
                    due.answerByOrNull());
            drafting = agreements.findById(agreement.getId()).orElseThrow(BrokerageNotFoundException::new);
        }
        BrokerageAgreement opened = drafting.openDraft(ids.generateId(), actor.userId(), now);
        agreements.update(opened);
        trail.recordEvent(agreement.getId(), opened.getVersions().getLast().getVersionNo(),
                AgreementEventType.DRAFT_OPENED, actor.userId(), now);
    }

    /**
     * brokerage-agreement US-2 AC-1 (E05-S5 spec S5-D6 step 2, S5-D18): the caller is an eligible owner, the id is well
     * formed, the agreement exists, is the caller's, and holds that version sent to them; else one not-found for every
     * case, which reads the agreement row by id for every caller.
     */
    private BrokerageAgreement boundVersion(String ownerUserId, String agreementId, int versionNo) {
        if (!accounts.eligibleOwner(ownerUserId) || agreementId == null
                || !AgreementQueryService.AGREEMENT_ID.matcher(agreementId).matches()) {
            throw new BrokerageNotFoundException();
        }
        BrokerageAgreement agreement = agreements.findById(agreementId).orElseThrow(BrokerageNotFoundException::new);
        if (!agreement.getOwnerUserId().equals(ownerUserId) || versionOf(agreement, versionNo)
                .filter(version -> version.getSentAtOrNull() != null).isEmpty()) {
            throw new BrokerageNotFoundException();
        }
        return agreement;
    }

    private static Optional<AgreementVersion> versionOf(BrokerageAgreement agreement, int versionNo) {
        return agreement.getVersions().stream().filter(version -> version.getVersionNo() == versionNo).findFirst();
    }

    /**
     * The E05-S1 check rerun on the version's own basis (E05-S3 spec S3-D3 step 7; E05-S5 spec S5-D6 step 6), the groups
     * in their order: BASIS alone when the lead's latest completed intake is not the version's (brokerage-agreement BR-2:
     * a version rests on one basis, so no owner is read); else the groups the create computes, plus OWNER when the
     * declared owner of that basis is no longer the agreement's owner party (BR-1, BR-4).
     */
    private List<ReasonGroup> draftGroups(BrokerageAgreement agreement, AgreementVersion draft, String basisLeadId) {
        IntakeBasisFacts basis = bases.latestBasis(basisLeadId).orElse(null);
        if (basis == null || !basis.intakeId().equals(draft.getBasisIntakeId())) {
            return List.of(ReasonGroup.BASIS);
        }
        Set<ReasonGroup> groups = EnumSet.noneOf(ReasonGroup.class);
        groups.addAll(reasonGroups(agreement.getUnitId(), basis));
        if (!agreement.getOwnerUserId().equals(basis.declaredOwnerIdOrNull())) {
            groups.add(ReasonGroup.OWNER);
        }
        return List.copyOf(groups);
    }

    /** The reason groups of the unit on this basis, as the eligibility read computes them. */
    private List<ReasonGroup> reasonGroups(String unitId, IntakeBasisFacts basis) {
        String declared = basis.declaredOwnerIdOrNull();
        boolean accountEligible = declared != null && accounts.eligibleOwner(declared);
        EligibilityPolicy.Basis policyBasis = new EligibilityPolicy.Basis(basis.state(), basis.units().stream()
                .map(unit -> new EligibilityPolicy.BasisUnit(unit.unitId(), unit.reasons())).toList());
        EligibilityPolicy.Owner owner = new EligibilityPolicy.Owner(declared, basis.holdings().stream()
                .map(holding -> new EligibilityPolicy.Holding(holding.userId(), holding.relation(), holding.verification()))
                .toList(), accountEligible);
        return EligibilityPolicy.reasonGroups(unitId, policyBasis, owner);
    }
}
