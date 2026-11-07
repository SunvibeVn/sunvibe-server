package vn.sunvibe.property.core.domain.model.intake;

import vn.sunvibe.property.core.domain.exception.IntakeStateException;
import vn.sunvibe.property.core.domain.model.property.enums.AssetType;
import vn.sunvibe.property.core.domain.model.property.enums.RentalLayout;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Domain Model - InventoryIntake (Aggregate Root)
 * <p>
 * One attempt to turn a usable PASSED into inventory (property-create intake spec 4.1, 4.2). Every method is a command of
 * the state table and refuses outside its states, and every command that writes raises the version (A-13). What a read
 * computes on top of this stored status is {@code CurrentRowPolicy}'s; this class knows only what is stored.
 */
public final class InventoryIntake {

    private static final Set<IntakeStatus> OPEN = EnumSet.of(IntakeStatus.DRAFT, IntakeStatus.AWAITING_OWNER,
            IntakeStatus.OWNER_CONFIRMED, IntakeStatus.CASE_OPEN);
    private static final Set<IntakeStatus> AFTER_THE_REQUEST = EnumSet.of(IntakeStatus.AWAITING_OWNER,
            IntakeStatus.OWNER_CONFIRMED, IntakeStatus.CASE_OPEN);

    private final String id;
    private final int intakeNo;
    private final String leadId;
    private final int intentCycle;
    private final IntakeSourceRef source;
    private final Instant createdAt;
    private IntakeStatus status;
    private long version;
    private List<SelectedSpace> selection;
    private IntakeTarget target;
    private List<AvailabilityItem> availability;
    private Map<String, Long> usedVersions;
    private String resultPropertyId;
    private String proofId;
    private String previewHash;
    private String completedByUserId;
    private Instant completedAt;
    private Instant updatedAt;

    private InventoryIntake(String id, int intakeNo, String leadId, int intentCycle, IntakeSourceRef source,
                            IntakeStatus status, long version, List<SelectedSpace> selection, IntakeTarget target,
                            List<AvailabilityItem> availability, Map<String, Long> usedVersions, String resultPropertyId,
                            String proofId, String previewHash, String completedByUserId, Instant completedAt,
                            Instant createdAt, Instant updatedAt) {
        this.id = required(id, "id");
        if (intakeNo < 1) {
            throw new IllegalArgumentException("The intake number starts at 1.");
        }
        this.intakeNo = intakeNo;
        this.leadId = required(leadId, "leadId");
        this.intentCycle = intentCycle;
        this.source = required(source, "source");
        this.status = required(status, "status");
        this.version = version;
        this.selection = List.copyOf(required(selection, "selection"));
        this.target = target;
        this.availability = availability == null ? List.of() : List.copyOf(availability);
        this.usedVersions = usedVersions == null ? Map.of() : Map.copyOf(usedVersions);
        this.resultPropertyId = resultPropertyId;
        this.proofId = proofId;
        this.previewHash = previewHash;
        this.completedByUserId = completedByUserId;
        this.completedAt = completedAt;
        this.createdAt = required(createdAt, "createdAt");
        this.updatedAt = required(updatedAt, "updatedAt");
    }

    /** A first selection on a source: DRAFT, version 0 (spec 4.2). */
    public static InventoryIntake create(String id, int intakeNo, String leadId, int intentCycle, IntakeSourceRef source,
                                         List<SelectedSpace> selection, Instant now) {
        return new InventoryIntake(id, intakeNo, leadId, intentCycle, source, IntakeStatus.DRAFT, 0L, selection, null, null,
                null, null, null, null, null, null, now, now);
    }

    /** Rebuilds a stored row. */
    public static InventoryIntake load(String id, int intakeNo, String leadId, int intentCycle, IntakeSourceRef source,
                                       IntakeStatus status, long version, List<SelectedSpace> selection,
                                       IntakeTarget target, List<AvailabilityItem> availability,
                                       Map<String, Long> usedVersions, String resultPropertyId, String proofId,
                                       String previewHash, String completedByUserId, Instant completedAt,
                                       Instant createdAt, Instant updatedAt) {
        return new InventoryIntake(id, intakeNo, leadId, intentCycle, source, status, version, selection, target,
                availability, usedVersions, resultPropertyId, proofId, previewHash, completedByUserId, completedAt,
                createdAt, updatedAt);
    }

    // ==================== Commands of spec 4.2 ====================

    /** A new selection: back to DRAFT, with nothing kept that was built on the old scope. */
    public void select(List<SelectedSpace> newSelection, Instant now) {
        require(OPEN, "selection");
        selection = List.copyOf(newSelection);
        clearWork();
        status = IntakeStatus.DRAFT;
        touch(now);
    }

    /** A request went out. On a COMPLETED row the status stays: a new proof can restore a result (spec 4.2). */
    public void awaitOwner(Instant now) {
        if (status != IntakeStatus.COMPLETED) {
            require(EnumSet.of(IntakeStatus.DRAFT, IntakeStatus.AWAITING_OWNER), "owner request");
            status = IntakeStatus.AWAITING_OWNER;
        }
        touch(now);
    }

