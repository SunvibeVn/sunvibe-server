package vn.sunvibe.property.core.application.service.intake;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.property.core.application.exception.IntakeError;
import vn.sunvibe.property.core.application.exception.IntakeRefusedException;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeLead;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeLeadPort;
import vn.sunvibe.property.core.application.shared.external.user.OwnerAccountFacts;
import vn.sunvibe.property.core.application.shared.external.user.OwnerNotice;
import vn.sunvibe.property.core.application.shared.external.user.UserDirectoryPort;
import vn.sunvibe.property.core.application.usecase.intake.IntakeActor;
import vn.sunvibe.property.core.application.usecase.intake.IntakeBlockView;
import vn.sunvibe.property.core.application.usecase.intake.OwnerCheckContext;
import vn.sunvibe.property.core.application.usecase.intake.OwnerCheckUseCase;
import vn.sunvibe.property.core.domain.model.intake.InventoryIntake;
import vn.sunvibe.property.core.domain.model.intake.OwnerCheckState;
import vn.sunvibe.property.core.domain.model.intake.OwnerProof;
import vn.sunvibe.property.core.domain.model.intake.OwnerRequest;
import vn.sunvibe.property.core.domain.policy.intake.IntakeCodes;
import vn.sunvibe.property.core.domain.repository.IntakeTrailDomainRepository;
import vn.sunvibe.property.core.domain.repository.InventoryIntakeDomainRepository;
import vn.sunvibe.property.core.domain.repository.InventoryReadRepository;
import vn.sunvibe.property.core.domain.repository.OwnerProofDomainRepository;
import vn.sunvibe.property.core.domain.repository.OwnerRequestDomainRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Use Case Implementation - the owner check (property-create intake spec 4.6 step 6, 5.4, S-8). Nobody who worked the lead runs it,
 * and it writes no intake event, so a manager's check makes nobody an actor (I-4).
 */
@Service
@RequiredArgsConstructor
public class OwnerCheckService implements OwnerCheckUseCase {

    private static final Duration PHRASE_VALID_FOR = Duration.ofMinutes(10);
    private static final String BASIS_FIELD = "basis";
    private static final String CONTEXT_READ = "OWNER_CHECK_CONTEXT";

    private final IntakeCommandSupport support;
    private final IntakeEvaluator evaluator;
    private final IntakeBlockAssembler assembler;
    private final IntakeLeadPort leads;
    private final UserDirectoryPort users;
    private final InventoryIntakeDomainRepository intakes;
    private final OwnerRequestDomainRepository requests;
    private final OwnerProofDomainRepository proofs;
    private final InventoryReadRepository inventory;
    private final IntakeTrailDomainRepository trail;
    private final IntakeWork work;

    /** Not read-only: it writes the access log row (spec 4.12). */
    @Override
    @Transactional
    public OwnerCheckContext context(String leadId, IntakeActor actor) {
        Instant now = IntakeCommandSupport.now();
        IntakeLead lead = leads.findVisible(leadId, actor.viewer())
                .orElseThrow(() -> new IntakeRefusedException(IntakeError.NOT_FOUND));
        IntakeState state = evaluator.read(lead, now);
        requireNotWorked(state, actor);
        requireLiveProof(state, EnumSet.of(OwnerCheckState.REQUIRED, OwnerCheckState.DONE));
        OwnerProof proof = state.proofOrNull();
        trail.recordAccess(actor.userId(), leadId, state.currentOrNull().getId(), null, CONTEXT_READ, now);
        OwnerAccountFacts account = users.accountContext(proof.getUserId());
        return new OwnerCheckContext(proof.getId(), account.addressMasked(), account.ageDays(), proof.getConfirmedAt(),
                proof.getOwnerCheckBasis(), signals(lead, proof, actor, now), refusals(leadId));
    }

    @Override
    @Transactional
    public StartedCheck start(String leadId, IntakeActor actor, String proofId, String basis, Long expectedVersion) {
        String text = IntakeText.require(BASIS_FIELD, basis, IntakeText.MAX_REASON);
        Instant now = IntakeCommandSupport.now();
        IntakeLead lead = support.lockVisibleLead(leadId, actor);
        IntakeState state = support.lockedState(lead, now);
        requireNotWorked(state, actor);
        requireLiveProof(state, EnumSet.of(OwnerCheckState.REQUIRED));
        requireProof(state, proofId);
        support.requireVersion(state, expectedVersion);

        InventoryIntake row = support.settle(state, now);
        OwnerProof proof = locked(proofId);
        String phrase = IntakeCodes.newPhrase();
        Instant expiresAt = now.plus(PHRASE_VALID_FOR);
        proof.startCheck(IntakeCodes.hash(proofId, phrase), expiresAt, actor.userId(), text, now);
        proofs.save(proof);
        row.recordChange(now);
        intakes.update(row);
        return new StartedCheck(phrase, expiresAt);
    }

