package vn.sunvibe.brokerage.core.domain.model.agreement;

import java.time.Instant;
import java.util.Objects;

/**
 * One version of an agreement (brokerage-agreement BR-5: a version once sent never changes; BR-2: each version rests on
 * its own usable intake basis), with its content: the period, the public photo consent (BR-9), the confirmed terms
 * (BR-8) and the brokerage fee policy (BR-7), and the time it was sent to the owner. An entity of the
 * {@link BrokerageAgreement} aggregate, changed only through it.
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
    private final BrokerageFee fee;
    private final Instant sentAtOrNull;

    private AgreementVersion(String id, int versionNo, VersionStatus status, AgreementPeriod period, String basisLeadId,
                             String basisIntakeId, String createdByUserId, Instant createdAt, Instant updatedAt,
                             PhotoConsent photoConsent, VersionTerms terms, BrokerageFee fee, Instant sentAtOrNull) {
        if (versionNo <= 0) {
            throw new IllegalStateException("An agreement version number starts at 1.");
        }
        // brokerage-agreement BR-5: a version is a draft until it is sent, so a draft has no sending time.
        if (status == VersionStatus.DRAFT && sentAtOrNull != null) {
            throw new IllegalArgumentException("A draft version has no sending time.");
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
        this.fee = Objects.requireNonNull(fee, "fee");
        this.sentAtOrNull = sentAtOrNull;
    }

    /** Rebuilds a stored version in any state with nothing entered yet: no consent answered, no terms, no fee policy. */
    public static AgreementVersion load(String id, int versionNo, VersionStatus status, AgreementPeriod period,
                                        String basisLeadId, String basisIntakeId, String createdByUserId,
                                        Instant createdAt, Instant updatedAt) {
        return load(id, versionNo, status, period, basisLeadId, basisIntakeId, createdByUserId, createdAt, updatedAt,
                PhotoConsent.NONE, VersionTerms.NONE);
    }

    /** Rebuilds a stored version in any state with its consent and terms, no fee policy entered and never sent. */
    public static AgreementVersion load(String id, int versionNo, VersionStatus status, AgreementPeriod period,
                                        String basisLeadId, String basisIntakeId, String createdByUserId,
                                        Instant createdAt, Instant updatedAt, PhotoConsent photoConsent,
                                        VersionTerms terms) {
        return load(id, versionNo, status, period, basisLeadId, basisIntakeId, createdByUserId, createdAt, updatedAt,
                photoConsent, terms, BrokerageFee.NONE, null);
    }

    /**
     * Rebuilds a stored version in any state with its whole content and its sending time.
     *
     * @throws IllegalArgumentException a DRAFT version with a sending time
     */
    public static AgreementVersion load(String id, int versionNo, VersionStatus status, AgreementPeriod period,
                                        String basisLeadId, String basisIntakeId, String createdByUserId,
                                        Instant createdAt, Instant updatedAt, PhotoConsent photoConsent,
                                        VersionTerms terms, BrokerageFee fee, Instant sentAtOrNull) {
        return new AgreementVersion(id, versionNo, status, period, basisLeadId, basisIntakeId, createdByUserId,
                createdAt, updatedAt, photoConsent, terms, fee, sentAtOrNull);
    }

    /** This version with a new content and update time, everything else kept; the aggregate calls it on its draft. */
    AgreementVersion withDraftContent(AgreementPeriod newPeriod, PhotoConsent newPhotoConsent, VersionTerms newTerms,
                                      BrokerageFee newFee, Instant now) {
        return new AgreementVersion(id, versionNo, status, newPeriod, basisLeadId, basisIntakeId, createdByUserId,
                createdAt, now, newPhotoConsent, newTerms, newFee, sentAtOrNull);
    }

    /**
     * This version sent to the owner (brokerage-agreement US-1 AC-5, BR-5): AWAITING_OWNER, with its sending and update
     * times at {@code now} and its content locked from then on; the aggregate calls it on its draft.
     */
    AgreementVersion sent(Instant now) {
        return new AgreementVersion(id, versionNo, VersionStatus.AWAITING_OWNER, period, basisLeadId, basisIntakeId,
                createdByUserId, createdAt, now, photoConsent, terms, fee, now);
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

    /** The brokerage fee policy (brokerage-agreement BR-7); never null, {@link BrokerageFee#NONE} when nothing entered. */
    public BrokerageFee getFee() {
        return fee;
    }

    /** When the version was sent to the owner; null for a version never sent (E05-S4 spec S4-D10). */
    public Instant getSentAtOrNull() {
        return sentAtOrNull;
    }

    /** brokerage-agreement BR-9: every version permits Sunvibe's own channels only, for its period (spec S3-D6). */
    public AdvertisingScope getAdvertisingScope() {
        return AdvertisingScope.SUNVIBE_CHANNELS;
    }
}
