package vn.sunvibe.property.core.application.service.intake;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.property.core.application.exception.IntakeError;
import vn.sunvibe.property.core.application.exception.IntakeRefusedException;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeLead;
import vn.sunvibe.property.core.application.usecase.intake.IntakeActor;
import vn.sunvibe.property.core.application.usecase.intake.IntakeAvailabilityUseCase;
import vn.sunvibe.property.core.application.usecase.intake.IntakeBlockView;
import vn.sunvibe.property.core.domain.model.intake.AvailabilityChoice;
import vn.sunvibe.property.core.domain.model.intake.AvailabilityItem;
import vn.sunvibe.property.core.domain.model.intake.InventoryIntake;
import vn.sunvibe.property.core.domain.model.intake.SelectedSpace;
import vn.sunvibe.property.core.domain.repository.InventoryIntakeDomainRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Use Case Implementation - availability (property-create intake spec 4.9, S-16). No state stands for "not contacted": without a
 * contact there is no completion (E-14). Nothing here touches a unit.
 */
@Service
@RequiredArgsConstructor
public class IntakeAvailabilityService implements IntakeAvailabilityUseCase {

    private static final Set<String> CHOICES = Arrays.stream(AvailabilityChoice.values()).map(Enum::name)
            .collect(Collectors.toSet());

    private final IntakeCommandSupport support;
    private final IntakeEvaluator evaluator;
    private final IntakeBlockAssembler assembler;
    private final InventoryIntakeDomainRepository intakes;

    @Override
    @Transactional
    public IntakeBlockView availability(String leadId, IntakeActor actor, List<Item> items, Long expectedVersion) {
        List<String> notes = items.stream().map(item -> IntakeText.require("note", item.note(), IntakeText.MAX_NOTE)).toList();
        Instant now = IntakeCommandSupport.now();
        IntakeLead lead = support.lockVisibleLead(leadId, actor);
        support.requireAssignee(lead, actor);
        IntakeState state = support.lockedState(lead, now);
        support.requireCheckedRow(state);
        support.requireGate(state);
        support.requireVersion(state, expectedVersion);
        requireValues(items, state.currentOrNull(), IntakeCommandSupport.today(now));

        InventoryIntake row = support.settle(state, now);
        row.availability(stored(items, notes, actor), now);
        intakes.update(row);
        support.event(row.getId(), actor, IntakeEventAction.AVAILABILITY, now);
        return assembler.block(evaluator.read(lead, now), actor, now);
    }

    private static void requireValues(List<Item> items, InventoryIntake row, LocalDate today) {
        Set<String> selected = row.getSelection().stream().map(SelectedSpace::spaceId).collect(Collectors.toSet());
        Set<String> given = items.stream().map(Item::spaceId).collect(Collectors.toSet());
        if (items.size() != selected.size() || !given.equals(selected)) {
            throw refused("items");
        }
        for (Item item : items) {
            if (!CHOICES.contains(item.state())) {
                throw refused("state");
            }
            if (item.contactedOn().isAfter(today)) {
                throw refused("contactedOn");
            }
        }
    }

    private static List<AvailabilityItem> stored(List<Item> items, List<String> notes, IntakeActor actor) {
        return java.util.stream.IntStream.range(0, items.size()).mapToObj(index -> {
            Item item = items.get(index);
            return new AvailabilityItem(item.spaceId(), AvailabilityChoice.valueOf(item.state()), item.contactedOn(),
                    notes.get(index), item.expectedDateOrNull(), actor.userId());
        }).toList();
    }

    private static IntakeRefusedException refused(String field) {
        return new IntakeRefusedException(IntakeError.VALIDATION_FAILED, List.of(field));
    }
}
