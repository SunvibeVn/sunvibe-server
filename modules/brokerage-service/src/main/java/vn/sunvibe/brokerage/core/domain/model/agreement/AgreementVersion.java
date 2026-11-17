package vn.sunvibe.brokerage.core.domain.model.agreement;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * One version of an agreement (brokerage-agreement BR-5: a version once sent never changes; BR-2: each version rests on
 * its own usable intake basis), with its content: the period, the public photo consent (BR-9), the confirmed terms
 * (BR-8) and the brokerage fee policy (BR-7), the time it was sent to the owner, and the owner's answer. An entity of
 * the {@link BrokerageAgreement} aggregate, changed only through it.
 */
public final class AgreementVersion {

    /** brokerage-agreement BR-10: the owner answers within 7 days (168 hours) of the sending (E05-S5 spec S5-D8). */
    public static final Duration ANSWER_WINDOW = Duration.ofDays(7);

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
    private final OwnerAnswer ownerAnswerOrNull;

    private AgreementVersion(String id, int versionNo, VersionStatus status, AgreementPeriod period, String basisLeadId,
                             String basisIntakeId, String createdByUserId, Instant createdAt, Instant updatedAt,
                             PhotoConsent photoConsent, VersionTerms terms, BrokerageFee fee, Instant sentAtOrNull,
                             OwnerAnswer ownerAnswerOrNull) {
        if (versionNo <= 0) {
            throw new IllegalStateException("An agreement version number starts at 1.");
        }
        // brokerage-agreement BR-5: a version is a draft until it is sent, so a draft has no sending time.
        if (status == VersionStatus.DRAFT && sentAtOrNull != null) {
            throw new IllegalArgumentException("A draft version has no sending time.");
        }
        // E05-S5 spec S5-D11: only a sent version is answered, and a draft or a version still waiting holds no answer.
        if (ownerAnswerOrNull != null && (sentAtOrNull == null || status == VersionStatus.DRAFT
                || status == VersionStatus.AWAITING_OWNER)) {
            throw new IllegalArgumentException("Only a sent version that is no longer waiting holds an owner answer.");
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
        this.ownerAnswerOrNull = ownerAnswerOrNull;
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
     * Rebuilds a stored version in any state with its whole content and its sending time, and no owner answer.
     *
     * @throws IllegalArgumentException a DRAFT version with a sending time
     */
    public static AgreementVersion load(String id, int versionNo, VersionStatus status, AgreementPeriod period,
                                        String basisLeadId, String basisIntakeId, String createdByUserId,
                                        Instant createdAt, Instant updatedAt, PhotoConsent photoConsent,
                                        VersionTerms terms, BrokerageFee fee, Instant sentAtOrNull) {
        return load(id, versionNo, status, period, basisLeadId, basisIntakeId, createdByUserId, createdAt, updatedAt,
                photoConsent, terms, fee, sentAtOrNull, null);
    }

    /**
     * Rebuilds a stored version in any state with its whole content, its sending time and the owner's answer.
     *
     * @throws IllegalArgumentException a DRAFT version with a sending time, or an answer on a version never sent, a
     *                                  DRAFT or an AWAITING_OWNER version (E05-S5 spec S5-D11)
     */
    public static AgreementVersion load(String id, int versionNo, VersionStatus status, AgreementPeriod period,
                                        String basisLeadId, String basisIntakeId, String createdByUserId,
                                        Instant createdAt, Instant updatedAt, PhotoConsent photoConsent,
                                        VersionTerms terms, BrokerageFee fee, Instant sentAtOrNull,
                                        OwnerAnswer ownerAnswerOrNull) {
        return new AgreementVersion(id, versionNo, status, period, basisLeadId, basisIntakeId, createdByUserId,
                createdAt, updatedAt, photoConsent, terms, fee, sentAtOrNull, ownerAnswerOrNull);
    }

    /** This version with a new content and update time, everything else kept; the aggregate calls it on its draft. */
    AgreementVersion withDraftContent(AgreementPeriod newPeriod, PhotoConsent newPhotoConsent, VersionTerms newTerms,
                                      BrokerageFee newFee, Instant now) {
        return new AgreementVersion(id, versionNo, status, newPeriod, basisLeadId, basisIntakeId, createdByUserId,
                createdAt, now, newPhotoConsent, newTerms, newFee, sentAtOrNull, ownerAnswerOrNull);
    }

    /**
     * This version sent to the owner (brokerage-agreement US-1 AC-5, BR-5): AWAITING_OWNER, with its sending and update
     * times at {@code now} and its content locked from then on; the aggregate calls it on its draft.
     */
    AgreementVersion sent(Instant now) {
        return new AgreementVersion(id, versionNo, VersionStatus.AWAITING_OWNER, period, basisLeadId, basisIntakeId,
                createdByUserId, createdAt, now, photoConsent, terms, fee, now, null);
    }

    /**
     * A new DRAFT version {@code newVersionNo} with this version's content, basis lead and intake, created by the actor
     * at {@code now} (E05-S5 spec S5-D10: the next draft starts as a copy of the latest version).
     */
    AgreementVersion copiedAsDraft(String newId, int newVersionNo, String actorUserId, Instant now) {
        return new AgreementVersion(newId, newVersionNo, VersionStatus.DRAFT, period, basisLeadId, basisIntakeId,
                actorUserId, now, now, photoConsent, terms, fee, null, null);
    }

    /** This version confirmed by its owner (brokerage-agreement BR-10): OWNER_CONFIRMED with the answer, content kept. */
    AgreementVersion confirmedBy(String userId, String statementVersion, Instant now) {
        return answered(VersionStatus.OWNER_CONFIRMED, OwnerAnswer.confirmed(userId, statementVersion, now), now);
    }

    /** This version declined by its owner (brokerage-agreement US-2 AC-3): OWNER_REJECTED with the answer. */
    AgreementVersion rejectedBy(String userId, String reasonOrNull, Instant now) {
        return answered(VersionStatus.OWNER_REJECTED, OwnerAnswer.rejected(userId, reasonOrNull, now), now);
    }

    /** This version not answered in time (brokerage-agreement BR-10): LAPSED, with no answer. */
    AgreementVersion lapsed(Instant now) {
        return answered(VersionStatus.LAPSED, null, now);
    }

    private AgreementVersion answered(VersionStatus newStatus, OwnerAnswer answerOrNull, Instant now) {
        return new AgreementVersion(id, versionNo, newStatus, period, basisLeadId, basisIntakeId, createdByUserId,
                createdAt, now, photoConsent, terms, fee, sentAtOrNull, answerOrNull);
    }

    /**
     * brokerage-agreement BR-10: the owner may answer until 7 days after the sending, exclusive (E05-S5 spec S5-D8); null
     * for a version never sent, which has no deadline.
     */
    public Instant answerByOrNull() {
        return sentAtOrNull == null ? null : sentAtOrNull.plus(ANSWER_WINDOW);
    }

    /**
     * Whether the owner may answer this version at {@code now}: it waits for them, it was sent, and {@code now} is before
     * its deadline (brokerage-agreement BR-10, US-2 AC-3).
     */
    public boolean answerableAt(Instant now) {
        return status == VersionStatus.AWAITING_OWNER && sentAtOrNull != null && now.isBefore(answerByOrNull());
    }

    /** Whether this version waits for the owner and its deadline has come at {@code now}, so it lapses. */
    boolean lapseDueAt(Instant now) {
        return status == VersionStatus.AWAITING_OWNER && sentAtOrNull != null && !now.isBefore(answerByOrNull());
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

    /** The owner's answer; null for a version never answered (E05-S5 spec S5-D11, S5-D13). */
    public OwnerAnswer getOwnerAnswerOrNull() {
        return ownerAnswerOrNull;
    }

    /** brokerage-agreement BR-9: every version permits Sunvibe's own channels only, for its period (spec S3-D6). */
    public AdvertisingScope getAdvertisingScope() {
        return AdvertisingScope.SUNVIBE_CHANNELS;
    }
}
