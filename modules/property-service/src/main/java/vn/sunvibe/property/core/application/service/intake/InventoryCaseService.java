package vn.sunvibe.property.core.application.service.intake;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.property.core.application.exception.IntakeError;
import vn.sunvibe.property.core.application.exception.IntakeRefusedException;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeLead;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeLeadPort;
import vn.sunvibe.property.core.application.shared.external.survey.IntakeSource;
import vn.sunvibe.property.core.application.shared.external.survey.IntakeSourcePort;
import vn.sunvibe.property.core.application.usecase.intake.IntakeActor;
import vn.sunvibe.property.core.application.usecase.intake.InventoryCaseUseCase;
import vn.sunvibe.property.core.application.usecase.intake.InventoryCaseView;
import vn.sunvibe.property.core.application.usecase.intake.OwnerRequestSnapshot;
import vn.sunvibe.property.core.domain.model.intake.CaseOutcome;
import vn.sunvibe.property.core.domain.model.intake.CaseSignal;
import vn.sunvibe.property.core.domain.model.intake.CaseStatus;
import vn.sunvibe.property.core.domain.model.intake.DuplicateCase;
import vn.sunvibe.property.core.domain.model.intake.InventoryIntake;
import vn.sunvibe.property.core.domain.policy.intake.CasePolicy;
import vn.sunvibe.property.core.domain.policy.intake.CasePolicy.CaseFacts;
import vn.sunvibe.property.core.domain.policy.intake.CasePolicy.Reading;
import vn.sunvibe.property.core.domain.repository.DuplicateCaseDomainRepository;
import vn.sunvibe.property.core.domain.repository.IntakeTrailDomainRepository;
import vn.sunvibe.property.core.domain.repository.InventoryIntakeDomainRepository;
import vn.sunvibe.property.core.domain.repository.InventoryReadRepository;
import vn.sunvibe.property.core.domain.repository.InventoryReadRepository.PropertyFacts;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Use Case Implementation - cases (property-create intake spec 4.10, 5.4, S-22). A decision locks both leads, then both intakes,
 * in id order, and then the row of the case. Who owns what is read each time, never stored.
 */
@Service
@RequiredArgsConstructor
public class InventoryCaseService implements InventoryCaseUseCase {

    private static final int MAX_SIZE = 50;
    private static final int MAX_PAGE = 10_000;
    private static final String CASE_READ = "CASE_READ";
    private static final String BASIS_FIELD = "basis";

    private final DuplicateCaseDomainRepository cases;
    private final InventoryIntakeDomainRepository intakes;
    private final InventoryReadRepository inventory;
    private final IntakeLeadPort leads;
    private final IntakeSourcePort sources;
    private final IntakeEvaluator evaluator;
    private final IntakeCommandSupport support;
    private final IntakeWork work;
    private final IntakeTrailDomainRepository trail;

    @Override
    @Transactional(readOnly = true)
    public CasePage list(IntakeActor actor, String statusOrNull, int page, int size) {
        CaseStatus status = listStatus(statusOrNull);
        if (page < 0 || page > MAX_PAGE || size < 1 || size > MAX_SIZE) {
            throw refused("size");
        }
        List<CaseItem> visible = new ArrayList<>();
        Map<String, Boolean> workedByLead = new HashMap<>();
        for (DuplicateCase item : cases.findByStatus(status)) {
            boolean reads = status == CaseStatus.DECIDED || CasePolicy.read(evaluator.caseFacts(item)) == Reading.OPEN;
            if (reads && !workedEitherSide(item, actor, workedByLead)) {
                IntakeLead lead = leadOf(item.getIntakeId());
                visible.add(new CaseItem(item.getId(), lead.leadCode(), lead.wardName(), item.getCreatedAt()));
            }
        }
        int from = Math.min(page * size, visible.size());
        int to = Math.min(from + size, visible.size());
        return new CasePage(List.copyOf(visible.subList(from, to)), page, size, visible.size());
    }

    private static CaseStatus listStatus(String statusOrNull) {
        if (statusOrNull == null || CaseStatus.OPEN.name().equals(statusOrNull)) {
            return CaseStatus.OPEN;
        }
        if (CaseStatus.DECIDED.name().equals(statusOrNull)) {
            return CaseStatus.DECIDED;
        }
        throw refused("status");
    }

