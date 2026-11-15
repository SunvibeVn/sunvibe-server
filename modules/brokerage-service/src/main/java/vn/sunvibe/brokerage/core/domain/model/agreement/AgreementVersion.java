package vn.sunvibe.brokerage.core.domain.model.agreement;

import java.time.Instant;
import java.util.Objects;

/**
 * One version of an agreement (brokerage-agreement BR-5: a version once sent never changes; BR-2: each version rests on
 * its own usable intake basis). An entity of the {@link BrokerageAgreement} aggregate, changed only through it.
 */
public final class AgreementVersion {

    private final String id;
    private final int versionNo;
    private final VersionStatus status;
    private final AgreementPeriod period;
    private final String basisLeadId;
    private final String basisIntakeId;
    private final String createdByUserId;
    private final Instant createdAt;
    private final Instant updatedAt;

    private AgreementVersion(String id, int versionNo, VersionStatus status, AgreementPeriod period, String basisLeadId,
                             String basisIntakeId, String createdByUserId, Instant createdAt, Instant updatedAt) {
        if (versionNo <= 0) {
            throw new IllegalStateException("An agreement version number starts at 1.");
        }
        this.id = Objects.requireNonNull(id, "id");
        this.versionNo = versionNo;
        this.status = Objects.requireNonNull(status, "status");
        this.period = Objects.requireNonNull(period, "period");
        this.basisLeadId = Objects.requireNonNull(basisLeadId, "basisLeadId");
        this.basisIntakeId = Objects.requireNonNull(basisIntakeId, "basisIntakeId");
        this.createdByUserId = Objects.requireNonNull(createdByUserId, "createdByUserId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /** Rebuilds a stored version in any state. */
    public static AgreementVersion load(String id, int versionNo, VersionStatus status, AgreementPeriod period,
                                        String basisLeadId, String basisIntakeId, String createdByUserId,
                                        Instant createdAt, Instant updatedAt) {
        return new AgreementVersion(id, versionNo, status, period, basisLeadId, basisIntakeId, createdByUserId,
                createdAt, updatedAt);
    }

    public String getId() {
        return id;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public VersionStatus getStatus() {
        return status;
    }

    public AgreementPeriod getPeriod() {
        return period;
    }

    public String getBasisLeadId() {
        return basisLeadId;
    }

    public String getBasisIntakeId() {
        return basisIntakeId;
    }

    public String getCreatedByUserId() {
        return createdByUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
