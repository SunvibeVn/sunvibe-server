package vn.sunvibe.property.core.application.service.intake;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import vn.sunvibe.property.core.application.exception.IntakeError;
import vn.sunvibe.property.core.application.exception.IntakeRefusedException;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeHistoryEntry;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeLead;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeLeadPort;
import vn.sunvibe.property.core.application.shared.external.snapshot.OwnerSnapshotCodec;
import vn.sunvibe.property.core.application.shared.external.user.OwnerNotice;
import vn.sunvibe.property.core.application.shared.external.user.OwnerStandingFacts;
import vn.sunvibe.property.core.application.shared.external.user.PasswordCheck;
import vn.sunvibe.property.core.application.shared.external.user.UserDirectoryPort;
import vn.sunvibe.property.core.application.usecase.intake.OwnerConfirmationDetails;
import vn.sunvibe.property.core.application.usecase.intake.OwnerConfirmationItem;
import vn.sunvibe.property.core.application.usecase.intake.OwnerConfirmationUseCase;
import vn.sunvibe.property.core.application.usecase.intake.OwnerRequestSnapshot;
import vn.sunvibe.property.core.domain.model.intake.InventoryIntake;
import vn.sunvibe.property.core.domain.model.intake.OwnerProof;
import vn.sunvibe.property.core.domain.model.intake.OwnerRequest;
import vn.sunvibe.property.core.domain.model.intake.OwnerStatement;
import vn.sunvibe.property.core.domain.model.intake.ProofBinding;
import vn.sunvibe.property.core.domain.policy.intake.CurrentRowPolicy;
import vn.sunvibe.property.core.domain.policy.intake.IntakeCodes;
import vn.sunvibe.property.core.domain.repository.IntakeTrailDomainRepository;
import vn.sunvibe.property.core.domain.repository.InventoryIntakeDomainRepository;
import vn.sunvibe.property.core.domain.repository.OwnerProofDomainRepository;
import vn.sunvibe.property.core.domain.repository.OwnerRequestDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Use Case Implementation - the owner's verbs (property-create intake spec 4.6, 5.4, 7.3). There is no surrounding transaction on
 * purpose: a wrong code and a wrong password must commit their counters though the command is refused, and bcrypt must run with
 * no row locked. The accepted part then runs in one short transaction: lead FOR SHARE, intake, request, then every re-check.
 */
@Service
@RequiredArgsConstructor
public class OwnerConfirmationService implements OwnerConfirmationUseCase {

    private static final String PASSWORD_FIELD = "currentPassword";
    private static final String OWNER_CONFIRMED_EVENT = "OWNER_CONFIRMED";
    private static final String OWNER_WITHDRAWN_EVENT = "OWNER_WITHDRAWN";

    private final OwnerRequestDomainRepository requests;
    private final OwnerProofDomainRepository proofs;
    private final InventoryIntakeDomainRepository intakes;
    private final IntakeTrailDomainRepository trail;
    private final IntakeLeadPort leads;
    private final UserDirectoryPort users;
    private final IntakeEvaluator evaluator;
    private final IntakeCommandSupport support;
    private final OwnerSnapshotCodec snapshots;
    private final IdGenerator<String> ulidGenerator;
    private final TransactionTemplate transaction;

    @Override
    public List<OwnerConfirmationItem> list(String userId) {
        return transaction.execute(status -> items(userId, IntakeCommandSupport.now()));
    }

    private List<OwnerConfirmationItem> items(String userId, Instant now) {
        if (!users.ownerStanding(userId).eligible()) {
            return List.of();
        }
        List<OwnerConfirmationItem> items = new ArrayList<>();
        for (OwnerRequest request : requests.findByTargetUser(userId)) {
            if (request.isOpen(now)) {
                items.add(OwnerConfirmationItem.pending(request.getId(), request.getExpiresAt()));
            } else if (request.getConsumedAt() != null && request.getDisplaySnapshot() != null) {
                IntakeState state = stateOf(request, now);
                liveProof(state, request).ifPresent(proof -> items.add(OwnerConfirmationItem.confirmed(request.getId(),
                        request.getExpiresAt(), snapshots.read(request.getDisplaySnapshot()), proof.phraseOpen(now))));
            }
        }
        return items;
    }

    /** property-create intake spec 4.6 step 3: no lock, because it changes nothing and confirm checks again under the lock. */
    @Override
    public OwnerConfirmationDetails details(String userId, String requestId, String code) {
        OwnerRequest request = bound(userId, requestId);
        requireCode(request, code);
        return transaction.execute(status -> {
            requireUsable(request, stateOf(request, IntakeCommandSupport.now()));
            return details(snapshots.read(request.getDisplaySnapshot()));
        });
    }

    private static OwnerConfirmationDetails details(OwnerRequestSnapshot snapshot) {
        return new OwnerConfirmationDetails(snapshot.leadCode(), snapshot.wardName(), snapshot.assetType(), snapshot.address(),
                snapshot.parts(), new OwnerConfirmationDetails.Statement(OwnerStatement.VERSION, OwnerStatement.TEXT));
    }