    /** Not read-only: opening a case writes an access log row (spec 4.12). */
    @Override
    @Transactional
    public InventoryCaseView read(String caseId, IntakeActor actor) {
        DuplicateCase item = found(caseId);
        requireNotWorked(item, actor);
        trail.recordAccess(actor.userId(), rowOf(item.getIntakeId()).getLeadId(), item.getIntakeId(), caseId, CASE_READ,
                IntakeCommandSupport.now());
        return view(item);
    }

    @Override
    @Transactional
    public InventoryCaseView decide(String caseId, IntakeActor actor, String outcome, String proceedingIntakeIdOrNull,
                                    String basis, Long expectedVersion) {
        String text = IntakeText.require(BASIS_FIELD, basis, IntakeText.MAX_REASON);
        CaseOutcome decision = outcome(outcome);
        DuplicateCase scalar = found(caseId);
        Instant now = IntakeCommandSupport.now();
        // The leads load under their locks first: a read before them would stay in the persistence context, and the decision
        // would run on what the lead was before the command it waited for
        List<IntakeLead> locked = lockSides(scalar, actor);
        requireNotWorked(scalar, actor);
        DuplicateCase item = cases.lockById(caseId).orElseThrow(() -> new IntakeRefusedException(IntakeError.NOT_FOUND));
        if (expectedVersion == null || expectedVersion != item.getVersion()) {
            throw new IntakeRefusedException(IntakeError.CONCURRENT_MODIFICATION);
        }
        if (CasePolicy.read(evaluator.caseFacts(item)) != Reading.OPEN) {
            throw new IntakeRefusedException(IntakeError.INTAKE_STATE);
        }
        requireProceeding(item, decision, proceedingIntakeIdOrNull);

        item.decide(decision, proceedingIntakeIdOrNull, text, actor.userId(), now);
        cases.update(item);
        requireOneForcedProperty(item);
        if (decision == CaseOutcome.DIFFERENT_ASSET && item.getSignal() == CaseSignal.LINK_PLACE) {
            clearTarget(item.getIntakeId(), now);
        }
        locked.forEach(lead -> support.settle(evaluator.read(lead, now), now));
        return view(item);
    }

    private static CaseOutcome outcome(String outcome) {
        if (CaseOutcome.SAME_ASSET.name().equals(outcome)) {
            return CaseOutcome.SAME_ASSET;
        }
        if (CaseOutcome.DIFFERENT_ASSET.name().equals(outcome)) {
            return CaseOutcome.DIFFERENT_ASSET;
        }
        throw refused("outcome");
    }

    /** spec 5.4: required only for SAME_ASSET on an intake candidate, and then one of the two sides. */
    private static void requireProceeding(DuplicateCase item, CaseOutcome decision, String proceedingOrNull) {
        boolean needed = decision == CaseOutcome.SAME_ASSET && !item.onProperty();
        boolean oneOfTheSides = item.getIntakeId().equals(proceedingOrNull) || Objects.equals(item.getCandidateIntakeId(),
                proceedingOrNull);
        if (needed ? !oneOfTheSides : proceedingOrNull != null) {
            throw refused("proceedingIntakeId");
        }
    }

    /** spec 4.10: a second same-user SAME_ASSET on another property would merge two assets, which is out of scope. */
    private void requireOneForcedProperty(DuplicateCase decided) {
        InventoryIntake row = rowOf(CasePolicy.waitingIntake(decided));
        if (decided.getOutcome() != CaseOutcome.SAME_ASSET) {
            return;
        }
        List<CaseFacts> forcing = evaluator.caseFacts(row).stream().filter(facts -> CasePolicy.read(facts) == Reading.FORCED)
                .filter(facts -> CasePolicy.waitingIntake(facts.row()).equals(row.getId())).toList();
        if (forcing.stream().map(CasePolicy::namedProperty).distinct().count() > 1) {
            throw new IntakeRefusedException(IntakeError.INTAKE_STATE);
        }
    }

    private void clearTarget(String intakeId, Instant now) {
        InventoryIntake row = rowOf(intakeId);
        row.clearTarget(now);
        intakes.update(row);
    }

    private List<IntakeLead> lockSides(DuplicateCase item, IntakeActor actor) {
        TreeSet<String> intakeIds = new TreeSet<>(sides(item).toList());
        TreeSet<String> leadIds = new TreeSet<>(intakeIds.stream().map(id -> rowOf(id).getLeadId()).toList());
        List<IntakeLead> locked = new ArrayList<>();
        for (String leadId : leadIds) {
            locked.add(support.lockVisibleLead(leadId, actor));
        }
        intakeIds.forEach(intakes::lockById);
        return locked;
    }