    @Override
    @Transactional
    public IntakeBlockView refuse(String leadId, IntakeActor actor, String proofId, String basis, Long expectedVersion) {
        String text = IntakeText.require(BASIS_FIELD, basis, IntakeText.MAX_REASON);
        Instant now = IntakeCommandSupport.now();
        IntakeLead lead = support.lockVisibleLead(leadId, actor);
        IntakeState state = support.lockedState(lead, now);
        requireNotWorked(state, actor);
        requireNoRefusalBy(actor, leadId);
        requireLiveProof(state, EnumSet.of(OwnerCheckState.REQUIRED));
        requireProof(state, proofId);
        support.requireVersion(state, expectedVersion);

        InventoryIntake row = support.settle(state, now);
        OwnerProof proof = locked(proofId);
        proof.refuse(actor.userId(), text, now);
        proofs.save(proof);
        requests.findById(proof.getRequestId()).ifPresent(request -> {
            request.clearSnapshot();
            requests.save(request);
        });
        if (row.getStatus().isOpen()) {
            support.closeUndecidedCases(row.getId());
            row.backToDraft(now);
        } else {
            row.recordChange(now);
        }
        intakes.update(row);
        users.notifyOwner(proof.getUserId(), OwnerNotice.OWNER_CHECK_REFUSED);
        return assembler.block(evaluator.read(lead, now), actor, now);
    }

    private void requireNotWorked(IntakeState state, IntakeActor actor) {
        if (work.hasWorked(actor.userId(), state.lead())) {
            throw new IntakeRefusedException(IntakeError.OWNER_CHECK_NOT_ALLOWED);
        }
    }

    /** spec 4.6 step 6: the limit reads REFUSED only, so a proof that merely died after a start does not stop this manager. */
    private void requireNoRefusalBy(IntakeActor actor, String leadId) {
        boolean refusedBefore = proofs.findByLeadId(leadId).stream()
                .anyMatch(proof -> proof.getOwnerCheck() == OwnerCheckState.REFUSED
                        && actor.userId().equals(proof.getOwnerCheckByUserId()));
        if (refusedBefore) {
            throw new IntakeRefusedException(IntakeError.OWNER_CHECK_NOT_ALLOWED);
        }
    }

    private static void requireLiveProof(IntakeState state, Set<OwnerCheckState> allowed) {
        if (state.currentOrNull() == null || !state.proofLive() || !allowed.contains(state.proofOrNull().getOwnerCheck())) {
            throw new IntakeRefusedException(IntakeError.INTAKE_STATE);
        }
    }

    private static void requireProof(IntakeState state, String proofId) {
        if (!state.proofOrNull().getId().equals(proofId)) {
            throw new IntakeRefusedException(IntakeError.CONCURRENT_MODIFICATION);
        }
    }

    private OwnerProof locked(String proofId) {
        return proofs.lockById(proofId).orElseThrow(() -> new IntakeRefusedException(IntakeError.CONCURRENT_MODIFICATION));
    }

    private OwnerCheckContext.Signals signals(IntakeLead lead, OwnerProof proof, IntakeActor actor, Instant now) {
        String creator = requests.findById(proof.getRequestId()).map(OwnerRequest::getCreatedByUserId).orElseThrow();
        List<OwnerProof> ofAccount = proofs.findByUserId(proof.getUserId());
        List<OwnerProof> ofCreator = proofs.findByRequestCreator(creator);
        Set<String> creatorProofIds = ofCreator.stream().map(OwnerProof::getId).collect(Collectors.toSet());
        List<String> otherLeads = leads.otherOpenLeadIdsOfPhone(lead.leadId(), actor.viewer());
        return new OwnerCheckContext.Signals(ofAccount.stream().filter(evaluator::isLiveNow).count(), refused(ofAccount),
                ofAccount.stream().filter(item -> creatorProofIds.contains(item.getId())).count(), refused(ofCreator),
                otherLeads.size(), otherLeads.stream().filter(id -> namesAnother(id, proof.getUserId(), now)).count());
    }

    /** Only a proof that would count if it were dead needs its liveness read. */
    private long refused(List<OwnerProof> items) {
        return items.stream().filter(item -> item.countsAsRefused(false))
                .filter(item -> item.countsAsRefused(evaluator.isLiveNow(item))).count();
    }

    private boolean namesAnother(String leadId, String userId, Instant now) {
        IntakeState other = evaluator.read(leads.systemFacts(List.of(leadId)).get(leadId), now);
        if (other.proofLive() && !userId.equals(other.proofOrNull().getUserId())) {
            return true;
        }
        InventoryIntake result = other.latestCompletedOrNull();
        return result != null && inventory.property(result.getResultPropertyId())
                .map(InventoryReadRepository.PropertyFacts::ownerId).filter(owner -> !userId.equals(owner)).isPresent();
    }

    private List<OwnerCheckContext.Refusal> refusals(String leadId) {
        return proofs.findByLeadId(leadId).stream().filter(item -> item.getOwnerCheck() == OwnerCheckState.REFUSED)
                .map(item -> new OwnerCheckContext.Refusal(item.getOwnerCheckAt(), item.getOwnerCheckBasis())).toList();
    }
}
