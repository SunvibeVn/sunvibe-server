package vn.sunvibe.property.core.application.service.intake;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeLead;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeLeadPort;
import vn.sunvibe.property.core.application.shared.external.survey.IntakeSource;
import vn.sunvibe.property.core.application.shared.external.survey.IntakeSourcePort;
import vn.sunvibe.property.core.application.shared.external.user.UserDirectoryPort;
import vn.sunvibe.property.core.domain.model.property.enums.AssetType;
import vn.sunvibe.property.core.domain.model.property.enums.RentalLayout;
import vn.sunvibe.property.core.domain.model.intake.CaseOutcome;
import vn.sunvibe.property.core.domain.model.intake.CaseStatus;
import vn.sunvibe.property.core.domain.model.intake.DuplicateCase;
import vn.sunvibe.property.core.domain.model.intake.IntakeStatus;
import vn.sunvibe.property.core.domain.model.intake.IntakeSuspension;
import vn.sunvibe.property.core.domain.model.intake.InventoryIntake;
import vn.sunvibe.property.core.domain.model.intake.OwnerCheckState;
import vn.sunvibe.property.core.domain.model.intake.OwnerProof;
import vn.sunvibe.property.core.domain.model.intake.OwnerRequest;
import vn.sunvibe.property.core.domain.model.intake.ProofBinding;
import vn.sunvibe.property.core.domain.policy.intake.CasePolicy;
import vn.sunvibe.property.core.domain.policy.intake.CasePolicy.CaseFacts;
import vn.sunvibe.property.core.domain.policy.intake.CasePolicy.Reading;
import vn.sunvibe.property.core.domain.policy.intake.CasePolicy.Side;
import vn.sunvibe.property.core.domain.policy.intake.CurrentRowPolicy;
import vn.sunvibe.property.core.domain.policy.intake.CurrentRowPolicy.RowFacts;
import vn.sunvibe.property.core.domain.policy.intake.CurrentRowPolicy.SourceFacts;
import vn.sunvibe.property.core.domain.policy.intake.GateReason;
import vn.sunvibe.property.core.domain.policy.intake.IntakeGate;
import vn.sunvibe.property.core.domain.repository.DuplicateCaseDomainRepository;
import vn.sunvibe.property.core.domain.repository.IntakeSuspensionDomainRepository;
import vn.sunvibe.property.core.domain.repository.InventoryIntakeDomainRepository;
import vn.sunvibe.property.core.domain.repository.InventoryReadRepository;
import vn.sunvibe.property.core.domain.repository.OwnerProofDomainRepository;
import vn.sunvibe.property.core.domain.repository.OwnerRequestDomainRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Computes where a lead's intake stands (property-create intake spec 4.1, 4.4, 4.10). Reads only: it stores nothing, so a GET
 * changes no intake, request, proof or case, and a command runs it after its locks to decide on fresh rows.
 */
@Component
@RequiredArgsConstructor
public class IntakeEvaluator {

    /** D5-12: these kinds on the source of a result leave inventory only reconciliation can put right (property-create BR-20). */
    private static final Set<String> RECONCILIATION_KINDS = Set.of("WRONG_ASSET", "WRONG_OWNER");
    private static final String EVIDENCE_GONE = "EVIDENCE_GONE";

    private final InventoryIntakeDomainRepository intakes;
    private final OwnerRequestDomainRepository requests;
    private final OwnerProofDomainRepository proofs;
    private final DuplicateCaseDomainRepository cases;
    private final IntakeSuspensionDomainRepository suspensions;
    private final InventoryReadRepository inventory;
    private final IntakeSourcePort sources;
    private final IntakeLeadPort leads;
    private final UserDirectoryPort users;

