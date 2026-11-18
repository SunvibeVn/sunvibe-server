package vn.sunvibe.property.core.application.service.intake;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeLead;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeLeadPort;
import vn.sunvibe.property.core.application.shared.external.survey.IntakeSource;
import vn.sunvibe.property.core.application.shared.external.survey.SourceStandingFacts;
import vn.sunvibe.property.core.application.shared.external.survey.SourceStandingPort;
import vn.sunvibe.property.core.application.shared.external.user.OwnerStandingFacts;
import vn.sunvibe.property.core.application.shared.external.user.OwnerStandingPort;
import vn.sunvibe.property.core.application.usecase.intake.IntakeBlockView;
import vn.sunvibe.property.core.application.usecase.intake.UnitIntakeUseCase;
import vn.sunvibe.property.core.application.usecase.intake.UnitIntakeView;
import vn.sunvibe.property.core.domain.model.intake.CaseOutcome;
import vn.sunvibe.property.core.domain.model.intake.CaseStatus;
import vn.sunvibe.property.core.domain.model.intake.DuplicateCase;
import vn.sunvibe.property.core.domain.model.intake.IntakeSuspension;
import vn.sunvibe.property.core.domain.model.intake.InventoryIntake;
import vn.sunvibe.property.core.domain.model.intake.OwnerProof;
import vn.sunvibe.property.core.domain.model.intake.UnitMapping;
import vn.sunvibe.property.core.domain.policy.intake.CasePolicy;
import vn.sunvibe.property.core.domain.policy.intake.CurrentRowPolicy;
import vn.sunvibe.property.core.domain.policy.intake.ReadinessPolicy;
import vn.sunvibe.property.core.domain.policy.intake.UnitStopPolicy;
import vn.sunvibe.property.core.domain.repository.DuplicateCaseDomainRepository;
import vn.sunvibe.property.core.domain.repository.IntakeSuspensionDomainRepository;
import vn.sunvibe.property.core.domain.repository.IntakeUnitMappingDomainRepository;
import vn.sunvibe.property.core.domain.repository.InventoryIntakeDomainRepository;
import vn.sunvibe.property.core.domain.repository.InventoryReadRepository;
import vn.sunvibe.property.core.domain.repository.OwnerProofDomainRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Use Case Implementation - read 1 of ADR-012 decision 2: the intakes, readiness and availability of a set of units
 * (S6b-D1, S6b-D2). It loads, for the whole set, the tables the readiness of one lead reads, each step one statement or
 * one port call and skipped when its input is empty, and computes each lead's readiness with the static methods of
 * {@link IntakeReadiness} and {@link IntakeDisputes}, so the rule has one place (S6b-D6). One reading of the clock;
 * nothing is written and nothing is logged.
 */
@Service
@RequiredArgsConstructor
public class UnitIntakeService implements UnitIntakeUseCase {

    private static final String MISSING_LEAD = "An intake names a lead that does not exist.";
    private static final String MISSING_ANSWER = "A set read left out an id it was asked for.";

    private final InventoryReadRepository inventory;
    private final IntakeUnitMappingDomainRepository mappings;
    private final InventoryIntakeDomainRepository intakes;
    private final IntakeLeadPort leads;
    private final SourceStandingPort sourceStandings;
    private final IntakeSuspensionDomainRepository suspensions;
    private final OwnerProofDomainRepository proofs;
    private final OwnerStandingPort ownerStandings;
    private final DuplicateCaseDomainRepository cases;