    public void confirmOwner(Instant now) {
        if (status != IntakeStatus.COMPLETED) {
            require(EnumSet.of(IntakeStatus.AWAITING_OWNER), "owner confirmation");
            status = IntakeStatus.OWNER_CONFIRMED;
        }
        touch(now);
    }

    /** A withdrawal, a refusal or the store of a computed DRAFT: what was built on the proof goes (spec 4.1, 4.2). */
    public void backToDraft(Instant now) {
        require(AFTER_THE_REQUEST, "return to draft");
        clearWork();
        status = IntakeStatus.DRAFT;
        touch(now);
    }

    public void target(IntakeTarget newTarget, Instant now) {
        require(EnumSet.of(IntakeStatus.OWNER_CONFIRMED), "target");
        target = required(newTarget, "target");
        touch(now);
    }

    /** A manager's ruling took the target away; the intake stays as it is (spec 4.10). */
    public void clearTarget(Instant now) {
        target = null;
        previewHash = null;
        touch(now);
    }

    public void availability(List<AvailabilityItem> items, Instant now) {
        require(EnumSet.of(IntakeStatus.OWNER_CONFIRMED), "availability");
        availability = List.copyOf(items);
        touch(now);
    }

    public void openCase(Instant now) {
        require(EnumSet.of(IntakeStatus.OWNER_CONFIRMED), "open case");
        status = IntakeStatus.CASE_OPEN;
        touch(now);
    }

    public void closeCase(Instant now) {
        require(EnumSet.of(IntakeStatus.CASE_OPEN), "close case");
        status = IntakeStatus.OWNER_CONFIRMED;
        touch(now);
    }

    public void complete(String propertyId, String proofInForceId, Map<String, Long> versions, String hash,
                         String actorUserId, Instant now) {
        require(EnumSet.of(IntakeStatus.OWNER_CONFIRMED), "completion");
        resultPropertyId = required(propertyId, "propertyId");
        proofId = required(proofInForceId, "proofId");
        usedVersions = Map.copyOf(versions);
        previewHash = required(hash, "previewHash");
        completedByUserId = required(actorUserId, "actorUserId");
        completedAt = required(now, "now");
        status = IntakeStatus.COMPLETED;
        touch(now);
    }

    /** The row's source died: the row ends, and a later command starts a new one (spec 4.1). */
    public void abandon(Instant now) {
        require(OPEN, "abandon");
        status = IntakeStatus.ABANDONED;
        touch(now);
    }

    /** A new proof takes over a completed result (spec 4.2, 4.11, E-13). */
    public void restore(String newProofId, Instant now) {
        require(EnumSet.of(IntakeStatus.COMPLETED), "restoration");
        proofId = required(newProofId, "proofId");
        touch(now);
    }

    /** A-13: a command that writes something else about this row still raises its version. */
    public void recordChange(Instant now) {
        touch(now);
    }

    private void clearWork() {
        target = null;
        availability = List.of();
        previewHash = null;
    }

    private void touch(Instant now) {
        version++;
        updatedAt = required(now, "now");
    }

    private void require(Set<IntakeStatus> allowed, String command) {
        if (!allowed.contains(status)) {
            throw new IntakeStateException(command);
        }
    }

    private static <T> T required(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required.");
        }
        return value;
    }

    // ==================== Reads ====================

    /** The hash of the selected spaces (I-3). */
    public String scopeHash() {
        return ScopeHash.of(selection.stream().map(SelectedSpace::spaceId).toList());
    }

    public String getId() {
        return id;
    }

    public int getIntakeNo() {
        return intakeNo;
    }

    public String getLeadId() {
        return leadId;
    }

    public int getIntentCycle() {
        return intentCycle;
    }

    public IntakeSourceRef getSource() {
        return source;
    }

    public String getSourceChecklistId() {
        return source.checklistId();
    }

    public RentalLayout getRentalScope() {
        return source.rentalScope();
    }

    public AssetType getAssetType() {
        return source.assetType();
    }

    public IntakeStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    public List<SelectedSpace> getSelection() {
        return selection;
    }

    public IntakeTarget getTarget() {
        return target;
    }

    public List<AvailabilityItem> getAvailability() {
        return availability;
    }

    public Map<String, Long> getUsedVersions() {
        return usedVersions;
    }

    public String getResultPropertyId() {
        return resultPropertyId;
    }

    public String getProofId() {
        return proofId;
    }

    public String getPreviewHash() {
        return previewHash;
    }

    public String getCompletedByUserId() {
        return completedByUserId;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Identity by row: two reads of one row are the same intake. */
    @Override
    public boolean equals(Object other) {
        return other instanceof InventoryIntake intake && id.equals(intake.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "InventoryIntake[" + id + " " + status + "]";
    }
}
