package vn.sunvibe.property.core.application.service.intake;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.property.core.application.shared.external.survey.IntakeSource;
import vn.sunvibe.property.core.application.shared.external.survey.IntakeSourcePort;
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
import vn.sunvibe.property.core.domain.policy.intake.UnitStopPolicy;
import vn.sunvibe.property.core.domain.repository.IntakeSuspensionDomainRepository;
import vn.sunvibe.property.core.domain.repository.IntakeUnitMappingDomainRepository;
import vn.sunvibe.property.core.domain.repository.InventoryIntakeDomainRepository;
import vn.sunvibe.property.core.domain.repository.InventoryReadRepository;
import vn.sunvibe.property.core.domain.repository.OwnerProofDomainRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
 * <p>
 * The units also carry the stops of brokerage-agreement BR-3 that come from another lead with a result on the same
 * property or from the property itself (BR-18, SV-215), as the groups PROPERTY_STOP and UNIT_STOP, which name no lead.
 * They are read from the rows that record them, never from another lead's computed state: another lead's hold or closure
 * neither starts nor ends one, and an owner's withdrawal ends when the property's owner confirms again on a completed
 * result of the property.
 */
@Component
@RequiredArgsConstructor
public class IntakeReadiness {

    private static final String AVAILABILITY_DIFFERS = "AVAILABILITY_DIFFERS";

    private final OwnerProofDomainRepository proofs;
    private final InventoryReadRepository inventory;
    private final IntakeUnitMappingDomainRepository mappings;
    private final UserDirectoryPort users;
    private final IntakeSourcePort sources;
    private final IntakeSuspensionDomainRepository suspensions;
    private final InventoryIntakeDomainRepository intakes;
    private final IntakeDisputes disputes;

    public IntakeBlockView.Readiness view(IntakeState state, Instant now) {
        InventoryIntake row = state.latestCompletedOrNull();
        if (row == null) {
            return new IntakeBlockView.Readiness(ReadinessState.NONE.name(), List.of(), null, List.of(),
                    ReadinessPolicy.MISSING_FOR_BROKERAGE);
        }
        String owner = ownerOf(row);
        OwnerStandingFacts standing = standing(owner);
        ReadinessPolicy.Result result = ReadinessPolicy.of(facts(state, row, standing));
        List<UnitMapping> mapped = mappings.findByIntakeId(row.getId());
        UnitStopPolicy.Facts stops = stops(state, row, mapped, owner, standing, now);
        return new IntakeBlockView.Readiness(result.state().name(), result.reasons().stream().map(ReadinessReason::name).toList(),
                row.getSourceChecklistId(), units(state, row, mapped, stops), ReadinessPolicy.MISSING_FOR_BROKERAGE);
    }

    public ReadinessPolicy.Result result(IntakeState state) {
        InventoryIntake row = state.latestCompletedOrNull();
        return row == null ? new ReadinessPolicy.Result(ReadinessState.NONE, List.of())
                : ReadinessPolicy.of(facts(state, row, standing(ownerOf(row))));
    }

    private String ownerOf(InventoryIntake row) {
        return inventory.property(row.getResultPropertyId()).map(InventoryReadRepository.PropertyFacts::ownerId).orElse(null);
    }

    private OwnerStandingFacts standing(String ownerOrNull) {
        return ownerOrNull == null ? new OwnerStandingFacts(false, -1) : users.ownerStanding(ownerOrNull);
    }