    @Override
    public void confirm(String userId, String requestId, String code, String password, String statementVersion) {
        OwnerRequest bound = bound(userId, requestId);
        if (!OwnerStatement.VERSION.equals(statementVersion)) {
            throw invalid();
        }
        requireCode(bound, code);
        requirePassword(userId, password);
        transaction.executeWithoutResult(status -> confirmLocked(userId, bound));
    }

    private void confirmLocked(String userId, OwnerRequest bound) {
        Instant now = IntakeCommandSupport.now();
        Locked locked = lock(bound);
        OwnerRequest request = locked.request();
        InventoryIntake row = locked.row();
        OwnerStandingFacts standing = users.ownerStanding(userId);
        if (!request.isOpen(now) || !standing.eligible()) {
            throw invalid();
        }
        requireUsable(request, evaluator.read(locked.lead(), now));

        // I-3: an earlier row counts, live or not, or the partial unique index would fail the insert
        proofs.findInForce(row.getId()).ifPresent(earlier -> support.voidProof(earlier, now));
        ProofBinding binding = new ProofBinding(row.getLeadId(), row.getIntentCycle(), row.getSourceChecklistId(),
                row.scopeHash());
        proofs.save(OwnerProof.create(ulidGenerator.generateId(), row.getId(), request.getId(), binding, userId,
                OwnerStatement.VERSION, standing.signInLosses(), now));
        request.consume(now);
        requests.save(request);
        row.confirmOwner(now);
        intakes.update(row);
        users.notifyOwner(userId, OwnerNotice.OWNER_CONFIRMED);
        trail.recordEvent(row.getId(), null, OWNER_CONFIRMED_EVENT, now);
    }

    /** property-create intake spec 4.6 step 3: it only voids, so it needs no check of the source or the creator, and it raises no version (A-13). */
    @Override
    public void decline(String userId, String requestId, String code) {
        OwnerRequest bound = bound(userId, requestId);
        requireCode(bound, code);
        transaction.executeWithoutResult(status -> {
            Instant now = IntakeCommandSupport.now();
            OwnerRequest request = lock(bound).request();
            if (!request.isOpen(now)) {
                throw invalid();
            }
            request.decline(now);
            requests.save(request);
        });
    }

    @Override
    public void withdraw(String userId, String requestId, String password) {
        OwnerRequest bound = bound(userId, requestId);
        // A proof withdrawn or voided answers before the password counter can move; one that died another way answers under the
        // lock. Only the proof row is read here: a lead would stay in the persistence context and hide what the lock must see
        if (proofs.findByRequestId(requestId).filter(OwnerProof::inForce).isEmpty()) {
            throw notFound();
        }
        requirePassword(userId, password);
        transaction.executeWithoutResult(status -> withdrawLocked(userId, bound));
    }

    private void withdrawLocked(String userId, OwnerRequest bound) {
        Instant now = IntakeCommandSupport.now();
        Locked locked = lock(bound);
        OwnerRequest request = locked.request();
        InventoryIntake row = locked.row();
        OwnerProof proof = liveProof(evaluator.read(locked.lead(), now), request)
                .flatMap(found -> proofs.lockById(found.getId())).orElseThrow(OwnerConfirmationService::notFound);

        proof.withdraw(now);
        proofs.save(proof);
        request.clearSnapshot();
        requests.save(request);
        if (row.getStatus().isOpen()) {
            support.closeUndecidedCases(row.getId());
            row.backToDraft(now);
        } else {
            row.recordChange(now);
            if (proof.getId().equals(row.getProofId())) {
                // property-create intake spec 4.12: a null actor, so no owner name appears, and only for the proof in force of the result
                leads.recordActivity(row.getLeadId(), new IntakeHistoryEntry(IntakeHistoryEntry.Type.INVENTORY_INTAKE_SUSPENDED,
                        null, row.getId(), row.getIntakeNo(), now));
            }
        }
        intakes.update(row);
        users.notifyOwner(userId, OwnerNotice.OWNER_WITHDRAWN);
        trail.recordEvent(row.getId(), null, OWNER_WITHDRAWN_EVENT, now);
    }

    /**
     * property-create intake spec 5.4: the phrase counter commits by itself; the right phrase then runs under the locks, where the proof must still be
     * live on the current row. The creator need not be the assignee: a proof survives a reassignment (property-create BR-15).
     */
    @Override
    public void enterPhrase(String userId, String requestId, String phrase) {
        OwnerRequest bound = bound(userId, requestId);
        OwnerProof scalar = proofs.findByRequestId(requestId).filter(OwnerProof::inForce)
                .filter(proof -> proof.phraseOpen(IntakeCommandSupport.now())).orElseThrow(OwnerConfirmationService::invalid);
        if (!IntakeCodes.matches(scalar.getId(), phrase, scalar.getPhraseHash())) {
            transaction.executeWithoutResult(status -> proofs.countWrongPhrase(scalar.getId(), OwnerProof.MAX_WRONG_PHRASES));
            throw invalid();
        }
        transaction.executeWithoutResult(status -> phraseLocked(bound, scalar));
    }