    public IntakeState read(IntakeLead lead, Instant now) {
        Optional<IntakeSource> source = sources.currentSource(lead.leadId(), lead.intentCycle());
        List<IntakeSource.MarkedRevision> marks = sources.markedRevisions(lead.leadId());
        List<InventoryIntake> rows = intakes.findByLeadId(lead.leadId());
        SourceFacts sourceFacts = sourceFacts(lead, source);
        InventoryIntake current = CurrentRowPolicy.current(rows, sourceFacts).orElse(null);
        InventoryIntake latestCompleted = CurrentRowPolicy.latestCompleted(rows).orElse(null);
        List<IntakeSuspension> ofLead = suspensions.findByLeadId(lead.leadId());

        OwnerRequest latestRequest = null;
        OwnerProof proof = null;
        boolean proofLive = false;
        IntakeStatus effective = null;
        List<CaseFacts> caseFacts = List.of();
        List<Reading> readings = List.of();
        Optional<String> forced = Optional.empty();
        if (current != null) {
            List<OwnerRequest> ofRow = requests.findByIntakeId(current.getId());
            latestRequest = ofRow.isEmpty() ? null : ofRow.getLast();
            proof = proofs.findInForce(current.getId()).orElse(null);
            proofLive = proofLive(proof, current);
            caseFacts = caseFacts(current);
            readings = caseFacts.stream().map(facts -> readingFor(current.getId(), facts)).toList();
            List<CaseFacts> waitingOnThisRow = caseFacts.stream()
                    .filter(facts -> CasePolicy.waitingIntake(facts.row()).equals(current.getId())).toList();
            forced = CasePolicy.forcedProperty(waitingOnThisRow);
            boolean waiting = readings.contains(Reading.WAITING);
            boolean openOrWaiting = waiting || readings.contains(Reading.OPEN);
            boolean expired = latestRequest != null && !now.isBefore(latestRequest.getExpiresAt());
            effective = CurrentRowPolicy.effectiveStatus(current.getStatus(), new RowFacts(
                    CurrentRowPolicy.sourceLive(current, sourceFacts) || current.getStatus() == IntakeStatus.COMPLETED,
                    expired, proofLive, openOrWaiting, waiting)).orElse(null);
        }
        List<GateReason> gate = IntakeGate.reasons(new IntakeGate.Facts(lead.intent(), lead.status(), source.isPresent(),
                source.map(IntakeSource::usable).orElse(false), source.map(IntakeEvaluator::assetType).orElse(null),
                source.map(IntakeEvaluator::scope).orElse(null),
                reconciliationRequired(latestCompleted, current, marks, caseFacts), wholeLeadRestricted(ofLead)));
        return new IntakeState(lead, source, marks, rows, sourceFacts, current, effective, latestRequest, proof, proofLive,
                caseFacts, readings, forced, latestCompleted, ofLead, gate);
    }

    /** The open rows of the lead whose source died: the next accepted command abandons them (property-create intake spec 4.1). */
    public List<InventoryIntake> deadOpenRows(IntakeState state) {
        return state.rows().stream().filter(row -> row.getStatus().isOpen()
                && !CurrentRowPolicy.sourceLive(row, state.sourceFacts())).toList();
    }

    /** I-3: in force, on what this row stands on now, and the account has lost no sign-in since. */
    boolean proofLive(OwnerProof proof, InventoryIntake row) {
        if (proof == null) {
            return false;
        }
        ProofBinding binding = new ProofBinding(row.getLeadId(), row.getIntentCycle(), row.getSourceChecklistId(),
                row.scopeHash());
        return proof.isLive(binding, users.ownerStanding(proof.getUserId()).signInLosses());
    }

    /** property-create intake spec 4.10: an open row whose source and proof are live, as a competing intake must be. */
    public boolean sideLive(InventoryIntake row) {
        return row.getStatus().isOpen() && proofs.findInForce(row.getId()).filter(this::isLiveNow).isPresent();
    }

    /** I-3 for a proof of any lead: live on its own row, whose source still stands, so it dies with its lead. */
    public boolean isLiveNow(OwnerProof proof) {
        if (!proof.inForce()) {
            return false;
        }
        InventoryIntake row = intakes.findById(proof.getIntakeId())
                .orElseThrow(() -> new IllegalStateException("A proof names a row that does not exist."));
        IntakeLead lead = leads.systemFacts(List.of(row.getLeadId())).get(row.getLeadId());
        SourceFacts facts = sourceFacts(lead, sources.currentSource(lead.leadId(), lead.intentCycle()));
        return CurrentRowPolicy.sourceLive(row, facts) && proofLive(proof, row);
    }

    /** The cases of a row on its scope, on either side of a pair (property-create intake spec 4.10). */
    public List<CaseFacts> caseFacts(InventoryIntake row) {
        String scope = row.scopeHash();
        List<CaseFacts> facts = new ArrayList<>();
        for (DuplicateCase item : cases.findForIntake(row.getId())) {
            boolean thisSide = item.getIntakeId().equals(row.getId()) ? scope.equals(item.getScopeHash())
                    : scope.equals(item.getCandidateScopeHash());
            if (thisSide) {
                facts.add(caseFacts(item));
            }
        }
        return List.copyOf(facts);
    }