    private ReadinessPolicy.Facts facts(IntakeState state, InventoryIntake row, OwnerStandingFacts standing) {
        Set<String> marked = state.marks().stream().map(IntakeSource.MarkedRevision::revisionId).collect(Collectors.toSet());
        boolean onCurrentSource = state.source().map(IntakeSource::revisionId)
                .filter(row.getSourceChecklistId()::equals).isPresent();
        String reason = onCurrentSource ? state.source().map(IntakeSource::reasonOrNull).orElse(null) : null;
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

    /**
     * brokerage-agreement BR-3, BR-18 (SV-215): the facts the stops of the property and of this result's units are read
     * from. Every COMPLETED result on the property counts, of any lead and whatever that lead's status; the other leads'
     * marks and suspensions are read from their rows, and no other lead's readiness is computed. Each such lead costs the
     * read of its marks and of its suspensions, and each result on the property one read of the proof it names.
     */
    private UnitStopPolicy.Facts stops(IntakeState state, InventoryIntake row, List<UnitMapping> mapped, String owner,
                                       OwnerStandingFacts standing, Instant now) {
        String propertyId = row.getResultPropertyId();
        String self = state.lead().leadId();
        List<InventoryIntake> onProperty = intakes.findCompletedByResult(propertyId);
        Map<String, Set<String>> unitsByIntake = mappings.findByUnitIds(mapped.stream().map(UnitMapping::unitId).toList())
                .stream().collect(Collectors.groupingBy(UnitMapping::intakeId,
                        Collectors.mapping(UnitMapping::unitId, Collectors.toSet())));
        Map<String, OwnerProof> proofByRow = new HashMap<>();
        onProperty.forEach(result -> proofByRow.put(result.getId(), proofOf(result)));
        List<UnitStopPolicy.Completion> completions = new ArrayList<>();
        onProperty.stream().filter(result -> result.getLeadId().equals(self)).forEach(result -> completions.add(
                new UnitStopPolicy.Completion(false, unitsByIntake.getOrDefault(result.getId(), Set.of()),
                        result.getCompletedAt(), false, null,
                        UnitStopPolicy.ownerConfirmedAt(proofByRow.get(result.getId()), owner))));
        List<String> otherLeadIds = onProperty.stream().map(InventoryIntake::getLeadId).filter(leadId -> !leadId.equals(self))
                .distinct().sorted().toList();
        List<UnitStopPolicy.OtherLead> otherLeads = new ArrayList<>();
        for (String leadId : otherLeadIds) {
            List<IntakeSource.MarkedRevision> marks = sources.markedRevisions(leadId);
            List<IntakeSuspension> ofLead = suspensions.findByLeadId(leadId);
            List<InventoryIntake> results = onProperty.stream().filter(result -> result.getLeadId().equals(leadId)).toList();
            InventoryIntake latest = results.stream().max(Comparator.comparing(InventoryIntake::getCompletedAt)
                    .thenComparing(InventoryIntake::getId)).orElseThrow();
            OwnerProof latestProof = proofByRow.get(latest.getId());
            otherLeads.add(new UnitStopPolicy.OtherLead(IntakeEvaluator.wholeLeadRestricted(ofLead),
                    latestProof == null ? null : latestProof.getWithdrawnAt(), suspendedUnits(ofLead)));
            for (InventoryIntake result : results) {
                Optional<IntakeSource.MarkedRevision> mark = marks.stream()
                        .filter(item -> item.revisionId().equals(result.getSourceChecklistId())).findFirst();
                completions.add(new UnitStopPolicy.Completion(true, unitsByIntake.getOrDefault(result.getId(), Set.of()),
                        result.getCompletedAt(), mark.map(item -> IntakeEvaluator.reconciliationKind(item.kind())).orElse(false),
                        mark.map(IntakeSource.MarkedRevision::markedAt).orElse(null),
                        UnitStopPolicy.ownerConfirmedAt(proofByRow.get(result.getId()), owner)));
            }
        }
        boolean holdsAlone = owner != null
                && inventory.currentOwnerIds(propertyId, IntakeCommandSupport.today(now)).equals(List.of(owner));
        return new UnitStopPolicy.Facts(standing.eligible(), holdsAlone, disputes.ownerDisputed(propertyId, owner),
                disputes.resultRuledOut(propertyId, self), otherLeads, completions);
    }

    private OwnerProof proofOf(InventoryIntake row) {
        return row.getProofId() == null ? null : proofs.findById(row.getProofId()).orElse(null);
    }

    private static Set<String> suspendedUnits(List<IntakeSuspension> ofLead) {
        return ofLead.stream().filter(IntakeSuspension::isActive).map(IntakeSuspension::getUnitIdOrNull)
                .filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private List<IntakeBlockView.ReadinessUnit> units(IntakeState state, InventoryIntake row, List<UnitMapping> mapped,
                                                      UnitStopPolicy.Facts stops) {
        Map<String, InventoryReadRepository.UnitFacts> units = inventory.units(mapped.stream().map(UnitMapping::unitId).toList())
                .stream().collect(Collectors.toMap(InventoryReadRepository.UnitFacts::id, Function.identity()));
        Set<String> restricted = suspendedUnits(state.suspensions());
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
            // brokerage-agreement BR-3, BR-18: the stops of another lead or of the property, after this lead's own reasons
            UnitStopPolicy.stops(mapping.unitId(), stops).forEach(stop -> reasons.add(stop.name()));
            views.add(new IntakeBlockView.ReadinessUnit(mapping.unitId(), unit == null ? null : unit.code(), reasons.isEmpty(),
                    List.copyOf(reasons)));
        }
        return List.copyOf(views);
    }

    /** property-create intake spec 4.9: on a link nothing is written to the unit; a difference shows here. */
    private static boolean differs(InventoryIntake row, UnitMapping mapping, InventoryReadRepository.UnitFacts unit) {
        return row.getAvailability().stream().filter(item -> item.spaceId().equals(mapping.spaceId())).findFirst()
                .map(AvailabilityItem::state).map(state -> !state.unitState().name().equals(unit.availabilityState()))
                .orElse(false);
    }
}
