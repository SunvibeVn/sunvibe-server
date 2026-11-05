package vn.sunvibe.property.core.application.service.intake;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.property.core.application.exception.IntakeError;
import vn.sunvibe.property.core.application.exception.IntakeRefusedException;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeLead;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeLeadPort;
import vn.sunvibe.property.core.application.usecase.intake.IntakeActor;
import vn.sunvibe.property.core.domain.model.intake.CaseStatus;
import vn.sunvibe.property.core.domain.model.intake.DuplicateCase;
import vn.sunvibe.property.core.domain.model.intake.IntakeStatus;
import vn.sunvibe.property.core.domain.model.intake.IntakeTarget;
import vn.sunvibe.property.core.domain.model.intake.InventoryIntake;
import vn.sunvibe.property.core.domain.model.intake.OwnerProof;
import vn.sunvibe.property.core.domain.model.intake.OwnerRequest;
import vn.sunvibe.property.core.domain.policy.intake.CasePolicy;
import vn.sunvibe.property.core.domain.policy.intake.CasePolicy.CaseFacts;
import vn.sunvibe.property.core.domain.policy.intake.CasePolicy.Reading;
import vn.sunvibe.property.core.domain.policy.intake.CurrentRowPolicy;
import vn.sunvibe.property.core.domain.policy.intake.GateReason;
import vn.sunvibe.property.core.domain.repository.DuplicateCaseDomainRepository;
import vn.sunvibe.property.core.domain.repository.IntakeTrailDomainRepository;
import vn.sunvibe.property.core.domain.repository.InventoryIntakeDomainRepository;
import vn.sunvibe.property.core.domain.repository.OwnerProofDomainRepository;
import vn.sunvibe.property.core.domain.repository.OwnerRequestDomainRepository;
import vn.sunvibe.shared.time.AppClock;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * What every intake command shares (property-create intake spec 4.1, 5.3, 5.4): the lock order lead, intake, then request,
 * proof or case; the check order; and the store of what a read computed. The caller holds the transaction, so a refusal rolls
 * the store back with everything else: only an accepted command stores.
 */
@Component
@RequiredArgsConstructor
public class IntakeCommandSupport {

    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    private final InventoryIntakeDomainRepository intakes;
    private final OwnerRequestDomainRepository requests;
    private final OwnerProofDomainRepository proofs;
    private final DuplicateCaseDomainRepository cases;
    private final IntakeTrailDomainRepository trail;
    private final IntakeLeadPort leads;
    private final IntakeEvaluator evaluator;

    /** The time of a command, as PostgreSQL stores it. */
    public static Instant now() {
        return AppClock.currentUTC().truncatedTo(ChronoUnit.MICROS);
    }

    /** A-7: the calendar day of a time, in Vietnam. */
    public static LocalDate today(Instant now) {
        return LocalDate.ofInstant(now, VIETNAM);
    }

    /** The lead under FOR NO KEY UPDATE: the first load of an assignee command (spec 5.3). */
    public IntakeLead lockVisibleLead(String leadId, IntakeActor actor) {
        return leads.findVisibleForUpdate(leadId, actor.viewer())
                .orElseThrow(() -> new IntakeRefusedException(IntakeError.NOT_FOUND));
    }

    /** spec 4.3: the lead's assignee, in a SALE or SALE_LEADER role; a manager role alone does not. */
    public void requireAssignee(IntakeLead lead, IntakeActor actor) {
        if (!actor.saleRole() || !actor.userId().equals(lead.assigneeOrNull())) {
            throw new IntakeRefusedException(IntakeError.INTAKE_NOT_ASSIGNEE);
        }
    }

    /** Takes the lock of every open row of the lead and of the latest result, then reads the state on fresh rows. */
    public IntakeState lockedState(IntakeLead lead, Instant now) {
        for (InventoryIntake row : intakes.findByLeadId(lead.leadId())) {
            if (row.getStatus().isOpen() || row.getStatus() == IntakeStatus.COMPLETED) {
                intakes.lockById(row.getId());
            }
        }
        return evaluator.read(lead, now);
    }

    public void requireGate(IntakeState state) {
        if (!state.gateReasons().isEmpty()) {
            throw new IntakeRefusedException(IntakeError.INTAKE_GATE_FAILED,
                    state.gateReasons().stream().map(GateReason::name).toList());
        }
    }

    /**
     * spec 5.4: no live proof or no check DONE answers OWNER_PROOF_REQUIRED, and any other state of 4.2 answers INTAKE_STATE.
     */
    public void requireCheckedRow(IntakeState state) {
        if (!state.checkDone()) {
            throw new IntakeRefusedException(IntakeError.OWNER_PROOF_REQUIRED);
        }
        if (state.effectiveOrNull() != IntakeStatus.OWNER_CONFIRMED) {
            throw new IntakeRefusedException(IntakeError.INTAKE_STATE);
        }
    }

