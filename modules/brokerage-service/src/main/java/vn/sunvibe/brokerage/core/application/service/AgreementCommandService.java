package vn.sunvibe.brokerage.core.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.sunvibe.brokerage.core.application.command.AgreementActivateCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementCancelCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementConfirmCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementCreateCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementDraftCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementOpenDraftCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementRefuseActivationCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementRejectCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementReleaseSuspensionCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementSendCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementSuspendCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementTerminateCommand;
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
import vn.sunvibe.brokerage.core.domain.model.agreement.ActivationDecision;
import vn.sunvibe.brokerage.core.domain.model.agreement.ActivationStatement;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementEventType;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementPeriod;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementStatement;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementStatus;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementSuspension;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementTermination;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementVersion;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageAgreement;
import vn.sunvibe.brokerage.core.domain.model.agreement.DueTransition;
import vn.sunvibe.brokerage.core.domain.model.agreement.OwnerAnswer;
import vn.sunvibe.brokerage.core.domain.model.agreement.SignedDocument;
import vn.sunvibe.brokerage.core.domain.model.agreement.StopAction;
import vn.sunvibe.brokerage.core.domain.model.agreement.TransactionType;
import vn.sunvibe.brokerage.core.domain.model.eligibility.ReasonGroup;
import vn.sunvibe.brokerage.core.domain.policy.agreement.AgreementStopPolicy;
import vn.sunvibe.brokerage.core.domain.policy.agreement.VersionSendRules;
import vn.sunvibe.brokerage.core.domain.policy.eligibility.EligibilityPolicy;
import vn.sunvibe.brokerage.core.domain.repository.AgreementDomainRepository;
import vn.sunvibe.brokerage.core.domain.repository.AgreementSuspensionDomainRepository;
import vn.sunvibe.brokerage.core.domain.repository.AgreementTrailDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.outbox.EventOutboxPort;
import vn.sunvibe.shared.outbox.OutboxEvent;
import vn.sunvibe.shared.outbox.UnitAdvertisingChangedEvent;
import vn.sunvibe.shared.time.AppClock;