    @Override
    @Transactional(readOnly = true)
    public List<UnitIntakeView> forUnits(Collection<String> unitIds) {
        List<String> ids = IntakeCommandSupport.boundedIds(unitIds, MAX_UNITS, "At most 250 units a call.");
        if (ids.isEmpty()) {
            return List.of();
        }
        Instant now = IntakeCommandSupport.now();
        // 1. the units; 2. the mappings that name them (E05S6b-P2); 3. the rows of those mappings
        Map<String, InventoryReadRepository.UnitFacts> unitsById = byKey(inventory.units(ids),
                InventoryReadRepository.UnitFacts::id);
        List<UnitMapping> mapped = mappings.findByUnitIds(unitsById.keySet());
        List<InventoryIntake> mappedRows = intakes.findByIds(distinct(mapped, UnitMapping::intakeId));
        // 4. every row of those leads; the latest COMPLETED row of each, kept when a mapping names it (S6b-D1, S1-D2)
        Set<String> mappedIntakeIds = mapped.stream().map(UnitMapping::intakeId).collect(Collectors.toSet());
        List<InventoryIntake> latestRows = intakes.findByLeadIds(distinct(mappedRows, InventoryIntake::getLeadId)).stream()
                .collect(Collectors.groupingBy(InventoryIntake::getLeadId, TreeMap::new, Collectors.toList()))
                .values().stream().map(CurrentRowPolicy::latestCompleted).flatMap(Optional::stream)
                .filter(row -> mappedIntakeIds.contains(row.getId())).toList();
        // 5. every COMPLETED row on the properties of those rows, of any lead (brokerage-agreement BR-3, SV-215)
        Map<String, List<InventoryIntake>> completedByProperty = intakes.findCompletedByResults(
                        distinct(latestRows, InventoryIntake::getResultPropertyId)).stream()
                .collect(Collectors.groupingBy(InventoryIntake::getResultPropertyId, LinkedHashMap::new, Collectors.toList()));
        List<InventoryIntake> completed = completedByProperty.values().stream().flatMap(List::stream).toList();
        Set<String> leadIds = new TreeSet<>(completed.stream().map(InventoryIntake::getLeadId).toList());
        // 6. the leads, a missing one failing the read closed (S6b-D11); 7. their sources (read 2); 8. their suspensions
        Map<String, IntakeLead> leadsById = leadIds.isEmpty() ? Map.of() : leads.systemFacts(leadIds);
        if (!leadsById.keySet().containsAll(leadIds)) {
            throw new IllegalStateException(MISSING_LEAD);
        }
        Map<String, Integer> cycles = new TreeMap<>();
        leadIds.forEach(leadId -> cycles.put(leadId, leadsById.get(leadId).intentCycle()));
        Map<String, SourceStandingFacts> sources = cycles.isEmpty() ? Map.of() : sourceStandings.standings(cycles);
        Map<String, List<IntakeSuspension>> suspensionsByLead = suspensions.findByLeadIds(leadIds).stream()
                .collect(Collectors.groupingBy(IntakeSuspension::getLeadId));
        // 9. the proofs the rows name; 10. the properties; 11. the standing of their owners and of the proofs' accounts
        Map<String, OwnerProof> proofById = byKey(proofs.findByIds(completed.stream().map(InventoryIntake::getProofId)
                .filter(Objects::nonNull).distinct().toList()), OwnerProof::getId);
        Map<String, InventoryReadRepository.PropertyFacts> propertiesById = byKey(
                inventory.properties(completedByProperty.keySet()), InventoryReadRepository.PropertyFacts::id);
        Set<String> userIds = new TreeSet<>();
        propertiesById.values().stream().map(InventoryReadRepository.PropertyFacts::ownerId).filter(Objects::nonNull)
                .forEach(userIds::add);
        proofById.values().forEach(proof -> userIds.add(proof.getUserId()));
        Map<String, OwnerStandingFacts> standings = userIds.isEmpty() ? Map.of()
                : ownerStandings.ownerStandings(List.copyOf(userIds));
        // 12. the OWNER holders valid today of the properties with an owner (brokerage-agreement BR-3 (e))
        Map<String, List<String>> ownersByProperty = inventory.currentOwnerIdsOf(propertiesById.values().stream()
                        .filter(property -> property.ownerId() != null).map(InventoryReadRepository.PropertyFacts::id).toList(),
                IntakeCommandSupport.today(now));
        // 13. the dispute rows, as IntakeDisputes reads them per property (E05S6b-P1, P9)
        Disputes disputes = disputes(completedByProperty);

        Map<String, List<UnitIntakeView.IntakeEntry>> entriesByUnit = new HashMap<>();
        Map<String, Set<String>> unitsByIntake = IntakeReadiness.unitsByIntake(mapped);
        for (InventoryIntake row : latestRows) {
            String propertyId = row.getResultPropertyId();
            IntakeLead lead = leadsById.get(row.getLeadId());
            SourceStandingFacts source = answered(sources, lead.leadId());
            InventoryReadRepository.PropertyFacts property = propertiesById.get(propertyId);
            String owner = property == null ? null : property.ownerId();
            OwnerStandingFacts standing = owner == null ? new OwnerStandingFacts(false, -1) : answered(standings, owner);
            List<IntakeSuspension> leadSuspensions = suspensionsByLead.getOrDefault(lead.leadId(), List.of());
            boolean stands = IntakeReadiness.proofStands(proofOf(row, proofById),
                    userId -> answered(standings, userId).signInLosses());
            ReadinessPolicy.Result result = ReadinessPolicy.of(IntakeReadiness.readinessFacts(lead, source.revisionIdOrNull(),
                    source.reasonOrNull(), source.markedRevisions(), leadSuspensions, row, stands, standing.eligible()));
            List<InventoryIntake> onProperty = completedByProperty.get(propertyId);
            Map<String, OwnerProof> proofByRow = new HashMap<>();
            onProperty.forEach(completedRow -> proofByRow.put(completedRow.getId(), proofOf(completedRow, proofById)));
            Map<String, List<IntakeSource.MarkedRevision>> marksByLead = new HashMap<>();
            IntakeReadiness.otherLeadIds(onProperty, lead.leadId())
                    .forEach(leadId -> marksByLead.put(leadId, answered(sources, leadId).markedRevisions()));
            List<DuplicateCase> naming = disputes.namingByProperty().getOrDefault(propertyId, List.of());
            boolean holdsAlone = IntakeReadiness.ownerHoldsAlone(owner,
                    () -> ownersByProperty.getOrDefault(propertyId, List.of()));
            boolean disputed = IntakeDisputes.ownerDisputedOf(disputes.casesNamingByProperty().get(propertyId),
                    disputes::intake, disputes::proofsOfLead, owner);
            boolean ruledOut = IntakeDisputes.resultRuledOutOf(naming, () -> onProperty, disputes::intake, lead.leadId());
            UnitStopPolicy.Facts stops = IntakeReadiness.stopFacts(lead.leadId(), owner, standing.eligible(), onProperty,
                    unitsByIntake, proofByRow, marksByLead, suspensionsByLead, holdsAlone, disputed, ruledOut);
            List<UnitMapping> rowMappings = mapped.stream().filter(mapping -> mapping.intakeId().equals(row.getId())).toList();
            for (IntakeBlockView.ReadinessUnit unit : IntakeReadiness.readinessUnits(row, rowMappings, unitsById,
                    leadSuspensions, stops)) {
                entriesByUnit.computeIfAbsent(unit.unitId(), id -> new ArrayList<>()).add(new UnitIntakeView.IntakeEntry(
                        row.getId(), result.state().name(), row.getSourceChecklistId(), unit.reasons()));
            }
        }
        return unitsById.values().stream().sorted(Comparator.comparing(InventoryReadRepository.UnitFacts::id))
                .map(unit -> new UnitIntakeView(unit.id(), unit.code(), unit.propertyId(), unit.availabilityState(),
                        entriesByUnit.getOrDefault(unit.id(), List.of()).stream()
                                .sorted(Comparator.comparing(UnitIntakeView.IntakeEntry::intakeId)).toList()))
                .toList();
    }