    private void phraseLocked(OwnerRequest bound, OwnerProof scalar) {
        Instant now = IntakeCommandSupport.now();
        Locked locked = lock(bound);
        OwnerProof proof = proofs.lockById(scalar.getId()).orElseThrow(OwnerConfirmationService::invalid);
        boolean samePhrase = proof.phraseOpen(now) && scalar.getPhraseHash().equals(proof.getPhraseHash());
        if (!samePhrase || liveProof(evaluator.read(locked.lead(), now), locked.request()).isEmpty()) {
            throw invalid();
        }
        proof.checkDone(now);
        proofs.save(proof);
        locked.row().recordChange(now);
        intakes.update(locked.row());
    }

    /** property-create intake spec 5.4: bound to this account, else 404 before any counter moves; an account in a staff role reads the same. */
    private OwnerRequest bound(String userId, String requestId) {
        OwnerRequest request = requests.findById(requestId).filter(item -> userId.equals(item.getTargetUserId()))
                .orElseThrow(OwnerConfirmationService::notFound);
        if (!users.ownerStanding(userId).eligible()) {
            throw notFound();
        }
        return request;
    }

    /** A-5: the counter commits by itself, so a refused command still counts; an expired or closed code is no wrong code. */
    private void requireCode(OwnerRequest request, String code) {
        Instant now = IntakeCommandSupport.now();
        if (!request.isOpen(now)) {
            throw invalid();
        }
        if (!IntakeCodes.matches(request.getId(), code, request.getCodeHash())) {
            transaction.executeWithoutResult(status -> requests.countWrongCode(request.getId(), OwnerRequest.MAX_WRONG_CODES, now));
            throw invalid();
        }
    }

    private void requirePassword(String userId, String password) {
        PasswordCheck check = users.checkPassword(userId, password);
        if (check.outcome() == PasswordCheck.Outcome.LOCKED) {
            throw new IntakeRefusedException(IntakeError.ACCOUNT_LOCKED);
        }
        if (check.outcome() != PasswordCheck.Outcome.OK) {
            throw new IntakeRefusedException(IntakeError.VALIDATION_FAILED, List.of(PASSWORD_FIELD));
        }
    }

    /** property-create intake spec 4.6 steps 3, 4: the source of the request's row is still live and its creator still the assignee. */
    private static void requireUsable(OwnerRequest request, IntakeState state) {
        boolean live = isCurrent(state, request) && CurrentRowPolicy.sourceLive(state.currentOrNull(), state.sourceFacts());
        if (!live || !request.getCreatedByUserId().equals(state.lead().assigneeOrNull())) {
            throw invalid();
        }
    }

    /** The proof of this request, when it is live on the current row of its lead. */
    private static Optional<OwnerProof> liveProof(IntakeState state, OwnerRequest request) {
        boolean live = isCurrent(state, request) && state.proofLive() && state.proofOrNull().getRequestId().equals(request.getId());
        return live ? Optional.of(state.proofOrNull()) : Optional.empty();
    }

    private static boolean isCurrent(IntakeState state, OwnerRequest request) {
        return state.currentOrNull() != null && state.currentOrNull().getId().equals(request.getIntakeId());
    }

    /** The state with no viewer: the owner side has none. Inside a transaction, as the ports ask. */
    private IntakeState stateOf(OwnerRequest request, Instant now) {
        String leadId = leadIdOf(request);
        return evaluator.read(leads.systemFacts(List.of(leadId)).get(leadId), now);
    }

    private String leadIdOf(OwnerRequest request) {
        return intakes.findById(request.getIntakeId()).map(InventoryIntake::getLeadId)
                .orElseThrow(OwnerConfirmationService::notFound);
    }

    /** property-create intake spec 5.3: lead, intake, request, each read again after its lock. */
    private Locked lock(OwnerRequest bound) {
        IntakeLead lead = leads.lockForShare(leadIdOf(bound)).orElseThrow(OwnerConfirmationService::notFound);
        InventoryIntake row = intakes.lockById(bound.getIntakeId()).orElseThrow(OwnerConfirmationService::notFound);
        OwnerRequest request = requests.lockById(bound.getId()).orElseThrow(OwnerConfirmationService::notFound);
        return new Locked(lead, row, request);
    }

    private static IntakeRefusedException invalid() {
        return new IntakeRefusedException(IntakeError.CONFIRMATION_INVALID);
    }

    private static IntakeRefusedException notFound() {
        return new IntakeRefusedException(IntakeError.NOT_FOUND);
    }

    private record Locked(IntakeLead lead, InventoryIntake row, OwnerRequest request) {
    }
}
