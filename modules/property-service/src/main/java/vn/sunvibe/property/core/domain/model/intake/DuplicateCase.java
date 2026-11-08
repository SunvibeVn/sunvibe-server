package vn.sunvibe.property.core.domain.model.intake;

import java.time.Instant;

/**
 * A possible duplicate for a manager (property-create intake spec 4.10, D5-15). Who owns what is read each time, never
 * stored here, so a later checked proof by another account changes how a decision applies. The basis stays out of every log.
 */
public final class DuplicateCase {

    private final String id;
    private final String intakeId;
    private final String candidatePropertyId;
    private final String candidateIntakeId;
    private final String pairKey;
    private final String scopeHash;
    private final String candidateScopeHash;
    private final CaseSignal signal;
    private final Instant createdAt;
    private CaseStatus status;
    private CaseOutcome outcome;
    private String proceedingIntakeId;
    private String basis;
    private String decidedByUserId;
    private Instant decidedAt;
    private long version;

    private DuplicateCase(String id, String intakeId, String candidatePropertyId, String candidateIntakeId, String pairKey,
                          String scopeHash, String candidateScopeHash, CaseSignal signal, CaseStatus status,
                          CaseOutcome outcome, String proceedingIntakeId, String basis, String decidedByUserId,
                          Instant decidedAt, long version, Instant createdAt) {
        this.id = id;
        this.intakeId = intakeId;
        this.candidatePropertyId = candidatePropertyId;
        this.candidateIntakeId = candidateIntakeId;
        this.pairKey = pairKey;
        this.scopeHash = scopeHash;
        this.candidateScopeHash = candidateScopeHash;
        this.signal = signal;
        this.status = status;
        this.outcome = outcome;
        this.proceedingIntakeId = proceedingIntakeId;
        this.basis = basis;
        this.decidedByUserId = decidedByUserId;
        this.decidedAt = decidedAt;
        this.version = version;
        this.createdAt = createdAt;
    }

    /** A new undecided case: one candidate, a property or an intake (property-create intake spec 5.2 file 9). */
    public static DuplicateCase open(String id, String intakeId, String candidatePropertyIdOrNull,
                                     String candidateIntakeIdOrNull, String pairKey, String scopeHash,
                                     String candidateScopeHashOrNull, CaseSignal signal, Instant now) {
        return new DuplicateCase(id, intakeId, candidatePropertyIdOrNull, candidateIntakeIdOrNull, pairKey, scopeHash,
                candidateScopeHashOrNull, signal, CaseStatus.OPEN, null, null, null, null, null, 0L, now);
    }

    public static DuplicateCase load(String id, String intakeId, String candidatePropertyId, String candidateIntakeId,
                                     String pairKey, String scopeHash, String candidateScopeHash, CaseSignal signal,
                                     CaseStatus status, CaseOutcome outcome, String proceedingIntakeId, String basis,
                                     String decidedByUserId, Instant decidedAt, long version, Instant createdAt) {
        return new DuplicateCase(id, intakeId, candidatePropertyId, candidateIntakeId, pairKey, scopeHash,
                candidateScopeHash, signal, status, outcome, proceedingIntakeId, basis, decidedByUserId, decidedAt, version,
                createdAt);
    }

    public void decide(CaseOutcome newOutcome, String proceedingIntakeIdOrNull, String reason, String managerUserId,
                       Instant now) {
        status = CaseStatus.DECIDED;
        outcome = newOutcome;
        proceedingIntakeId = proceedingIntakeIdOrNull;
        basis = reason;
        decidedByUserId = managerUserId;
        decidedAt = now;
        version++;
    }

    /** An exit that is no decision (property-create intake spec 4.10): a CLOSED pair that meets again opens a new case. */
    public void close() {
        status = CaseStatus.CLOSED;
        version++;
    }

    public boolean onProperty() {
        return candidatePropertyId != null;
    }

    public String getId() {
        return id;
    }

    public String getIntakeId() {
        return intakeId;
    }

    public String getCandidatePropertyId() {
        return candidatePropertyId;
    }

    public String getCandidateIntakeId() {
        return candidateIntakeId;
    }

    public String getPairKey() {
        return pairKey;
    }

    public String getScopeHash() {
        return scopeHash;
    }

    public String getCandidateScopeHash() {
        return candidateScopeHash;
    }

    public CaseSignal getSignal() {
        return signal;
    }

    public CaseStatus getStatus() {
        return status;
    }

    public CaseOutcome getOutcome() {
        return outcome;
    }

    public String getProceedingIntakeId() {
        return proceedingIntakeId;
    }

    public String getBasis() {
        return basis;
    }

    public String getDecidedByUserId() {
        return decidedByUserId;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "DuplicateCase[" + id + " " + status + "]";
    }
}