    /**
     * The dispute rows of the properties, loaded as {@link IntakeDisputes} reads them for one: the cases naming each
     * property, the cases of its COMPLETED results, which a result won (brokerage-agreement BR-3 (c) through a winner's
     * case, E05S6b-P9); the intakes a decided SAME_ASSET case of either list waits on and those a DIFFERENT_ASSET ruling
     * of the naming list names; and the proofs of the waiting intakes' leads.
     */
    private Disputes disputes(Map<String, List<InventoryIntake>> completedByProperty) {
        Map<String, List<DuplicateCase>> namingByProperty = cases.findNamingProperties(completedByProperty.keySet()).stream()
                .collect(Collectors.groupingBy(DuplicateCase::getCandidatePropertyId));
        // a case is filed under both of its intakes, so the lookup of one answers what findForIntake answers for it
        Map<String, List<DuplicateCase>> casesByIntake = new HashMap<>();
        for (DuplicateCase item : cases.findForIntakes(completedByProperty.values().stream().flatMap(List::stream)
                .map(InventoryIntake::getId).toList())) {
            casesByIntake.computeIfAbsent(item.getIntakeId(), id -> new ArrayList<>()).add(item);
            if (item.getCandidateIntakeId() != null) {
                casesByIntake.computeIfAbsent(item.getCandidateIntakeId(), id -> new ArrayList<>()).add(item);
            }
        }
        Map<String, List<DuplicateCase>> casesNamingByProperty = new HashMap<>();
        Set<String> waiting = new TreeSet<>();
        Set<String> ruled = new TreeSet<>();
        completedByProperty.forEach((propertyId, onProperty) -> {
            List<DuplicateCase> naming = namingByProperty.getOrDefault(propertyId, List.of());
            List<DuplicateCase> casesNaming = IntakeDisputes.casesNamingOf(naming, onProperty,
                    intakeId -> casesByIntake.getOrDefault(intakeId, List.of()));
            casesNamingByProperty.put(propertyId, casesNaming);
            casesNaming.stream().filter(item -> decided(item, CaseOutcome.SAME_ASSET))
                    .forEach(item -> waiting.add(CasePolicy.waitingIntake(item)));
            naming.stream().filter(item -> decided(item, CaseOutcome.DIFFERENT_ASSET))
                    .forEach(item -> ruled.add(item.getIntakeId()));
        });
        Set<String> wanted = new TreeSet<>(waiting);
        wanted.addAll(ruled);
        Map<String, InventoryIntake> intakesById = byKey(intakes.findByIds(wanted), InventoryIntake::getId);
        Map<String, List<OwnerProof>> proofsByLead = proofs.findByLeadIds(waiting.stream().map(intakesById::get)
                        .filter(Objects::nonNull).map(InventoryIntake::getLeadId).distinct().toList()).stream()
                .collect(Collectors.groupingBy(proof -> proof.getBinding().leadId()));
        return new Disputes(namingByProperty, casesNamingByProperty, intakesById, proofsByLead);
    }

