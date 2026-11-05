package vn.sunvibe.property.core.application.service.intake;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.property.core.application.shared.external.survey.IntakeSource;
import vn.sunvibe.property.core.application.shared.external.user.OwnerStandingFacts;
import vn.sunvibe.property.core.application.shared.external.user.UserDirectoryPort;
import vn.sunvibe.property.core.application.usecase.intake.IntakeBlockView;
import vn.sunvibe.property.core.domain.model.intake.AvailabilityItem;
import vn.sunvibe.property.core.domain.model.intake.IntakeSuspension;
import vn.sunvibe.property.core.domain.model.intake.InventoryIntake;
import vn.sunvibe.property.core.domain.model.intake.OwnerProof;
import vn.sunvibe.property.core.domain.model.intake.TargetMode;
import vn.sunvibe.property.core.domain.model.intake.UnitMapping;
import vn.sunvibe.property.core.domain.policy.intake.ReadinessPolicy;
import vn.sunvibe.property.core.domain.policy.intake.ReadinessReason;
import vn.sunvibe.property.core.domain.policy.intake.ReadinessState;
import vn.sunvibe.property.core.domain.repository.IntakeUnitMappingDomainRepository;
import vn.sunvibe.property.core.domain.repository.InventoryReadRepository;
import vn.sunvibe.property.core.domain.repository.OwnerProofDomainRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The readiness of the lead's latest COMPLETED row, computed on every read (property-create intake spec 4.11, property-create BR-19). Nothing
 * here is stored, so a mark, a hold or a lost sign-in shows at once.
 */
@Component
@RequiredArgsConstructor
public class IntakeReadiness {

    private static final String AVAILABILITY_DIFFERS = "AVAILABILITY_DIFFERS";

    private final OwnerProofDomainRepository proofs;
    private final InventoryReadRepository inventory;
    private final IntakeUnitMappingDomainRepository mappings;
    private final UserDirectoryPort users;

    public IntakeBlockView.Readiness view(IntakeState state) {
        InventoryIntake row = state.latestCompletedOrNull();
        if (row == null) {
            return new IntakeBlockView.Readiness(ReadinessState.NONE.name(), List.of(), null, List.of(),
                    ReadinessPolicy.MISSING_FOR_BROKERAGE);
        }
        ReadinessPolicy.Result result = ReadinessPolicy.of(facts(state, row));
        return new IntakeBlockView.Readiness(result.state().name(), result.reasons().stream().map(ReadinessReason::name).toList(),
                row.getSourceChecklistId(), units(state, row), ReadinessPolicy.MISSING_FOR_BROKERAGE);
    }

    public ReadinessPolicy.Result result(IntakeState state) {
        InventoryIntake row = state.latestCompletedOrNull();
        return row == null ? new ReadinessPolicy.Result(ReadinessState.NONE, List.of())
                : ReadinessPolicy.of(facts(state, row));
    }

    private ReadinessPolicy.Facts facts(IntakeState state, InventoryIntake row) {
        Set<String> marked = state.marks().stream().map(IntakeSource.MarkedRevision::revisionId).collect(Collectors.toSet());
        boolean onCurrentSource = state.source().map(IntakeSource::revisionId)
                .filter(row.getSourceChecklistId()::equals).isPresent();
        String reason = onCurrentSource ? state.source().map(IntakeSource::reasonOrNull).orElse(null) : null;
        String owner = inventory.property(row.getResultPropertyId())
                .map(InventoryReadRepository.PropertyFacts::ownerId).orElse(null);
        OwnerStandingFacts standing = owner == null ? new OwnerStandingFacts(false, -1) : users.ownerStanding(owner);
        return new ReadinessPolicy.Facts(state.lead().status(), marked.contains(row.getSourceChecklistId()),
                row.getIntentCycle() != state.lead().intentCycle(), "EVIDENCE_GONE".equals(reason),
                "EVIDENCE_HELD".equals(reason), proofStands(row), standing.eligible(),
                IntakeEvaluator.wholeLeadRestricted(state.suspensions()));
    }

    /** Spec 4.11: withdrawn, voided or made before the account's last loss of sign-in reads as not confirmed. */
    private boolean proofStands(InventoryIntake row) {
        Optional<OwnerProof> proof = row.getProofId() == null ? Optional.empty() : proofs.findById(row.getProofId());
        return proof.filter(OwnerProof::inForce)
                .filter(found -> found.getSignInLosses() == users.ownerStanding(found.getUserId()).signInLosses()).isPresent();
    }

    private List<IntakeBlockView.ReadinessUnit> units(IntakeState state, InventoryIntake row) {
        List<UnitMapping> mapped = mappings.findByIntakeId(row.getId());
        Map<String, InventoryReadRepository.UnitFacts> units = inventory.units(mapped.stream().map(UnitMapping::unitId).toList())
                .stream().collect(Collectors.toMap(InventoryReadRepository.UnitFacts::id, Function.identity()));
        Set<String> restricted = state.suspensions().stream().filter(IntakeSuspension::isActive)
                .map(IntakeSuspension::getUnitIdOrNull).filter(Objects::nonNull).collect(Collectors.toSet());
        boolean link = row.getTarget() != null && row.getTarget().mode() == TargetMode.LINK;
        List<IntakeBlockView.ReadinessUnit> views = new ArrayList<>();
        for (UnitMapping mapping : mapped) {
            InventoryReadRepository.UnitFacts unit = units.get(mapping.unitId());
            List<String> reasons = new ArrayList<>();
            if (restricted.contains(mapping.unitId())) {
                reasons.add(ReadinessReason.RESTRICTED.name());
            }
            if (link && unit != null && differs(row, mapping, unit)) {
                reasons.add(AVAILABILITY_DIFFERS);
            }
            views.add(new IntakeBlockView.ReadinessUnit(mapping.unitId(), unit == null ? null : unit.code(), reasons.isEmpty(),
                    List.copyOf(reasons)));
        }
        return List.copyOf(views);
    }

    /** spec 4.9: on a link nothing is written to the unit; a difference shows here. */
    private static boolean differs(InventoryIntake row, UnitMapping mapping, InventoryReadRepository.UnitFacts unit) {
        return row.getAvailability().stream().filter(item -> item.spaceId().equals(mapping.spaceId())).findFirst()
                .map(AvailabilityItem::state).map(state -> !state.unitState().name().equals(unit.availabilityState()))
                .orElse(false);
    }
}