    private static Stream<String> sides(DuplicateCase item) {
        return Stream.of(item.getIntakeId(), item.getCandidateIntakeId()).filter(Objects::nonNull);
    }

    /** spec 4.3: either side's lead, a property candidate's origin intake included. */
    private boolean workedEitherSide(DuplicateCase item, IntakeActor actor, Map<String, Boolean> workedByLead) {
        Stream<String> origin = item.onProperty() ? inventory.property(item.getCandidatePropertyId())
                .map(PropertyFacts::intakeIdOrNull).stream() : Stream.empty();
        return Stream.concat(sides(item), origin).map(this::leadOf).anyMatch(lead -> workedByLead
                .computeIfAbsent(lead.leadId(), id -> work.hasWorked(actor.userId(), lead)));
    }

    private void requireNotWorked(DuplicateCase item, IntakeActor actor) {
        if (workedEitherSide(item, actor, new HashMap<>())) {
            throw new IntakeRefusedException(IntakeError.CASE_NOT_ALLOWED);
        }
    }

    private InventoryCaseView view(DuplicateCase item) {
        CaseFacts facts = evaluator.caseFacts(item);
        InventoryIntake row = rowOf(item.getIntakeId());
        IntakeLead lead = leadOf(row.getId());
        return new InventoryCaseView(item.getId(), item.getStatus().name(), item.getSignal().name(),
                item.getOutcome() == null ? null : item.getOutcome().name(), item.getBasis(), item.getDecidedAt(),
                new InventoryCaseView.IntakeSide(row.getId(), lead.leadCode(), lead.wardName(), parts(row)),
                item.onProperty() ? propertyCandidate(item, facts) : intakeCandidate(item, facts), item.getVersion());
    }

    private InventoryCaseView.Candidate propertyCandidate(DuplicateCase item, CaseFacts facts) {
        PropertyFacts property = inventory.property(item.getCandidatePropertyId())
                .orElseThrow(() -> new IllegalStateException("A case names a property that does not exist."));
        List<OwnerRequestSnapshot.Part> units = inventory.unitsOf(property.id()).stream()
                .map(unit -> new OwnerRequestSnapshot.Part(unit.code(), unit.locationLabelOrNull())).toList();
        String checked = facts.side().checkedProofUserOrNull();
        return new InventoryCaseView.Candidate("PROPERTY", null, property.code(),
                new InventoryCaseView.Boundary(property.assetTypeOrNull(), property.layoutOrNull(), property.wardName(),
                        property.street()), units, checked == null ? null : checked.equals(property.ownerId()));
    }

    private InventoryCaseView.Candidate intakeCandidate(DuplicateCase item, CaseFacts facts) {
        InventoryIntake other = rowOf(item.getCandidateIntakeId());
        IntakeLead lead = leadOf(other.getId());
        Optional<IntakeSource> source = sources.currentSource(lead.leadId(), lead.intentCycle());
        String first = facts.side().checkedProofUserOrNull();
        String second = facts.candidateOrNull().checkedProofUserOrNull();
        return new InventoryCaseView.Candidate("INTAKE", other.getId(), lead.leadCode(),
                new InventoryCaseView.Boundary(other.getAssetType().name(), other.getRentalScope().name(), lead.wardName(),
                        source.map(IntakeSource::addressText).orElse(null)), parts(other),
                first == null || second == null ? null : first.equals(second));
    }

    private static List<OwnerRequestSnapshot.Part> parts(InventoryIntake row) {
        return row.getSelection().stream().map(space -> new OwnerRequestSnapshot.Part(space.code(), space.location())).toList();
    }

    private DuplicateCase found(String caseId) {
        return cases.findById(caseId).orElseThrow(() -> new IntakeRefusedException(IntakeError.NOT_FOUND));
    }

    private InventoryIntake rowOf(String intakeId) {
        return intakes.findById(intakeId).orElseThrow(() -> new IllegalStateException("A case names a row that does not exist."));
    }

    private IntakeLead leadOf(String intakeId) {
        String leadId = rowOf(intakeId).getLeadId();
        return leads.systemFacts(List.of(leadId)).get(leadId);
    }

    private static IntakeRefusedException refused(String field) {
        return new IntakeRefusedException(IntakeError.VALIDATION_FAILED, List.of(field));
    }
}
