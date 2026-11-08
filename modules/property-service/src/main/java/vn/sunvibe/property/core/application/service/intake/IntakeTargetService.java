package vn.sunvibe.property.core.application.service.intake;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.property.core.application.exception.IntakeError;
import vn.sunvibe.property.core.application.exception.IntakeRefusedException;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeLead;
import vn.sunvibe.property.core.application.shared.external.survey.IntakeSource;
import vn.sunvibe.property.core.application.usecase.intake.IntakeActor;
import vn.sunvibe.property.core.application.usecase.intake.IntakeBlockView;
import vn.sunvibe.property.core.application.usecase.intake.IntakeTargetUseCase;
import vn.sunvibe.property.core.domain.model.property.enums.RentalLayout;
import vn.sunvibe.property.core.domain.model.intake.IntakeTarget;
import vn.sunvibe.property.core.domain.model.intake.InventoryIntake;
import vn.sunvibe.property.core.domain.model.intake.SelectedSpace;
import vn.sunvibe.property.core.domain.model.intake.SpaceUnitMapping;
import vn.sunvibe.property.core.domain.model.intake.TargetMode;
import vn.sunvibe.property.core.domain.repository.InventoryIntakeDomainRepository;

import java.time.Instant;
import java.util.List;

/**
 * Use Case Implementation - the target (property-create intake spec 4.7, 4.8, S-11). A create runs the duplicate gate, and a link
 * whose place differs opens a case, so either can leave the row in CASE_OPEN with its target stored.
 */
@Service
@RequiredArgsConstructor
public class IntakeTargetService implements IntakeTargetUseCase {

    private static final String MODE_FIELD = "mode";
    private static final String CANONICAL_FIELD = "canonicalSpaceId";

    private final IntakeCommandSupport support;
    private final IntakeEvaluator evaluator;
    private final IntakeBlockAssembler assembler;
    private final IntakeLinkRules linkRules;
    private final IntakeDuplicateGate gate;
    private final InventoryIntakeDomainRepository intakes;

    @Override
    @Transactional
    public IntakeBlockView target(String leadId, IntakeActor actor, TargetCommand command, Long expectedVersion) {
        TargetMode mode = mode(command);
        Instant now = IntakeCommandSupport.now();
        IntakeLead lead = support.lockVisibleLead(leadId, actor);
        support.requireAssignee(lead, actor);
        IntakeState state = support.lockedState(lead, now);
        support.requireCheckedRow(state);
        support.requireGate(state);
        support.requireVersion(state, expectedVersion);
        IntakeSource source = state.source().orElseThrow(() -> new IntakeRefusedException(IntakeError.INTAKE_STATE));

        InventoryIntake row = support.settle(state, now);
        IntakeTarget target = mode == TargetMode.CREATE ? create(state, row, source, command) : link(state, row, source, command);
        boolean held = mode == TargetMode.CREATE ? gate.holdsCreate(row, source.wardName(), now)
                : gate.holdsLink(row, target.propertyIdOrNull(), now);
        row.target(target, now);
        if (held) {
            row.openCase(now);
        }
        intakes.update(row);
        support.event(row.getId(), actor, IntakeEventAction.TARGET, now);
        return assembler.block(evaluator.read(lead, now), actor, now);
    }

    /** property-create intake spec 7.1: {@code canonicalSpaceId} only with CREATE; {@code propertyId} and the mappings only with LINK. */
    private static TargetMode mode(TargetCommand command) {
        boolean create = "CREATE".equals(command.mode());
        boolean link = "LINK".equals(command.mode());
        boolean mappings = command.mappings() != null && !command.mappings().isEmpty();
        boolean fits = create ? command.propertyIdOrNull() == null && !mappings
                : link && command.canonicalSpaceIdOrNull() == null && command.propertyIdOrNull() != null && mappings;
        if (!fits) {
            throw new IntakeRefusedException(IntakeError.VALIDATION_FAILED, List.of(MODE_FIELD));
        }
        return create ? TargetMode.CREATE : TargetMode.LINK;
    }

    private static IntakeTarget create(IntakeState state, InventoryIntake row, IntakeSource source, TargetCommand command) {
        if (state.forcedPropertyId().isPresent() || state.hasEarlierResult()) {
            throw new IntakeRefusedException(IntakeError.INTAKE_CREATE_NOT_ALLOWED);
        }
        String canonical = command.canonicalSpaceIdOrNull();
        boolean inSelection = row.getSelection().stream().map(SelectedSpace::spaceId).anyMatch(id -> id.equals(canonical));
        boolean fits = IntakeEvaluator.scope(source) == RentalLayout.PARTS ? inSelection : canonical == null;
        if (!fits) {
            throw new IntakeRefusedException(IntakeError.VALIDATION_FAILED, List.of(CANONICAL_FIELD));
        }
        return new IntakeTarget(TargetMode.CREATE, null, canonical, List.of());
    }

    private IntakeTarget link(IntakeState state, InventoryIntake row, IntakeSource source, TargetCommand command) {
        List<SpaceUnitMapping> mappings = command.mappings().stream()
                .map(item -> new SpaceUnitMapping(item.spaceId(), item.unitId())).toList();
        linkRules.require(state, row, source, command.propertyIdOrNull(), mappings);
        return new IntakeTarget(TargetMode.LINK, command.propertyIdOrNull(), null, mappings);
    }
}
