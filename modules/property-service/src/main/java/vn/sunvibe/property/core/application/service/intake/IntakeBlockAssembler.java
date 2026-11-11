package vn.sunvibe.property.core.application.service.intake;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.property.core.application.shared.external.survey.IntakeSource;
import vn.sunvibe.property.core.application.usecase.intake.IntakeActor;
import vn.sunvibe.property.core.application.usecase.intake.IntakeBlockView;
import vn.sunvibe.property.core.domain.model.intake.AvailabilityItem;
import vn.sunvibe.property.core.domain.model.intake.IntakeStatus;
import vn.sunvibe.property.core.domain.model.intake.IntakeSuspension;
import vn.sunvibe.property.core.domain.model.intake.IntakeTarget;
import vn.sunvibe.property.core.domain.model.intake.InventoryIntake;
import vn.sunvibe.property.core.domain.model.intake.OwnerRequest;
import vn.sunvibe.property.core.domain.model.intake.SelectedSpace;
import vn.sunvibe.property.core.domain.policy.intake.CasePolicy;
import vn.sunvibe.property.core.domain.policy.intake.CaseState;
import vn.sunvibe.property.core.domain.policy.intake.CurrentRowPolicy;
import vn.sunvibe.property.core.domain.policy.intake.GateReason;
import vn.sunvibe.property.core.domain.repository.IntakeUnitMappingDomainRepository;
import vn.sunvibe.property.core.domain.repository.InventoryReadRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Turns the state of a lead into its block (property-create intake spec 7.1). The block reads as the store would leave it, so
 * a computed DRAFT shows no target, availability or case (property-create intake spec 4.1).
 */
@Component
@RequiredArgsConstructor
public class IntakeBlockAssembler {

    private final InventoryReadRepository inventory;
    private final IntakeUnitMappingDomainRepository mappings;
    private final IntakeReadiness readiness;

    public IntakeBlockView block(IntakeState state, IntakeActor actor, Instant now) {
        InventoryIntake row = state.currentOrNull();
        boolean none = row == null || state.effectiveOrNull() == null;
        boolean cleared = !none && CurrentRowPolicy.readsCleared(row.getStatus(), state.effectiveOrNull());
        boolean work = !none && !cleared;
        return new IntakeBlockView(none ? null : state.effectiveOrNull().name(), none ? null : row.getVersion(),
                gate(state), earlierResult(state), source(state),
                none ? null : new IntakeBlockView.Selection(row.getSelection().stream().map(SelectedSpace::spaceId).toList()),
                ownerState(state, now), ownerCheck(state), work ? target(row) : null,
                work ? state.forcedPropertyId().map(this::code).orElse(null) : null,
                work ? availability(row) : List.of(),
                work ? CasePolicy.caseState(state.readings()).name() : CaseState.NONE.name(), result(state),
                readiness.view(state, now), suspensions(state, actor));
    }

    private static IntakeBlockView.Gate gate(IntakeState state) {
        return new IntakeBlockView.Gate(state.gateReasons().isEmpty(),
                state.gateReasons().stream().map(GateReason::name).toList());
    }

    private IntakeBlockView.EarlierResult earlierResult(IntakeState state) {
        return state.hasEarlierResult()
                ? new IntakeBlockView.EarlierResult(code(state.latestCompletedOrNull().getResultPropertyId())) : null;
    }

    private static IntakeBlockView.Source source(IntakeState state) {
        return state.source().map(source -> new IntakeBlockView.Source(source.revisionId(), source.rentalScope(),
                source.assetTypeOrNull(), source.spaces().stream().map(space -> new IntakeBlockView.Space(space.spaceId(),
                space.code(), space.location(), space.accessLimits(), space.sourceDate())).toList(),
                source.terms().stream().map(IntakeBlockAssembler::term).toList())).orElse(null);
    }

    private static IntakeBlockView.Term term(IntakeSource.Term term) {
        boolean known = term.amount() != null || term.text() != null;
        String name = term.spaceCodeOrNull() == null ? term.name() : term.spaceCodeOrNull() + "." + term.name();
        return new IntakeBlockView.Term(name, known ? new IntakeBlockView.TermValue(term.amount(), term.currency(), term.unit(),
                term.text()) : null, term.source(), term.date());
    }

    /** property-create intake spec 4.2: a decline or a void reads WAITING until the end of the request, so staff cannot read it. */
    private static String ownerState(IntakeState state, Instant now) {
        if (state.proofLive()) {
            return "CONFIRMED";
        }
        OwnerRequest request = state.latestRequestOrNull();
        boolean pending = request != null && request.getConsumedAt() == null && now.isBefore(request.getExpiresAt());
        boolean waiting = state.effectiveOrNull() == IntakeStatus.AWAITING_OWNER
                || (state.effectiveOrNull() == IntakeStatus.COMPLETED && pending);
        return waiting ? "WAITING" : "NONE";
    }

    private static String ownerCheck(IntakeState state) {
        return state.proofLive() ? state.proofOrNull().getOwnerCheck().name() : "NONE";
    }

    private IntakeBlockView.Target target(InventoryIntake row) {
        IntakeTarget target = row.getTarget();
        if (target == null) {
            return null;
        }
        return new IntakeBlockView.Target(target.mode().name(),
                target.propertyIdOrNull() == null ? null : code(target.propertyIdOrNull()), target.canonicalSpaceIdOrNull());
    }

    private static List<IntakeBlockView.Availability> availability(InventoryIntake row) {
        return row.getAvailability().stream().map(IntakeBlockAssembler::availability).toList();
    }

    private static IntakeBlockView.Availability availability(AvailabilityItem item) {
        return new IntakeBlockView.Availability(item.spaceId(), item.state().name(), item.contactedOn(), item.note(),
                item.expectedDateOrNull());
    }

    private IntakeBlockView.Result result(IntakeState state) {
        InventoryIntake completed = state.latestCompletedOrNull();
        if (completed == null || !completed.equals(state.currentOrNull())) {
            return null;
        }
        return new IntakeBlockView.Result(code(completed.getResultPropertyId()),
                mappings.findByIntakeId(completed.getId()).size(), completed.getCompletedAt());
    }

    /** property-create intake spec 7.1: only for manager roles, and the reason is left out for the assignee. */
    private List<IntakeBlockView.Suspension> suspensions(IntakeState state, IntakeActor actor) {
        if (!actor.manager()) {
            return List.of();
        }
        boolean assignee = actor.userId().equals(state.lead().assigneeOrNull());
        List<IntakeSuspension> active = state.suspensions().stream().filter(IntakeSuspension::isActive).toList();
        Map<String, String> codes = inventory.units(active.stream().map(IntakeSuspension::getUnitIdOrNull)
                        .filter(java.util.Objects::nonNull).toList()).stream()
                .collect(Collectors.toMap(InventoryReadRepository.UnitFacts::id, InventoryReadRepository.UnitFacts::code));
        return active.stream().map(item -> new IntakeBlockView.Suspension(item.getId(), codes.get(item.getUnitIdOrNull()),
                assignee ? null : item.getReason(), item.getCreatedAt())).toList();
    }

    private String code(String propertyId) {
        return inventory.property(propertyId).map(InventoryReadRepository.PropertyFacts::code).orElse(null);
    }

}
