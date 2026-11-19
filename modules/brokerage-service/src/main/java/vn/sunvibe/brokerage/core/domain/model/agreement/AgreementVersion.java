package vn.sunvibe.brokerage.core.domain.model.agreement;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/**
 * One version of an agreement (brokerage-agreement BR-5: a version once sent never changes; BR-2: each version rests on
 * its own usable intake basis), with its content: the period, the public photo consent (BR-9), the confirmed terms
 * (BR-8) and the brokerage fee policy (BR-7), the time it was sent to the owner, the owner's answer, and a manager's
 * activation decision (BR-11). An entity of the {@link BrokerageAgreement} aggregate, changed only through it.
 */
public final class AgreementVersion {

    /** brokerage-agreement BR-10: the owner answers within 7 days (168 hours) of the sending (E05-S5 spec S5-D8). */
    public static final Duration ANSWER_WINDOW = Duration.ofDays(7);
    /**
     * brokerage-agreement BR-11, US-3 AC-4: a confirmed version is activated within 14 days (336 hours; Vietnam has no
     * daylight saving) of the owner's confirmation (E05-S6 spec S6-D5).
     */
    public static final Duration ACTIVATION_WINDOW = Duration.ofDays(14);
    /** brokerage-agreement BR-6: the dates of a version are calendar dates in Vietnam. */
    public static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

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
    private final ActivationDecision activationOrNull;

    private AgreementVersion(String id, int versionNo, VersionStatus status, AgreementPeriod period, String basisLeadId,
                             String basisIntakeId, String createdByUserId, Instant createdAt, Instant updatedAt,
                             PhotoConsent photoConsent, VersionTerms terms, BrokerageFee fee, Instant sentAtOrNull,
                             OwnerAnswer ownerAnswerOrNull, ActivationDecision activationOrNull) {
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
        checkActivation(status, ownerAnswerOrNull, activationOrNull);
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
        this.activationOrNull = activationOrNull;
    }

    /**
     * E05-S6 spec S6-D14: a version holds an activation decision only after the owner confirmed it; an activation only on
     * a version activated or past it (ACTIVATED, EFFECTIVE, SUPERSEDED, CANCELLED), a refusal only on an
     * ACTIVATION_REFUSED version. No rule asks a record on those states: stored rows inserted around the commands hold
     * none.
     */
    private static void checkActivation(VersionStatus status, OwnerAnswer ownerAnswerOrNull,
                                        ActivationDecision activationOrNull) {
        if (activationOrNull == null) {
            return;
        }
        if (ownerAnswerOrNull == null || ownerAnswerOrNull.type() != OwnerAnswerType.CONFIRMED) {
            throw new IllegalArgumentException("Only a version its owner confirmed holds an activation decision.");
        }
        boolean activatedState = status == VersionStatus.ACTIVATED || status == VersionStatus.EFFECTIVE
                || status == VersionStatus.SUPERSEDED || status == VersionStatus.CANCELLED;
        if (activationOrNull.type() == ActivationDecisionType.ACTIVATED && !activatedState) {
            throw new IllegalArgumentException("An activation is held only by an activated version.");
        }
        if (activationOrNull.type() == ActivationDecisionType.REFUSED && status != VersionStatus.ACTIVATION_REFUSED) {
            throw new IllegalArgumentException("A refusal is held only by a version whose activation was refused.");
        }
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
        return load(id, versionNo, status, period, basisLeadId, basisIntakeId, createdByUserId, createdAt, updatedAt,
                photoConsent, terms, fee, sentAtOrNull, ownerAnswerOrNull, null);
    }