    public void requireVersion(IntakeState state, Long expectedVersion) {
        if (!CurrentRowPolicy.versionMatches(expectedVersion, state.versionOrNull())) {
            throw new IntakeRefusedException(IntakeError.CONCURRENT_MODIFICATION);
        }
    }

    /**
     * Stores what the read computed (spec 4.1): abandons a dead open row, voiding its request and proof and closing its
     * undecided cases; moves a row to DRAFT as a withdrawal does; moves it between OWNER_CONFIRMED and CASE_OPEN.
     *
     * @return the current row as stored now; null with none
     */
    public InventoryIntake settle(IntakeState state, Instant now) {
        for (InventoryIntake dead : evaluator.deadOpenRows(state)) {
            voidWork(dead.getId(), now);
            closeUndecidedCases(dead.getId());
            dead.abandon(now);
            intakes.update(dead);
        }
        InventoryIntake row = state.currentOrNull();
        if (row == null) {
            return null;
        }
        if (!row.getStatus().isOpen()) {
            clearDeadRequestSnapshots(row.getId(), now);
            return row;
        }
        storeCaseReadings(state);
        IntakeStatus effective = state.effectiveOrNull();
        if (CurrentRowPolicy.readsCleared(row.getStatus(), effective)) {
            closeUndecidedCases(row.getId());
            clearDeadRequestSnapshots(row.getId(), now);
            row.backToDraft(now);
            intakes.update(row);
        } else if (row.getStatus() == IntakeStatus.CASE_OPEN && effective == IntakeStatus.OWNER_CONFIRMED) {
            row.closeCase(now);
            intakes.update(row);
        } else if (row.getStatus() == IntakeStatus.OWNER_CONFIRMED && effective == IntakeStatus.CASE_OPEN) {
            row.openCase(now);
            intakes.update(row);
        }
        clearOtherTarget(state, row, now);
        return row;
    }

    /** spec 4.10: a forced property clears any other target at the next accepted command of its side. */
    private void clearOtherTarget(IntakeState state, InventoryIntake row, Instant now) {
        IntakeTarget target = row.getTarget();
        if (target != null && state.forcedPropertyId().filter(id -> !id.equals(target.propertyIdOrNull())).isPresent()) {
            row.clearTarget(now);
            intakes.update(row);
        }
    }

    /** An OPEN case with a dead side reads closed everywhere; the next accepted command on a side stores it. */
    private void storeCaseReadings(IntakeState state) {
        for (int i = 0; i < state.cases().size(); i++) {
            CaseFacts facts = state.cases().get(i);
            if (facts.row().getStatus() == CaseStatus.OPEN && CasePolicy.read(facts) == Reading.CLOSED) {
                facts.row().close();
                cases.update(facts.row());
            }
        }
    }

    /** Voids every open request and the proof in force of a row; the snapshots go with them (spec 4.6 step 1). */
    public void voidWork(String intakeId, Instant now) {
        for (OwnerRequest request : requests.findByIntakeId(intakeId)) {
            if (!request.isClosed()) {
                request.voidNow(now);
                requests.save(request);
            }
        }
        proofs.findInForce(intakeId).ifPresent(proof -> voidProof(proof, now));
    }

    public void voidProof(OwnerProof proof, Instant now) {
        proof.voidNow(now);
        proofs.save(proof);
        requests.findById(proof.getRequestId()).ifPresent(request -> {
            request.clearSnapshot();
            requests.save(request);
        });
    }

    /** Any exit that is no decision closes the undecided cases of the row, on either side (spec 4.10). */
    public void closeUndecidedCases(String intakeId) {
        for (DuplicateCase item : cases.findForIntake(intakeId)) {
            if (item.getStatus() == CaseStatus.OPEN) {
                item.close();
                cases.update(item);
            }
        }
    }

    private void clearDeadRequestSnapshots(String intakeId, Instant now) {
        for (OwnerRequest request : requests.findByIntakeId(intakeId)) {
            if (!request.isOpen(now) && request.getConsumedAt() == null && request.getDisplaySnapshot() != null) {
                request.clearSnapshot();
                requests.save(request);
            }
        }
    }

    /** I-4: the trail of an assignee command. */
    public void event(String intakeId, IntakeActor actor, IntakeEventAction action, Instant now) {
        trail.recordEvent(intakeId, actor.userId(), action.name(), now);
    }

}
