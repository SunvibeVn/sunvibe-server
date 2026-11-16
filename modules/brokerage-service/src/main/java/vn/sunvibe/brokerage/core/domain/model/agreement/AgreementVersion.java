package vn.sunvibe.brokerage.core.domain.model.agreement;

import java.time.Instant;
import java.util.Objects;

/**
 * One version of an agreement (brokerage-agreement BR-5: a version once sent never changes; BR-2: each version rests on
 * its own usable intake basis), with its content: the period, the public photo consent (BR-9) and the confirmed terms
 * (BR-8). An entity of the {@link BrokerageAgreement} aggregate, changed only through it.
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
    private final PhotoConsent photoConsent;
    private final VersionTerms terms;

    private AgreementVersion(String id, int versionNo, VersionStatus status, AgreementPeriod period, String basisLeadId,
                             String basisIntakeId, String createdByUserId, Instant createdAt, Instant updatedAt,
                             PhotoConsent photoConsent, VersionTerms terms) {
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
        this.photoConsent = Objects.requireNonNull(photoConsent, "photoConsent");
        this.terms = Objects.requireNonNull(terms, "terms");
    }

    /** Rebuilds a stored version in any state with nothing entered yet: no consent answered, no terms. */
    public static AgreementVersion load(String id, int versionNo, VersionStatus status, AgreementPeriod period,
                                        String basisLeadId, String basisIntakeId, String createdByUserId,
                                        Instant createdAt, Instant updatedAt) {
        return load(id, versionNo, status, period, basisLeadId, basisIntakeId, createdByUserId, createdAt, updatedAt,
                PhotoConsent.NONE, VersionTerms.NONE);
    }

    /** Rebuilds a stored version in any state with its content. */
    public static AgreementVersion load(String id, int versionNo, VersionStatus status, AgreementPeriod period,
                                        String basisLeadId, String basisIntakeId, String createdByUserId,
                                        Instant createdAt, Instant updatedAt, PhotoConsent photoConsent,
                                        VersionTerms terms) {
        return new AgreementVersion(id, versionNo, status, period, basisLeadId, basisIntakeId, createdByUserId,
                createdAt, updatedAt, photoConsent, terms);
    }

    /** This version with a new content and update time, everything else kept; the aggregate calls it on its draft. */
    AgreementVersion withDraftContent(AgreementPeriod newPeriod, PhotoConsent newPhotoConsent, VersionTerms newTerms,
                                      Instant now) {
        return new AgreementVersion(id, versionNo, status, newPeriod, basisLeadId, basisIntakeId, createdByUserId,
                createdAt, now, newPhotoConsent, newTerms);
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

    public PhotoConsent getPhotoConsent() {
        return photoConsent;
    }

    public VersionTerms getTerms() {
        return terms;
    }

    /** brokerage-agreement BR-9: every version permits Sunvibe's own channels only, for its period (spec S3-D6). */
    public AdvertisingScope getAdvertisingScope() {
        return AdvertisingScope.SUNVIBE_CHANNELS;
    }
}