    /** One case with both sides read as they stand now. */
    public CaseFacts caseFacts(DuplicateCase item) {
        Side side = side(item.getIntakeId(), item.getScopeHash(), item);
        Side candidate = item.getCandidateIntakeId() == null ? null
                : side(item.getCandidateIntakeId(), item.getCandidateScopeHash(), item);
        String named = item.onProperty() ? item.getCandidatePropertyId() : completedWinnerResult(item, side, candidate);
        String owner = named == null ? null : inventory.property(named)
                .map(InventoryReadRepository.PropertyFacts::ownerId).orElse(null);
        return new CaseFacts(item, side, candidate, owner);
    }

    private static String completedWinnerResult(DuplicateCase item, Side side, Side candidate) {
        if (item.getProceedingIntakeId() == null) {
            return null;
        }
        Side winner = item.getProceedingIntakeId().equals(side.intakeId()) ? side : candidate;
        return winner.completed() ? winner.resultPropertyIdOrNull() : null;
    }

    /** A DECIDED case holds or forces only its waiting intake; for the other side it is over (property-create intake spec 4.10). */
    public static Reading readingFor(String intakeId, CaseFacts facts) {
        Reading reading = CasePolicy.read(facts);
        boolean decided = facts.row().getStatus() == CaseStatus.DECIDED;
        return decided && !CasePolicy.waitingIntake(facts.row()).equals(intakeId) ? Reading.RELEASED : reading;
    }

    private Side side(String intakeId, String pairScope, DuplicateCase item) {
        InventoryIntake row = intakes.findById(intakeId)
                .orElseThrow(() -> new IllegalStateException("A case names a row that does not exist."));
        if (row.getStatus() == IntakeStatus.COMPLETED) {
            return new Side(intakeId, intakeId.equals(item.getProceedingIntakeId()), true, null, row.getResultPropertyId());
        }
        if (!row.getStatus().isOpen() || !pairScope.equals(row.scopeHash())) {
            return new Side(intakeId, false, false, null, null);
        }
        IntakeLead lead = leads.systemFacts(List.of(row.getLeadId())).get(row.getLeadId());
        SourceFacts facts = sourceFacts(lead, sources.currentSource(lead.leadId(), lead.intentCycle()));
        OwnerProof proof = proofs.findInForce(intakeId).orElse(null);
        boolean live = CurrentRowPolicy.sourceLive(row, facts) && proofLive(proof, row);
        String checked = live && proof.getOwnerCheck() == OwnerCheckState.DONE ? proof.getUserId() : null;
        return new Side(intakeId, live, false, checked, null);
    }

    public static SourceFacts sourceFacts(IntakeLead lead, Optional<IntakeSource> source) {
        return new SourceFacts(source.map(IntakeSource::revisionId).orElse(null),
                source.map(IntakeSource::reasonOrNull).filter(EVIDENCE_GONE::equals).isPresent(), lead.intentCycle(),
                lead.closed());
    }

    /** property-create intake spec 4.11: an unreleased suspension of the whole lead. */
    public static boolean wholeLeadRestricted(List<IntakeSuspension> suspensions) {
        return suspensions.stream().anyMatch(item -> item.isActive() && item.onWholeLead());
    }

    /**
     * D5-12, property-create BR-20: a WRONG_ASSET or WRONG_OWNER mark needs reconciliation. A missing or unknown kind does
     * not, and reads without a throw (SV-215).
     */
    public static boolean reconciliationKind(String kindOrNull) {
        return kindOrNull != null && RECONCILIATION_KINDS.contains(kindOrNull);
    }

    public static AssetType assetType(IntakeSource source) {
        return source.assetTypeOrNull() == null ? null : AssetType.valueOf(source.assetTypeOrNull());
    }

    public static RentalLayout scope(IntakeSource source) {
        return RentalLayout.valueOf(source.rentalScope());
    }

    /**
     * D5-12, property-create BR-20: the source of the lead's result carries a wrong-asset or wrong-owner mark, or a manager ruled the earlier
     * result out for this row, so no legal target is left.
     */
    private static boolean reconciliationRequired(InventoryIntake latestCompleted, InventoryIntake current,
                                                  List<IntakeSource.MarkedRevision> marks, List<CaseFacts> caseFacts) {
        if (latestCompleted == null) {
            return false;
        }
        boolean wrongSource = marks.stream().anyMatch(mark -> mark.revisionId().equals(latestCompleted.getSourceChecklistId())
                && reconciliationKind(mark.kind()));
        if (wrongSource) {
            return true;
        }
        boolean earlier = !latestCompleted.equals(current);
        return earlier && caseFacts.stream().map(CaseFacts::row).anyMatch(row -> row.getStatus() == CaseStatus.DECIDED
                && row.getOutcome() == CaseOutcome.DIFFERENT_ASSET
                && Objects.equals(row.getCandidatePropertyId(), latestCompleted.getResultPropertyId()));
    }

}