    /**
     * Rebuilds a stored version in any state with its whole content, its sending time, the owner's answer and a
     * manager's activation decision.
     *
     * @throws IllegalArgumentException a DRAFT version with a sending time, an answer on a version never sent, a DRAFT
     *                                  or an AWAITING_OWNER version (E05-S5 spec S5-D11), or a decision outside the
     *                                  combinations of E05-S6 spec S6-D14
     */
    public static AgreementVersion load(String id, int versionNo, VersionStatus status, AgreementPeriod period,
                                        String basisLeadId, String basisIntakeId, String createdByUserId,
                                        Instant createdAt, Instant updatedAt, PhotoConsent photoConsent,
                                        VersionTerms terms, BrokerageFee fee, Instant sentAtOrNull,
                                        OwnerAnswer ownerAnswerOrNull, ActivationDecision activationOrNull) {
        return new AgreementVersion(id, versionNo, status, period, basisLeadId, basisIntakeId, createdByUserId,
                createdAt, updatedAt, photoConsent, terms, fee, sentAtOrNull, ownerAnswerOrNull, activationOrNull);
    }

    /** This version with a new content and update time, everything else kept; the aggregate calls it on its draft. */
    AgreementVersion withDraftContent(AgreementPeriod newPeriod, PhotoConsent newPhotoConsent, VersionTerms newTerms,
                                      BrokerageFee newFee, Instant now) {
        return new AgreementVersion(id, versionNo, status, newPeriod, basisLeadId, basisIntakeId, createdByUserId,
                createdAt, now, newPhotoConsent, newTerms, newFee, sentAtOrNull, ownerAnswerOrNull, activationOrNull);
    }

    /**
     * This version sent to the owner (brokerage-agreement US-1 AC-5, BR-5): AWAITING_OWNER, with its sending and update
     * times at {@code now} and its content locked from then on; the aggregate calls it on its draft.
     */
    AgreementVersion sent(Instant now) {
        return new AgreementVersion(id, versionNo, VersionStatus.AWAITING_OWNER, period, basisLeadId, basisIntakeId,
                createdByUserId, createdAt, now, photoConsent, terms, fee, now, null, null);
    }

