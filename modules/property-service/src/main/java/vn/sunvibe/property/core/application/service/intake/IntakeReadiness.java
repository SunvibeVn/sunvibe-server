package vn.sunvibe.property.core.application.service.intake;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeLead;
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
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
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
 * <p>
 * One rule, two loaders (E05-S6b, S6b-D6): the instance methods read one lead's rows, in a fixed order, and hand them to the
 * package-private static methods, which turn loaded rows into the readiness; the set read of units loads the same tables
 * for many leads at once and calls the same static methods.
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
        return readiness(result, row, units(state, row, mapped, stops));
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
        boolean stands = proofStands(proofOf(row), userId -> users.ownerStanding(userId).signInLosses());
        return readinessFacts(state.lead(), state.source().map(IntakeSource::revisionId).orElse(null),
                state.source().map(IntakeSource::reasonOrNull).orElse(null), state.marks(), state.suspensions(), row, stands,
                standing.eligible());
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
        Map<String, Set<String>> unitsByIntake = unitsByIntake(mappings.findByUnitIds(mapped.stream()
                .map(UnitMapping::unitId).toList()));
        Map<String, OwnerProof> proofByRow = new HashMap<>();
        onProperty.forEach(result -> proofByRow.put(result.getId(), proofOf(result)));
        Map<String, List<IntakeSource.MarkedRevision>> marksByLead = new HashMap<>();
        Map<String, List<IntakeSuspension>> suspensionsByLead = new HashMap<>();
        for (String leadId : otherLeadIds(onProperty, self)) {
            marksByLead.put(leadId, sources.markedRevisions(leadId));
            suspensionsByLead.put(leadId, suspensions.findByLeadId(leadId));
        }
        boolean holdsAlone = ownerHoldsAlone(owner,
                () -> inventory.currentOwnerIds(propertyId, IntakeCommandSupport.today(now)));
        boolean disputed = disputes.ownerDisputed(propertyId, owner);
        boolean ruledOut = disputes.resultRuledOut(propertyId, self);
        return stopFacts(self, owner, standing.eligible(), onProperty, unitsByIntake, proofByRow, marksByLead,
                suspensionsByLead, holdsAlone, disputed, ruledOut);
    }

    private OwnerProof proofOf(InventoryIntake row) {
        return row.getProofId() == null ? null : proofs.findById(row.getProofId()).orElse(null);
    }

    private List<IntakeBlockView.ReadinessUnit> units(IntakeState state, InventoryIntake row, List<UnitMapping> mapped,
                                                      UnitStopPolicy.Facts stops) {
        Map<String, InventoryReadRepository.UnitFacts> units = inventory.units(mapped.stream().map(UnitMapping::unitId).toList())
                .stream().collect(Collectors.toMap(InventoryReadRepository.UnitFacts::id, Function.identity()));
        return readinessUnits(row, mapped, units, state.suspensions(), stops);
    }

    // ==================== the rule, fed by loaded rows (S6b-D6) ====================

    /**
     * The facts of the lead-level state of a result (property-create intake spec 4.11): the lead's status, a mark on the
     * result's source, a moved intent cycle, the lost or held evidence of the current source when the result stands on it,
     * the proof, the owner's eligibility and a whole-lead suspension.
     *
     * @param currentRevisionIdOrNull the revision of the lead's current source; null when it has none
     * @param currentReasonOrNull     why that source is not usable; null when it is or when there is none
     * @param proofStands             {@link #proofStands} of the proof the result names
     */
    static ReadinessPolicy.Facts readinessFacts(IntakeLead lead, String currentRevisionIdOrNull, String currentReasonOrNull,
                                                List<IntakeSource.MarkedRevision> marks,
                                                List<IntakeSuspension> leadSuspensions, InventoryIntake row,
                                                boolean proofStands, boolean ownerEligible) {
        Set<String> marked = marks.stream().map(IntakeSource.MarkedRevision::revisionId).collect(Collectors.toSet());
        boolean onCurrentSource = row.getSourceChecklistId().equals(currentRevisionIdOrNull);
        String reason = onCurrentSource ? currentReasonOrNull : null;
        return new ReadinessPolicy.Facts(lead.status(), marked.contains(row.getSourceChecklistId()),
                row.getIntentCycle() != lead.intentCycle(), "EVIDENCE_GONE".equals(reason),
                "EVIDENCE_HELD".equals(reason), proofStands, ownerEligible,
                IntakeEvaluator.wholeLeadRestricted(leadSuspensions));
    }

    /**
     * Spec 4.11: withdrawn, voided or made before the account's last loss of sign-in reads as not confirmed. The account's
     * losses are asked for a proof in force only.
     */
    static boolean proofStands(OwnerProof proofOrNull, ToIntFunction<String> signInLossesOf) {
        return Optional.ofNullable(proofOrNull).filter(OwnerProof::inForce)
                .filter(found -> found.getSignInLosses() == signInLossesOf.applyAsInt(found.getUserId())).isPresent();
    }

    /** The other leads with a COMPLETED result on the property, each once, sorted. */
    static List<String> otherLeadIds(List<InventoryIntake> onProperty, String selfLeadId) {
        return onProperty.stream().map(InventoryIntake::getLeadId).filter(leadId -> !leadId.equals(selfLeadId))
                .distinct().sorted().toList();
    }

    /** The units each intake maps, among the mappings given. */
    static Map<String, Set<String>> unitsByIntake(List<UnitMapping> mappings) {
        return mappings.stream().collect(Collectors.groupingBy(UnitMapping::intakeId,
                Collectors.mapping(UnitMapping::unitId, Collectors.toSet())));
    }

    /**
     * brokerage-agreement BR-3 (e): the OWNER rows valid today name the property's owner and nobody else. They are read
     * only when the property has an owner.
     */
    static boolean ownerHoldsAlone(String ownerOrNull, Supplier<List<String>> currentOwnerIds) {
        return ownerOrNull != null && currentOwnerIds.get().equals(List.of(ownerOrNull));
    }

    /**
     * brokerage-agreement BR-3, BR-18 (SV-215): the facts of the stops from the loaded rows of the property: its COMPLETED
     * results, the units they map, the proof each names (or null), and each other lead's marks and suspensions.
     */
    static UnitStopPolicy.Facts stopFacts(String selfLeadId, String ownerOrNull, boolean ownerEligible,
                                          List<InventoryIntake> onProperty, Map<String, Set<String>> unitsByIntake,
                                          Map<String, OwnerProof> proofByRow,
                                          Map<String, List<IntakeSource.MarkedRevision>> marksByLead,
                                          Map<String, List<IntakeSuspension>> suspensionsByLead, boolean ownerHoldsAlone,
                                          boolean ownerDisputed, boolean resultRuledOut) {
        List<UnitStopPolicy.Completion> completions = new ArrayList<>();
        onProperty.stream().filter(result -> result.getLeadId().equals(selfLeadId)).forEach(result -> completions.add(
                new UnitStopPolicy.Completion(false, unitsByIntake.getOrDefault(result.getId(), Set.of()),
                        result.getCompletedAt(), false, null,
                        UnitStopPolicy.ownerConfirmedAt(proofByRow.get(result.getId()), ownerOrNull))));
        List<UnitStopPolicy.OtherLead> otherLeads = new ArrayList<>();
        for (String leadId : otherLeadIds(onProperty, selfLeadId)) {
            List<IntakeSource.MarkedRevision> marks = marksByLead.getOrDefault(leadId, List.of());
            List<IntakeSuspension> ofLead = suspensionsByLead.getOrDefault(leadId, List.of());
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
                        UnitStopPolicy.ownerConfirmedAt(proofByRow.get(result.getId()), ownerOrNull)));
            }
        }
        return new UnitStopPolicy.Facts(ownerEligible, ownerHoldsAlone, ownerDisputed, resultRuledOut, otherLeads,
                completions);
    }

    /**
     * The units of a result with their reasons: this lead's own unit suspension, then a difference of availability on a
     * link, then the stops of brokerage-agreement BR-3 from another lead or the property.
     *
     * @param mapped    the mappings of the result whose units are answered
     * @param unitsById the stored units, by id; a unit missing here has no code and no availability difference
     */
    static List<IntakeBlockView.ReadinessUnit> readinessUnits(InventoryIntake row, List<UnitMapping> mapped,
                                                              Map<String, InventoryReadRepository.UnitFacts> unitsById,
                                                              List<IntakeSuspension> leadSuspensions,
                                                              UnitStopPolicy.Facts stops) {
        Set<String> restricted = suspendedUnits(leadSuspensions);
        boolean link = row.getTarget() != null && row.getTarget().mode() == TargetMode.LINK;
        List<IntakeBlockView.ReadinessUnit> views = new ArrayList<>();
        for (UnitMapping mapping : mapped) {
            InventoryReadRepository.UnitFacts unit = unitsById.get(mapping.unitId());
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

    /** The readiness of a result as the block and the published read show it. */
    static IntakeBlockView.Readiness readiness(ReadinessPolicy.Result result, InventoryIntake row,
                                               List<IntakeBlockView.ReadinessUnit> units) {
        return new IntakeBlockView.Readiness(result.state().name(), result.reasons().stream().map(ReadinessReason::name).toList(),
                row.getSourceChecklistId(), units, ReadinessPolicy.MISSING_FOR_BROKERAGE);
    }

    private static Set<String> suspendedUnits(List<IntakeSuspension> ofLead) {
        return ofLead.stream().filter(IntakeSuspension::isActive).map(IntakeSuspension::getUnitIdOrNull)
                .filter(Objects::nonNull).collect(Collectors.toSet());
    }

    /** property-create intake spec 4.9: on a link nothing is written to the unit; a difference shows here. */
    private static boolean differs(InventoryIntake row, UnitMapping mapping, InventoryReadRepository.UnitFacts unit) {
        return row.getAvailability().stream().filter(item -> item.spaceId().equals(mapping.spaceId())).findFirst()
                .map(AvailabilityItem::state).map(state -> !state.unitState().name().equals(unit.availabilityState()))
                .orElse(false);
    }
}