import java.time.Duration;
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
import java.util.function.Function;
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

    private static final Logger log = LoggerFactory.getLogger(AgreementCommandService.class);

    /**
     * E05-S6 spec S6-D12: a due event of an activation is appended only when it falls at or before the command's now plus
     * this horizon, one day inside the outbox adapter's 800 days, which reads the clock after the command did; a later
     * one is left out, then and later, and the listing job is its only signal (a named narrowing of ADR-012 decision 6).
     */
    static final Duration OUTBOX_DUE_HORIZON = Duration.ofDays(799);

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
    /** E05's outbox events of an activation, in the command's transaction (ADR-014 decision 2; spec S6-D12). */
    private final EventOutboxPort outbox;
    /** The suspension records beside the aggregate, in the agreement's transaction (E05-S8 spec S8-D6). */
    private final AgreementSuspensionDomainRepository suspensions;

    public AgreementCommandService(BasisLeadPort leads, IntakeBasisPort bases, OwnerAccountPort accounts,
                                   AgreementDomainRepository agreements, AgreementTrailDomainRepository trail,
                                   IdGenerator<String> ids, OwnerNoticePort notices, AgreementSwitches switches,
                                   AccountPasswordPort passwords, TransactionTemplate transactions,
                                   EventOutboxPort outbox, AgreementSuspensionDomainRepository suspensions) {
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
        this.outbox = outbox;
        this.suspensions = suspensions;
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
            // E05-S6 spec S6-D5: the row is dated by the deadline of the lapse that fell due (7 or 14 days)
            trail.recordEvent(agreement.getId(), due.getVersionNo(), AgreementEventType.VERSION_LAPSED, null,
                    due.lapseDeadlineOrNull());
            drafting = agreements.findById(agreement.getId()).orElseThrow(BrokerageNotFoundException::new);
        }
        BrokerageAgreement opened = drafting.openDraft(ids.generateId(), actor.userId(), now);
        agreements.update(opened);
        trail.recordEvent(agreement.getId(), opened.getVersions().getLast().getVersionNo(),
                AgreementEventType.DRAFT_OPENED, actor.userId(), now);
    }

    /**
     * Statements (E05-S6 spec S6-D1, plan E05S6-P4): one agreement read with its versions and their fee and tier rows,
     * one locked lead read, one read of the version's authors, the basis and owner reads of the E05-S1 check; then the
     * guarded update of the agreement row with the version's row, one history insert, the outbox appends and auth's
     * notice, in this order, all in this transaction. The update comes first, so a lost race writes nothing at all. No
     * access row, no lead, intake or ownership change (brokerage-agreement BR-17, BR-18).
     */
    @Override
    @Transactional
    public void activate(AgreementActivateCommand command, StaffViewer actor) {
        Instant now = AppClock.currentUTC().truncatedTo(ChronoUnit.MICROS);
        // brokerage-agreement BR-11 (spec S6-D3): the signed document's values, a request error, before any read; a
        // document is not signed after today in Vietnam.
        SignedDocument document = SignedDocument.of(command.documentNumber(), command.signedOn(),
                command.sunvibeSignatoryName(), command.sunvibeSignatoryTitle(), AgreementVersion.todayInVietnam(now));
        AwaitingActivation awaiting = awaitingActivation(command.agreementId(), command.expectedDataVersion(),
                command.versionNo(), actor, now);
        // brokerage-agreement BR-11: the manager made the statement the server holds now.
        if (!ActivationStatement.VERSION.equals(command.statementVersion())) {
            throw AgreementRefusedException.statementOutdated();
        }
        // brokerage-agreement US-3 AC-4, BR-6: a version whose end date has passed is not put in effect; a start date
        // in the past is allowed, and the right then starts at the activation.
        AgreementVersion version = awaiting.version();
        if (!now.isBefore(version.endsAt())) {
            throw AgreementRefusedException.periodEnded();
        }
        // brokerage-agreement US-3 AC-4, E-2, E-11: the basis, the BR-3 stops and the owner are checked again, as the
        // sending and the owner's confirmation check them.
        List<ReasonGroup> groups = draftGroups(awaiting.agreement(), version, awaiting.lead().leadId());
        if (!groups.isEmpty()) {
            throw AgreementRefusedException.notEligible(groups);
        }
        BrokerageAgreement agreement = awaiting.agreement();
        ActivationDecision decision = ActivationDecision.activated(actor.userId(), now, ActivationStatement.VERSION,
                document);
        agreements.update(agreement.activate(version.getVersionNo(), decision, now));
        trail.recordEvent(agreement.getId(), version.getVersionNo(), AgreementEventType.ACTIVATED, actor.userId(), now);
        // brokerage-agreement BR-12 (ADR-012 decision 6, ADR-014 decision 2): the unit's answer changes now, at the
        // version's start when it lies ahead, and at the end of its end date.
        for (OutboxEvent event : activationEvents(agreement.getUnitId(), now, version.startsAt(), version.endsAt())) {
            outbox.append(event);
        }
        // brokerage-agreement US-3 AC-5, BR-17: the owner party's account gets a neutral notice by user id.
        notices.agreementActivated(agreement.getOwnerUserId());
    }

    /**
     * Statements (E05-S6 spec S6-D4, plan E05S6-P4): one agreement read, one locked lead read and one read of the
     * version's authors, then the guarded update of the agreement row with the version's row and one history insert. No
     * statement, no period check and no E05-S1 rerun (refusing a "no" needs none), no event (the answer does not
     * change) and no notice (brokerage-agreement BR-17 names none).
     */
    @Override
    @Transactional
    public void refuseActivation(AgreementRefuseActivationCommand command, StaffViewer actor) {
        Instant now = AppClock.currentUTC().truncatedTo(ChronoUnit.MICROS);
        // brokerage-agreement BR-17 (spec S6-D4): the note's rule is part of the body's shape, so it answers first.
        ActivationDecision decision = ActivationDecision.refused(actor.userId(), now, command.refusalType(),
                command.noteOrNull());
        AwaitingActivation awaiting = awaitingActivation(command.agreementId(), command.expectedDataVersion(),
                command.versionNo(), actor, now);
        // brokerage-agreement US-3 AC-3, E-7 (E05-S5 spec S5-D9): the agreement is drafting again and the Sale opens a
        // new draft with the new-draft command.
        BrokerageAgreement agreement = awaiting.agreement();
        int versionNo = awaiting.version().getVersionNo();
        agreements.update(agreement.refuseActivation(versionNo, decision, now));
        trail.recordEvent(agreement.getId(), versionNo, AgreementEventType.ACTIVATION_REFUSED, actor.userId(), now);
    }

    /**
     * E05-S6 spec S6-D12: the outbox events of an activation at {@code now}, in the order now, start, end: the event due
     * now; the event due at the version's start when it lies after {@code now}; the event due at the end of its end date.
     * A due event later than {@code now} + {@link #OUTBOX_DUE_HORIZON} is left out (the start's is never later than the
     * end's, so a left-out start leaves the end out too).
     */
    static List<OutboxEvent> activationEvents(String unitId, Instant now, Instant startsAt, Instant endsAt) {
        Instant horizon = now.plus(OUTBOX_DUE_HORIZON);
        List<OutboxEvent> events = new ArrayList<>();
        events.add(UnitAdvertisingChangedEvent.now(unitId));
        if (startsAt.isAfter(now) && !startsAt.isAfter(horizon)) {
            events.add(UnitAdvertisingChangedEvent.dueAt(unitId, startsAt));
        }
        if (!endsAt.isAfter(horizon)) {
            events.add(UnitAdvertisingChangedEvent.dueAt(unitId, endsAt));
        }
        return List.copyOf(events);
    }

    /** The agreement awaiting a decision on its version, with that version and the basis lead read under its lock. */
    private record AwaitingActivation(BrokerageAgreement agreement, AgreementVersion version, BasisLeadFacts lead) {
    }

    /**
     * Steps 3 to 7 of E05-S6 spec S6-D1, shared by the activation and its refusal (S6-D4), the first failing one
     * answering: the id and the agreement (404); its basis lead, locked to the commit and visible to the actor (404;
     * lock order of E05-S2 spec S2-D4: the lead, then the agreement's row through the guarded update); the actor is
     * independent (403); the agreement, with its due transitions applied, awaits confirmation with that version
     * confirmed and within its 14 days (409); the data version (409).
     */
    private AwaitingActivation awaitingActivation(String agreementId, long expectedDataVersion, int versionNo,
                                                  StaffViewer actor, Instant now) {
        if (agreementId == null || !AgreementQueryService.AGREEMENT_ID.matcher(agreementId).matches()) {
            throw new BrokerageNotFoundException();
        }
        BrokerageAgreement agreement = agreements.findById(agreementId).orElseThrow(BrokerageNotFoundException::new);
        BasisLeadFacts lead = leads.findVisibleForUpdate(agreement.getBasisLeadId(), actor.userId(),
                actor.expandedAuthorities()).orElseThrow(BrokerageNotFoundException::new);
        // brokerage-agreement BR-11, BR-16 (spec S6-D2): the decider is not the lead's current assignee, whatever role
        // the assignee holds, and wrote no version from 1 to n: a new draft starts as a copy of the latest version.
        if (actor.userId().equals(lead.assignedUserIdOrNull())
                || trail.versionAuthors(agreement.getId(), versionNo).contains(actor.userId())) {
            throw AgreementRefusedException.notIndependent();
        }
        // brokerage-agreement BR-5, BR-11: only the confirmed version awaiting activation, within its 14 days.
        BrokerageAgreement effective = agreement.effectiveAt(now);
        AgreementVersion version = effective.getStatus() != AgreementStatus.AWAITING_CONFIRMATION ? null
                : versionOf(effective, versionNo).filter(candidate -> candidate.activatableAt(now)).orElse(null);
        if (version == null) {
            throw AgreementRefusedException.notAwaitingActivation();
        }
        // brokerage-agreement BR-5: the decision checks the data version the caller read.
        if (agreement.getRowVersionOrNull() == null || agreement.getRowVersionOrNull() != expectedDataVersion) {
            throw AgreementRefusedException.stale();
        }
        return new AwaitingActivation(agreement, version, lead);
    }

    /**
     * One run of the lapse job (E05-S5 spec S5-D8, plan E05S5-P4): one selection of at most 100 due agreements, then per
     * agreement a short transaction of its own with one agreement read, the guarded update and one history insert. No
     * lead lock: like the owner's commands it writes only through the agreement row's guarded update. No ShedLock: two
     * instances' runs that load one row compute the same lapse, and the loser of the row-version race writes nothing, its
     * history row included, because the update comes first.
     */
    @Override
    public int storeDueLapses() {
        Instant now = AppClock.currentUTC().truncatedTo(ChronoUnit.MICROS);
        // brokerage-agreement BR-10: a version not answered within 7 days of its sending lapses.
        List<String> due = agreements.dueLapseAgreementIds(now.minus(AgreementVersion.ANSWER_WINDOW),
                MAX_LAPSES_PER_RUN);
        int stored = storeEach(due, agreementId -> storeDueLapse(agreementId, now));
        // brokerage-agreement BR-11, US-3 AC-4 (E05-S6 spec S6-D5): a version the owner confirmed and nobody activated
        // within 14 days lapses; the second branch of the same run, at most 100 more.
        List<String> dueActivation = agreements.dueActivationLapseAgreementIds(
                now.minus(AgreementVersion.ACTIVATION_WINDOW), MAX_LAPSES_PER_RUN);
        return stored + storeEach(dueActivation, agreementId -> storeDueLapse(agreementId, now));
    }

    /**
     * One run of the start job (E05-S6 spec S6-D6): one selection of at most 100 agreements whose activated version
     * holds its record and starts today or earlier in Vietnam, then per agreement a short transaction of its own with
     * one agreement read, the guarded update and one history insert, under the failure rule of {@link #storeEach}.
     */
    @Override
    public int storeDueStarts() {
        Instant now = AppClock.currentUTC().truncatedTo(ChronoUnit.MICROS);
        List<String> due = agreements.dueStartAgreementIds(AgreementVersion.todayInVietnam(now), MAX_STARTS_PER_RUN);
        return storeEach(due, agreementId -> storeDueStart(agreementId, now));
    }

    /**
     * Statements (E05-S8 spec S8-D8): one agreement read, one locked lead read, the agreement row's lock statement and
     * the three fresh reads; then the guarded update, the history rows of the transitions due before the stop and its
     * own, the record's insert and one outbox append, in this order.
     */
    @Override
    @Transactional
    public void suspend(AgreementSuspendCommand command, StaffViewer actor) {
        Instant now = AppClock.currentUTC().truncatedTo(ChronoUnit.MICROS);
        // brokerage-agreement BR-15, BR-17 (E05-S8 spec 3.1): the note's rule is part of the body, so it answers first
        AgreementSuspension record = AgreementSuspension.open(ids.generateId(), command.agreementId(),
                command.reasonType(), command.noteOrNull(), actor.userId(), now);
        StopTarget target = lockedForStop(command.agreementId(), actor);
        // brokerage-agreement BR-15, BR-16: a manager suspends; the method rule lets only managers through, so a viewer
        // refused here answers as one who may not see the agreement (spec 3.1)
        if (!mayAct(StopAction.SUSPEND, target, actor)) {
            throw new BrokerageNotFoundException();
        }
        BrokerageAgreement effective = target.agreement().effectiveAt(now);
        refuseEnded(effective);
        // brokerage-agreement BR-15: a manager suspends an agreement in effect, and only once
        if (!AgreementStopPolicy.admits(StopAction.SUSPEND, effective.getStatus())) {
            throw effective.getStatus() == AgreementStatus.SUSPENDED ? AgreementRefusedException.alreadySuspended()
                    : AgreementRefusedException.notInEffect();
        }
        agreements.update(effective.suspend(now));
        recordDueTransitions(effective.getId(), target.agreement().dueTransitionsAt(now));
        trail.recordEvent(effective.getId(), latestVersionNo(effective), AgreementEventType.SUSPENDED, actor.userId(),
                now);
        // E05-S8 spec S8-D6: the record opens only with ACTIVE -> SUSPENDED, in this transaction
        suspensions.open(record);
        outbox.append(UnitAdvertisingChangedEvent.now(effective.getUnitId()));
    }

    /** The statements of {@link #suspend}, the record's guarded close in place of its insert. */
    @Override
    @Transactional
    public void releaseSuspension(AgreementReleaseSuspensionCommand command, StaffViewer actor) {
        Instant now = AppClock.currentUTC().truncatedTo(ChronoUnit.MICROS);
        // brokerage-agreement BR-15 "Gỡ tạm ngưng cần lý do": the reason's rule is part of the body
        String reason = AgreementSuspension.releaseReason(command.reason());
        StopTarget target = lockedForStop(command.agreementId(), actor);
        // brokerage-agreement BR-15: only a manager who is not the lead's assignee lifts a suspension
        if (!mayAct(StopAction.RELEASE_SUSPENSION, target, actor)) {
            throw AgreementRefusedException.releaserNotIndependent();
        }
        BrokerageAgreement effective = target.agreement().effectiveAt(now);
        refuseEnded(effective);
        if (!AgreementStopPolicy.admits(StopAction.RELEASE_SUSPENSION, effective.getStatus())) {
            throw AgreementRefusedException.notSuspended();
        }
        // brokerage-agreement BR-5: the release is no stop of BR-5's list, so it checks the data version the caller read
        requireDataVersion(target.agreement(), command.expectedDataVersion());
        agreements.update(effective.releaseSuspension(now));
        recordDueTransitions(effective.getId(), target.agreement().dueTransitionsAt(now));
        trail.recordEvent(effective.getId(), latestVersionNo(effective), AgreementEventType.SUSPENSION_RELEASED,
                actor.userId(), now);
        // E05-S8 spec S8-D6: the record closes only with SUSPENDED -> ACTIVE; none open breaks the rule, and the whole
        // command rolls back (500)
        if (!suspensions.release(effective.getId(), actor.userId(), now, reason)) {
            throw new IllegalStateException("A suspended agreement holds no open suspension record.");
        }
        outbox.append(UnitAdvertisingChangedEvent.now(effective.getUnitId()));
    }

    /** The statements of {@link #suspend}, without a record. */
    @Override
    @Transactional
    public void terminate(AgreementTerminateCommand command, StaffViewer actor) {
        Instant now = AppClock.currentUTC().truncatedTo(ChronoUnit.MICROS);
        // brokerage-agreement BR-14, BR-17 (E05-S8 spec S8-D5): the fields of the type are the body, checked first
        AgreementTermination termination = AgreementTermination.of(command.type(), command.reportedOnOrNull(),
                command.channelOrNull(), command.introducedOrNull(), command.noteOrNull(),
                AgreementVersion.todayInVietnam(now), now);
        StopTarget target = lockedForStop(command.agreementId(), actor);
        StopAction action = AgreementStopPolicy.actionOf(command.type());
        if (!mayAct(action, target, actor)) {
            throw switch (action) {
                // brokerage-agreement BR-14, BR-16: the assignee records that the unit is let
                case TERMINATE_UNIT_LET -> AgreementRefusedException.assigneeOnly();
                // brokerage-agreement BR-16: Sunvibe's own termination is a manager's
                case TERMINATE_SUNVIBE -> AgreementRefusedException.terminationNotAllowed();
                // every viewer who sees the basis lead records the owner's withdrawal (spec 3.3, A-4)
                default -> new BrokerageNotFoundException();
            };
        }
        BrokerageAgreement effective = target.agreement().effectiveAt(now);
        // brokerage-agreement BR-14 (E05-S8 spec S8-D1): termination acts on every agreement that has not ended
        refuseEnded(effective);
        agreements.update(effective.terminate(termination, now));
        recordDueTransitions(effective.getId(), target.agreement().dueTransitionsAt(now));
        trail.recordEvent(effective.getId(), latestVersionNo(effective), AgreementEventType.TERMINATED,
                actor.userId(), now);
        outbox.append(UnitAdvertisingChangedEvent.now(effective.getUnitId()));
    }

    /** The statements of {@link #suspend}, without a record. */
    @Override
    @Transactional
    public void cancel(AgreementCancelCommand command, StaffViewer actor) {
        Instant now = AppClock.currentUTC().truncatedTo(ChronoUnit.MICROS);
        StopTarget target = lockedForStop(command.agreementId(), actor);
        // brokerage-agreement BR-16: cancelling an agreement that never took effect is the assignee's
        if (!mayAct(StopAction.CANCEL, target, actor)) {
            throw AgreementRefusedException.assigneeOnly();
        }
        BrokerageAgreement effective = target.agreement().effectiveAt(now);
        refuseEnded(effective);
        // brokerage-agreement BR-5: cancelled means it never took effect; one that did is ended by a termination
        if (!AgreementStopPolicy.admits(StopAction.CANCEL, effective.getStatus())) {
            throw AgreementRefusedException.tookEffect();
        }
        // brokerage-agreement BR-5: the cancellation is no stop of BR-5's list, so it checks the data version read
        requireDataVersion(target.agreement(), command.expectedDataVersion());
        agreements.update(effective.cancel(now));
        recordDueTransitions(effective.getId(), target.agreement().dueTransitionsAt(now));
        trail.recordEvent(effective.getId(), latestVersionNo(effective), AgreementEventType.CANCELLED, actor.userId(),
                now);
        outbox.append(UnitAdvertisingChangedEvent.now(effective.getUnitId()));
    }

    /** The agreement read under its row lock and its basis lead read under its own (E05-S8 spec S8-D8 steps 1 to 3). */
    private record StopTarget(BrokerageAgreement agreement, BasisLeadFacts lead) {
    }

    /**
     * Steps 1 to 3 of E05-S8 spec S8-D8: the id and the agreement (404), read to learn its basis lead; that lead, locked
     * to the commit and visible to the actor (404; lock order of E05-S2 spec S2-D4: the lead, then the agreement); then
     * the agreement row locked and the agreement read again, so the stop decides on what committed before its lock: none
     * answers 404, another basis lead answers Spring's optimistic-locking failure (409 CONCURRENT_MODIFICATION without
     * an entry).
     */
    private StopTarget lockedForStop(String agreementId, StaffViewer actor) {
        if (agreementId == null || !AgreementQueryService.AGREEMENT_ID.matcher(agreementId).matches()) {
            throw new BrokerageNotFoundException();
        }
        BrokerageAgreement first = agreements.findById(agreementId).orElseThrow(BrokerageNotFoundException::new);
        BasisLeadFacts lead = leads.findVisibleForUpdate(first.getBasisLeadId(), actor.userId(),
                actor.expandedAuthorities()).orElseThrow(BrokerageNotFoundException::new);
        BrokerageAgreement locked = agreements.findByIdForUpdate(agreementId)
                .orElseThrow(BrokerageNotFoundException::new);
        if (!locked.getBasisLeadId().equals(first.getBasisLeadId())) {
            throw new OptimisticLockingFailureException("The agreement's basis lead changed.");
        }
        return new StopTarget(locked, lead);
    }

    /**
     * The viewer column of the stop rule (E05-S8 spec S8-D11, plan E05S8-P4) on the basis lead read under its lock: the
     * assigned Sale (the current assignee holding SALE or SALE_LEADER), the current assignee in any role, a manager.
     */
    private static boolean mayAct(StopAction action, StopTarget target, StaffViewer actor) {
        String assignee = target.lead().assignedUserIdOrNull();
        return AgreementStopPolicy.mayAct(action,
                EligibilityPolicy.assignedSale(assignee, actor.userId(), actor.expandedAuthorities()),
                actor.userId().equals(assignee),
                actor.expandedAuthorities().stream().anyMatch(AgreementQueryService.DECIDER_ROLES::contains));
    }

    /** brokerage-agreement BR-5 (E05-S8 spec section 3): an agreement that ended answers AGREEMENT_ENDED first. */
    private static void refuseEnded(BrokerageAgreement effective) {
        if (!effective.getStatus().isUnfinished()) {
            throw AgreementRefusedException.ended();
        }
    }

    /** brokerage-agreement BR-5: the change checks the data version the caller read against the locked row's. */
    private static void requireDataVersion(BrokerageAgreement locked, long expectedDataVersion) {
        if (locked.getRowVersionOrNull() == null || locked.getRowVersionOrNull() != expectedDataVersion) {
            throw AgreementRefusedException.stale();
        }
    }

    /**
     * E05-S8 spec S8-D8 step 5: one history row per transition the effective copy stores, without an actor, at its own
     * moment, in time order, before the command's own row.
     */
    private void recordDueTransitions(String agreementId, List<DueTransition> due) {
        for (DueTransition transition : due) {
            trail.recordEvent(agreementId, transition.versionNo(), transition.type(), null, transition.at());
        }
    }

    /** E05-S8 spec S8-D10: a stop concerns the whole agreement, so its row names the latest version. */
    private static int latestVersionNo(BrokerageAgreement agreement) {
        return agreement.getVersions().getLast().getVersionNo();
    }

    /**
     * The failure rule of the stores (E05-S6 spec S6-D5, plan E05S6-P5; SV-319 (2)): each agreement in a transaction of
     * its own. A lost row-version race (another instance's run, the owner's answer or a staff command committed first)
     * is skipped silently: its state is what the next run reads. Any other runtime fault of one agreement is caught
     * after its transaction rolled back, at this job loop's boundary (java-core.md), and logged once with a fixed code,
     * the agreement id and the exception's class name, never its message, which may carry row values
     * (brokerage-agreement BR-17); the run goes on. An Error ends the run.
     *
     * @return the number of agreements stored
     */
    private int storeEach(List<String> agreementIds, Function<String, Boolean> store) {
        int stored = 0;
        for (String agreementId : agreementIds) {
            try {
                if (Boolean.TRUE.equals(transactions.execute(status -> store.apply(agreementId)))) {
                    stored++;
                }
            } catch (OptimisticLockingFailureException lost) {
                // the next run reads what committed first; this agreement's transaction rolled back whole
            } catch (RuntimeException failure) {
                log.error("BROKERAGE_TRANSITION_FAILED agreementId={} exception={}", agreementId,
                        failure.getClass().getName());
            }
        }
        return stored;
    }

    /**
     * brokerage-agreement BR-5 (E05-S2 spec S2-D4, E05-S5 spec S5-D8, E05-S6 spec S6-D5): the lapse is a stored
     * transition, written when it is due, its row dated by the deadline of the lapse that fell due; nothing when the
     * agreement was answered, decided, lapsed or given a new draft since the selection.
     */
    private boolean storeDueLapse(String agreementId, Instant now) {
        BrokerageAgreement agreement = agreements.findById(agreementId).orElse(null);
        AgreementVersion due = agreement == null ? null : agreement.dueLapseOrNull(now);
        if (due == null) {
            return false;
        }
        agreements.update(agreement.withDueLapse(now));
        trail.recordEvent(agreementId, due.getVersionNo(), AgreementEventType.VERSION_LAPSED, null,
                due.lapseDeadlineOrNull());
        return true;
    }

    /**
     * brokerage-agreement BR-5, BR-6 (E05-S2 spec S2-D4, E05-S6 spec S6-D6): the start of an activated version is a
     * stored transition, written when it is due, its row dated by the start of its start date, without an actor;
     * nothing when the agreement changed since the selection.
     */
    private boolean storeDueStart(String agreementId, Instant now) {
        BrokerageAgreement agreement = agreements.findById(agreementId).orElse(null);
        AgreementVersion due = agreement == null ? null : agreement.dueStartOrNull(now);
        if (due == null) {
            return false;
        }
        agreements.update(agreement.withDueStart(now));
        trail.recordEvent(agreementId, due.getVersionNo(), AgreementEventType.VERSION_STARTED, null, due.startsAt());
        return true;
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