    /**
     * A new DRAFT version {@code newVersionNo} with this version's content, basis lead and intake, created by the actor
     * at {@code now} (E05-S5 spec S5-D10: the next draft starts as a copy of the latest version).
     */
    AgreementVersion copiedAsDraft(String newId, int newVersionNo, String actorUserId, Instant now) {
        return new AgreementVersion(newId, newVersionNo, VersionStatus.DRAFT, period, basisLeadId, basisIntakeId,
                actorUserId, now, now, photoConsent, terms, fee, null, null, null);
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
                createdAt, now, photoConsent, terms, fee, sentAtOrNull, answerOrNull, null);
    }

    /**
     * This version activated by an independent manager (brokerage-agreement US-3 AC-2, AC-5; E05-S6 spec S6-D6):
     * ACTIVATED with the decision when its start lies ahead, else EFFECTIVE at once; the owner's answer and the content
     * kept. The aggregate calls it on the version awaiting activation.
     */
    AgreementVersion activated(ActivationDecision decision, boolean startAhead, Instant now) {
        return decided(startAhead ? VersionStatus.ACTIVATED : VersionStatus.EFFECTIVE, decision, now);
    }

    /** This version whose activation a manager refused (brokerage-agreement US-3 AC-3): ACTIVATION_REFUSED with it. */
    AgreementVersion activationRefused(ActivationDecision decision, Instant now) {
        return decided(VersionStatus.ACTIVATION_REFUSED, decision, now);
    }

    /** This activated version taking effect at its start (brokerage-agreement BR-5, BR-6): EFFECTIVE, record kept. */
    AgreementVersion started(Instant now) {
        return decided(VersionStatus.EFFECTIVE, activationOrNull, now);
    }

    /**
     * This confirmed version not activated within 14 days (brokerage-agreement BR-11, US-3 AC-4): LAPSED, the owner's
     * confirmation kept, so its {@link #activateByOrNull()} tells this lapse from the owner's 7-day one.
     */
    AgreementVersion activationLapsed(Instant now) {
        return decided(VersionStatus.LAPSED, null, now);
    }

    /**
     * This pending version cancelled because its agreement ended (brokerage-agreement BR-5 "Khi thỏa thuận kết thúc,
     * phiên bản đang chờ bị hủy"; E05-S8 spec S8-D3): CANCELLED, its sending time, owner answer and activation record
     * kept. The aggregate calls it on its pending version only.
     */
    AgreementVersion cancelled(Instant now) {
        return decided(VersionStatus.CANCELLED, activationOrNull, now);
    }

    private AgreementVersion decided(VersionStatus newStatus, ActivationDecision decisionOrNull, Instant now) {
        return new AgreementVersion(id, versionNo, newStatus, period, basisLeadId, basisIntakeId, createdByUserId,
                createdAt, now, photoConsent, terms, fee, sentAtOrNull, ownerAnswerOrNull, decisionOrNull);
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

    /**
     * brokerage-agreement BR-11, US-3 AC-4: a version whose owner answer is a confirmation is activated until 14 days
     * after that confirmation, exclusive (E05-S6 spec S6-D5); null when the owner did not confirm it, whatever its
     * status now (S6-D21), so a 14-day lapse holds one and a 7-day lapse none.
     */
    public Instant activateByOrNull() {
        if (ownerAnswerOrNull == null || ownerAnswerOrNull.type() != OwnerAnswerType.CONFIRMED) {
            return null;
        }
        return ownerAnswerOrNull.answeredAt().plus(ACTIVATION_WINDOW);
    }

    /**
     * The deadline of whichever lapse applies to this version (E05-S6 spec S6-D5): the owner's 7 days for a version
     * waiting for them, the 14 days of activation for a confirmed one, null in any other state. The history row of a
     * stored lapse is dated by it.
     */
    public Instant lapseDeadlineOrNull() {
        return switch (status) {
            case AWAITING_OWNER -> answerByOrNull();
            case OWNER_CONFIRMED -> activateByOrNull();
            default -> null;
        };
    }

    /** Whether a manager may activate or refuse this version at {@code now}: confirmed, and before its activateBy. */
    public boolean activatableAt(Instant now) {
        Instant activateBy = activateByOrNull();
        return status == VersionStatus.OWNER_CONFIRMED && activateBy != null && now.isBefore(activateBy);
    }

    /** Whether this confirmed version's 14 days have passed at {@code now}, so it lapses (E05-S6 spec S6-D5). */
    public boolean activationLapseDueAt(Instant now) {
        Instant activateBy = activateByOrNull();
        return status == VersionStatus.OWNER_CONFIRMED && activateBy != null && !now.isBefore(activateBy);
    }

    /** brokerage-agreement BR-6: the start of the start date in Vietnam, when the version may take effect. */
    public Instant startsAt() {
        return period.startDate().atStartOfDay(VIETNAM).toInstant();
    }

    /** brokerage-agreement BR-6: the start of the day after the end date in Vietnam, when the version's right ends. */
    public Instant endsAt() {
        return period.endDate().plusDays(1).atStartOfDay(VIETNAM).toInstant();
    }

    /**
     * Whether this activated version takes effect at {@code now} (E05-S6 spec S6-D6): ACTIVATED with its activation
     * record and its start reached. A row without a record (inserted around the commands) is never started.
     */
    public boolean startDueAt(Instant now) {
        return status == VersionStatus.ACTIVATED && activationOrNull != null
                && activationOrNull.type() == ActivationDecisionType.ACTIVATED && !startsAt().isAfter(now);
    }

    /** Today's calendar date in Vietnam at {@code now} (brokerage-agreement BR-6). */
    public static LocalDate todayInVietnam(Instant now) {
        return LocalDate.ofInstant(now, VIETNAM);
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

    /** A manager's activation decision; null for a version never decided (E05-S6 spec S6-D14, S6-D21). Staff data. */
    public ActivationDecision getActivationOrNull() {
        return activationOrNull;
    }

    /** brokerage-agreement BR-9: every version permits Sunvibe's own channels only, for its period (spec S3-D6). */
    public AdvertisingScope getAdvertisingScope() {
        return AdvertisingScope.SUNVIBE_CHANNELS;
    }
}
