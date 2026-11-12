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
import vn.sunvibe.property.core.application.usecase.intake.IntakeSelectionUseCase;
import vn.sunvibe.property.core.domain.model.intake.IntakeSourceRef;
import vn.sunvibe.property.core.domain.model.intake.IntakeStatus;
import vn.sunvibe.property.core.domain.model.intake.InventoryIntake;
import vn.sunvibe.property.core.domain.model.intake.SelectedSpace;
import vn.sunvibe.property.core.domain.policy.intake.AddressNormalizer;
import vn.sunvibe.property.core.domain.repository.InventoryIntakeDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Use Case Implementation - the selection (property-create intake spec 4.5, S-2): the first one creates the row, a different
 * one goes back to DRAFT and voids the request, the proof, the undecided cases, the target and the availability. Saving the
 * same parts again (any order) changes nothing and writes no trail row, after the same checks and the settle step
 * (property-create SV-202).
 */
@Service
@RequiredArgsConstructor
public class IntakeSelectionService implements IntakeSelectionUseCase {

    private static final int MAX_PARTS = 50;

    private final IntakeCommandSupport support;
    private final IntakeEvaluator evaluator;
    private final IntakeBlockAssembler assembler;
    private final InventoryIntakeDomainRepository intakes;
    private final IdGenerator<String> ulidGenerator;

    @Override
    @Transactional
    public IntakeBlockView select(String leadId, IntakeActor actor, List<String> spaceIds, Long expectedVersion) {
        Instant now = IntakeCommandSupport.now();
        IntakeLead lead = support.lockVisibleLead(leadId, actor);
        support.requireAssignee(lead, actor);
        IntakeState state = support.lockedState(lead, now);
        if (state.effectiveOrNull() == IntakeStatus.COMPLETED) {
            throw new IntakeRefusedException(IntakeError.INTAKE_STATE);
        }
        support.requireGate(state);
        support.requireVersion(state, expectedVersion);
        IntakeSource source = state.source().orElseThrow(() -> new IntakeRefusedException(IntakeError.INTAKE_STATE));
        List<SelectedSpace> selection = selection(source, spaceIds);

        InventoryIntake row = support.settle(state, now);
        if (row != null && row.sameScope(selection)) {
            // property-create SV-202: the same parts again change nothing; the checks and the settle above ran as for any command
            return assembler.block(evaluator.read(lead, now), actor, now);
        }
        InventoryIntake saved = row == null ? create(lead, source, selection, now) : change(row, selection, now);
        support.event(saved.getId(), actor, IntakeEventAction.SELECTION, now);
        return assembler.block(evaluator.read(lead, now), actor, now);
    }

    private InventoryIntake create(IntakeLead lead, IntakeSource source, List<SelectedSpace> selection, Instant now) {
        InventoryIntake row = InventoryIntake.create(ulidGenerator.generateId(), intakes.nextIntakeNo(lead.leadId()),
                lead.leadId(), lead.intentCycle(), reference(source), selection, now);
        intakes.insert(row);
        return row;
    }

    private InventoryIntake change(InventoryIntake row, List<SelectedSpace> selection, Instant now) {
        support.voidWork(row.getId(), now);
        support.closeUndecidedCases(row.getId());
        row.select(selection, now);
        intakes.update(row);
        return row;
    }

    private static IntakeSourceRef reference(IntakeSource source) {
        return new IntakeSourceRef(source.revisionId(), source.contentSha256(), IntakeEvaluator.scope(source),
                IntakeEvaluator.assetType(source), source.wardCode(), AddressNormalizer.normalize(source.addressText()),
                source.latitude() == null ? null : source.latitude().doubleValue(),
                source.longitude() == null ? null : source.longitude().doubleValue());
    }

    /** property-create intake spec 4.5: WHOLE takes the one space, PARTS 1..50 unique spaces of the source; anything outside cannot be chosen. */
    private static List<SelectedSpace> selection(IntakeSource source, List<String> spaceIds) {
        Map<String, IntakeSource.Space> spaces = source.spaces().stream()
                .collect(Collectors.toMap(IntakeSource.Space::spaceId, Function.identity()));
        boolean unique = new HashSet<>(spaceIds).size() == spaceIds.size();
        boolean inside = spaces.keySet().containsAll(spaceIds);
        boolean sized = !spaceIds.isEmpty() && spaceIds.size() <= MAX_PARTS;
        boolean wholeTakesAll = !"WHOLE".equals(source.rentalScope()) || spaceIds.size() == source.spaces().size();
        if (!unique || !inside || !sized || !wholeTakesAll) {
            throw new IntakeRefusedException(IntakeError.INTAKE_SELECTION_INVALID);
        }
        return spaceIds.stream().map(spaces::get)
                .map(space -> new SelectedSpace(space.spaceId(), space.code(), space.location())).toList();
    }
}
