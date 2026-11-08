package vn.sunvibe.property.core.application.service.intake;

import vn.sunvibe.property.core.application.shared.external.lead.IntakeLead;
import vn.sunvibe.property.core.application.shared.external.survey.IntakeSource;
import vn.sunvibe.property.core.domain.model.intake.IntakeStatus;
import vn.sunvibe.property.core.domain.model.intake.IntakeSuspension;
import vn.sunvibe.property.core.domain.model.intake.InventoryIntake;
import vn.sunvibe.property.core.domain.model.intake.OwnerCheckState;
import vn.sunvibe.property.core.domain.model.intake.OwnerProof;
import vn.sunvibe.property.core.domain.model.intake.OwnerRequest;
import vn.sunvibe.property.core.domain.policy.intake.CasePolicy.CaseFacts;
import vn.sunvibe.property.core.domain.policy.intake.CasePolicy.Reading;
import vn.sunvibe.property.core.domain.policy.intake.CurrentRowPolicy.SourceFacts;
import vn.sunvibe.property.core.domain.policy.intake.GateReason;

import java.util.List;
import java.util.Optional;

/**
 * What one lead stands at now (property-create intake spec 4.1): computed on read and changing nothing. A command reads it
 * after its locks, so it decides on what the rows hold at that moment.
 *
 * @param currentOrNull     the current row of the lead and cycle
 * @param effectiveOrNull   its effective status; null reads as none
 * @param proofOrNull       the proof of the current row neither withdrawn nor voided
 * @param proofLive         that proof is live (I-3, D5-10)
 * @param cases             the cases of the current row on its scope
 * @param readings          how each of them reads for that row, in the same order
 * @param forcedPropertyId  the property a SAME_ASSET decision binds this row to
 * @param latestCompletedOrNull the lead's latest COMPLETED row, current or not
 */
public record IntakeState(IntakeLead lead, Optional<IntakeSource> source, List<IntakeSource.MarkedRevision> marks,
                          List<InventoryIntake> rows, SourceFacts sourceFacts, InventoryIntake currentOrNull,
                          IntakeStatus effectiveOrNull, OwnerRequest latestRequestOrNull, OwnerProof proofOrNull,
                          boolean proofLive, List<CaseFacts> cases, List<Reading> readings,
                          Optional<String> forcedPropertyId, InventoryIntake latestCompletedOrNull,
                          List<IntakeSuspension> suspensions, List<GateReason> gateReasons) {

    public boolean checkDone() {
        return proofLive && proofOrNull.getOwnerCheck() == OwnerCheckState.DONE;
    }

    /** The version a client must name; null with no current row (property-create intake spec 4.1). */
    public Long versionOrNull() {
        return currentOrNull == null ? null : currentOrNull.getVersion();
    }

    /** property-create intake spec 4.1: the latest COMPLETED row is not the current one, so a link to its property is all that is left. */
    public boolean hasEarlierResult() {
        return latestCompletedOrNull != null && !latestCompletedOrNull.equals(currentOrNull);
    }
}
