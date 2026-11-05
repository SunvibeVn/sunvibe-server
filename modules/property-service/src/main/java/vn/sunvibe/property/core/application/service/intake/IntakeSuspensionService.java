package vn.sunvibe.property.core.application.service.intake;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.property.core.application.exception.IntakeError;
import vn.sunvibe.property.core.application.exception.IntakeRefusedException;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeHistoryEntry;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeLead;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeLeadPort;
import vn.sunvibe.property.core.application.usecase.intake.IntakeActor;
import vn.sunvibe.property.core.application.usecase.intake.IntakeBlockView;
import vn.sunvibe.property.core.application.usecase.intake.IntakeSuspensionUseCase;
import vn.sunvibe.property.core.domain.model.intake.IntakeStatus;
import vn.sunvibe.property.core.domain.model.intake.IntakeSuspension;
import vn.sunvibe.property.core.domain.model.intake.InventoryIntake;
import vn.sunvibe.property.core.domain.model.intake.UnitMapping;
import vn.sunvibe.property.core.domain.repository.IntakeSuspensionDomainRepository;
import vn.sunvibe.property.core.domain.repository.IntakeUnitMappingDomainRepository;
import vn.sunvibe.property.core.domain.repository.InventoryIntakeDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Use Case Implementation - suspensions (property-create intake spec 4.11, 5.4, S-27). They hold for the lead, in any state, none
 * included, so a new intake cannot dodge one. The reasons stay out of every log and of History.
 */
@Service
@RequiredArgsConstructor
public class IntakeSuspensionService implements IntakeSuspensionUseCase {

    private static final String REASON_FIELD = "reason";
    private static final String RELEASE_REASON_FIELD = "releaseReason";

    private final IntakeCommandSupport support;
    private final IntakeEvaluator evaluator;
    private final IntakeBlockAssembler assembler;
    private final IntakeLeadPort leads;
    private final InventoryIntakeDomainRepository intakes;
    private final IntakeSuspensionDomainRepository suspensions;
    private final IntakeUnitMappingDomainRepository mappings;
    private final IdGenerator<String> ulidGenerator;

    @Override
    @Transactional
    public IntakeBlockView suspend(String leadId, IntakeActor actor, String unitIdOrNull, String reason, Long expectedVersion) {
        String text = IntakeText.require(REASON_FIELD, reason, IntakeText.MAX_REASON);
        Instant now = IntakeCommandSupport.now();
        IntakeLead lead = support.lockVisibleLead(leadId, actor);
        requireNotAssignee(lead, actor);
        IntakeState state = support.lockedState(lead, now);
        Optional<InventoryIntake> named = unitIdOrNull == null ? onLead(state) : Optional.of(completedRowOf(state, unitIdOrNull));
        support.requireVersion(state, expectedVersion);

        InventoryIntake row = support.settle(state, now);
        suspensions.save(IntakeSuspension.create(ulidGenerator.generateId(), leadId, named.map(InventoryIntake::getId).orElse(null),
                unitIdOrNull, text, actor.userId(), now));
        raise(row, now);
        history(IntakeHistoryEntry.Type.INVENTORY_INTAKE_SUSPENDED, lead, actor, named, now);
        return assembler.block(evaluator.read(lead, now), actor, now);
    }

    @Override
    @Transactional
    public IntakeBlockView release(String leadId, IntakeActor actor, String suspensionId, String releaseReason,
                                   Long expectedVersion) {
        String text = IntakeText.require(RELEASE_REASON_FIELD, releaseReason, IntakeText.MAX_REASON);
        Instant now = IntakeCommandSupport.now();
        IntakeLead lead = support.lockVisibleLead(leadId, actor);
        requireNotAssignee(lead, actor);
        IntakeState state = support.lockedState(lead, now);
        IntakeSuspension suspension = suspensions.findById(suspensionId).filter(item -> item.getLeadId().equals(leadId))
                .orElseThrow(() -> new IntakeRefusedException(IntakeError.NOT_FOUND));
        support.requireVersion(state, expectedVersion);
        if (!suspension.isActive()) {
            throw new IntakeRefusedException(IntakeError.INTAKE_STATE);
        }

        InventoryIntake row = support.settle(state, now);
        suspension.release(text, actor.userId(), now);
        suspensions.save(suspension);
        raise(row, now);
        Optional<InventoryIntake> named = Optional.ofNullable(suspension.getIntakeIdOrNull()).flatMap(intakes::findById);
        history(IntakeHistoryEntry.Type.INVENTORY_INTAKE_RESTORED, lead, actor, named, now);
        return assembler.block(evaluator.read(lead, now), actor, now);
    }

    private static void requireNotAssignee(IntakeLead lead, IntakeActor actor) {
        if (actor.userId().equals(lead.assigneeOrNull())) {
            throw new IntakeRefusedException(IntakeError.OWNER_CHECK_NOT_ALLOWED);
        }
    }

    /** The row a whole-lead suspension names: the current one, else the lead's latest result, else none. */
    private static Optional<InventoryIntake> onLead(IntakeState state) {
        return Optional.ofNullable(state.currentOrNull()).or(() -> Optional.ofNullable(state.latestCompletedOrNull()));
    }

    /** spec 5.4: a unit mapped by a COMPLETED intake of this lead, an earlier one included; anything else is 404. */
    private InventoryIntake completedRowOf(IntakeState state, String unitId) {
        List<String> intakeIds = mappings.findByUnitIds(List.of(unitId)).stream().map(UnitMapping::intakeId).toList();
        return state.rows().stream().filter(row -> row.getStatus() == IntakeStatus.COMPLETED)
                .filter(row -> intakeIds.contains(row.getId())).findFirst()
                .orElseThrow(() -> new IntakeRefusedException(IntakeError.NOT_FOUND));
    }

    /** A-13: a command that writes raises the version of the current row, when there is one. */
    private void raise(InventoryIntake rowOrNull, Instant now) {
        if (rowOrNull != null) {
            rowOrNull.recordChange(now);
            intakes.update(rowOrNull);
        }
    }

    /** spec 4.12: reference only; a suspension with no intake has both references null. */
    private void history(IntakeHistoryEntry.Type type, IntakeLead lead, IntakeActor actor, Optional<InventoryIntake> named,
                         Instant now) {
        leads.recordActivity(lead.leadId(), new IntakeHistoryEntry(type, actor.userId(),
                named.map(InventoryIntake::getId).orElse(null), named.map(InventoryIntake::getIntakeNo).orElse(null), now));
    }
}