    private record Disputes(Map<String, List<DuplicateCase>> namingByProperty,
                            Map<String, List<DuplicateCase>> casesNamingByProperty,
                            Map<String, InventoryIntake> intakesById, Map<String, List<OwnerProof>> proofsByLead) {

        Optional<InventoryIntake> intake(String intakeId) {
            return Optional.ofNullable(intakesById.get(intakeId));
        }

        List<OwnerProof> proofsOfLead(String leadId) {
            return proofsByLead.getOrDefault(leadId, List.of());
        }
    }

    private static boolean decided(DuplicateCase item, CaseOutcome outcome) {
        return item.getStatus() == CaseStatus.DECIDED && item.getOutcome() == outcome;
    }

    private static OwnerProof proofOf(InventoryIntake row, Map<String, OwnerProof> proofById) {
        return row.getProofId() == null ? null : proofById.get(row.getProofId());
    }

    /** A set read answers every id it is asked for; one left out would read as fewer stops, so the read fails closed. */
    private static <V> V answered(Map<String, V> answers, String id) {
        V answer = answers.get(id);
        if (answer == null) {
            throw new IllegalStateException(MISSING_ANSWER);
        }
        return answer;
    }

    private static <T> List<String> distinct(Collection<T> items, Function<T, String> key) {
        return items.stream().map(key).filter(Objects::nonNull).distinct().toList();
    }

    private static <T> Map<String, T> byKey(Collection<T> items, Function<T, String> key) {
        Map<String, T> byKey = new LinkedHashMap<>();
        items.forEach(item -> byKey.put(key.apply(item), item));
        return byKey;
    }
}
