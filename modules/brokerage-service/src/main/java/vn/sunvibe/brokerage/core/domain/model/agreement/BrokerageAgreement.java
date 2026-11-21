package vn.sunvibe.brokerage.core.domain.model.agreement;

import vn.sunvibe.brokerage.core.domain.policy.agreement.VersionSendRules;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * A brokerage agreement between Sunvibe and the declared owner of one unit, for one transaction type
 * (brokerage-agreement BR-1), with its versions (BR-5). The aggregate is the only write path of its rows: it keeps the
 * agreement's basis lead equal to its latest version's (E05-S2 spec S2-D3). A draft is created, its content edited, and
 * it is sent to the owner (E05-S4); the owner confirms or declines it, or it lapses after 7 days, and a new draft opens
 * after a rejection or a lapse (E05-S5). An independent manager activates a confirmed version within 14 days or refuses
 * it, else it lapses; an activated version takes effect at its start (E05-S6). A manager suspends an agreement in
 * effect and another lifts it; staff terminate an agreement that has not ended, the assigned Sale cancels one that never
 * took effect, and an agreement in effect expires at the end of its version's end date (E05-S8).
 */
public final class BrokerageAgreement {

    private final String id;
    private final String unitId;
    private final String unitCodeOrNull;
    private final String propertyId;
    private final TransactionType transactionType;
    private final String ownerUserId;
    private final String basisLeadId;
    private final AgreementStatus status;
    private final String createdByUserId;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Long rowVersionOrNull;
    private final List<AgreementVersion> versions;
    private final Instant endedAtOrNull;
    private final AgreementTermination terminationOrNull;

    private BrokerageAgreement(String id, String unitId, String unitCodeOrNull, String propertyId,
                               TransactionType transactionType, String ownerUserId, String basisLeadId,
                               AgreementStatus status, String createdByUserId, Instant createdAt, Instant updatedAt,
                               Long rowVersionOrNull, List<AgreementVersion> versions, Instant endedAtOrNull,
                               AgreementTermination terminationOrNull) {
        this.id = required(id, "id");
        this.unitId = required(unitId, "unitId");
        this.unitCodeOrNull = unitCodeOrNull;
        this.propertyId = required(propertyId, "propertyId");
        this.transactionType = required(transactionType, "transactionType");
        this.ownerUserId = required(ownerUserId, "ownerUserId");
        this.basisLeadId = required(basisLeadId, "basisLeadId");
        this.status = required(status, "status");
        this.createdByUserId = required(createdByUserId, "createdByUserId");
        this.createdAt = required(createdAt, "createdAt");
        this.updatedAt = required(updatedAt, "updatedAt");
        this.rowVersionOrNull = rowVersionOrNull;
        this.versions = List.copyOf(required(versions, "versions"));
        // E05-S8 spec 5.1: the end fields bind one way only. A final state may lack them (a row stored before E05-S8),
        // but no agreement that has not ended holds an end, and only a terminated one holds a termination record.
        if (endedAtOrNull != null && this.status.isUnfinished()) {
            throw new IllegalStateException("Only an agreement that ended holds its end.");
        }
        if (terminationOrNull != null && this.status != AgreementStatus.TERMINATED) {
            throw new IllegalStateException("Only a terminated agreement holds a termination record.");
        }
        this.endedAtOrNull = endedAtOrNull;
        this.terminationOrNull = terminationOrNull;
        checkVersions();
    }

    /**
     * A new agreement in the state DRAFTING with its version 1 DRAFT (brokerage-agreement US-1 AC-3, BR-5), drafted by
     * the actor on the lead's basis.
     */
    public static BrokerageAgreement create(String id, String unitId, String unitCodeOrNull, String propertyId,
                                            TransactionType type, String ownerUserId, String basisLeadId,
                                            String basisIntakeId, String versionId, AgreementPeriod period,
                                            String actorUserId, Instant now) {
        AgreementVersion first = AgreementVersion.load(versionId, 1, VersionStatus.DRAFT, period, basisLeadId,
                basisIntakeId, actorUserId, now, now);
        return new BrokerageAgreement(id, unitId, unitCodeOrNull, propertyId, type, ownerUserId, basisLeadId,
                AgreementStatus.DRAFTING, actorUserId, now, now, null, List.of(first), null, null);
    }

    /** Rebuilds a stored agreement in any state, its versions by number, with no end and no termination record. */
    public static BrokerageAgreement load(String id, String unitId, String unitCodeOrNull, String propertyId,
                                          TransactionType type, String ownerUserId, String basisLeadId,
                                          AgreementStatus status, String createdByUserId, Instant createdAt,
                                          Instant updatedAt, Long rowVersion, List<AgreementVersion> versions) {
        return load(id, unitId, unitCodeOrNull, propertyId, type, ownerUserId, basisLeadId, status, createdByUserId,
                createdAt, updatedAt, rowVersion, versions, null, null);
    }

    /**
     * Rebuilds a stored agreement in any state with its end (E05-S8 spec 5.1): a final row stored without its end loads
     * with null in those fields, its status kept.
     *
     * @throws IllegalStateException an end on an agreement that has not ended, or a termination record on one that was
     *                               not terminated
     */
    public static BrokerageAgreement load(String id, String unitId, String unitCodeOrNull, String propertyId,
                                          TransactionType type, String ownerUserId, String basisLeadId,
                                          AgreementStatus status, String createdByUserId, Instant createdAt,
                                          Instant updatedAt, Long rowVersion, List<AgreementVersion> versions,
                                          Instant endedAtOrNull, AgreementTermination terminationOrNull) {
        return new BrokerageAgreement(id, unitId, unitCodeOrNull, propertyId, type, ownerUserId, basisLeadId, status,
                createdByUserId, createdAt, updatedAt, rowVersion, versions, endedAtOrNull, terminationOrNull);
    }

    /**
     * The version a Sale may edit: the pending version of a DRAFTING agreement while it is DRAFT (brokerage-agreement
     * BR-5: a version is a draft until it is sent and never changes after; E05-S3 spec S3-D2). Empty otherwise.
     */
    public Optional<AgreementVersion> draft() {
        if (status != AgreementStatus.DRAFTING) {
            return Optional.empty();
        }
        return versions.stream().filter(version -> version.getStatus().isPending()).findFirst()
                .filter(version -> version.getStatus() == VersionStatus.DRAFT);
    }

    /**
     * This agreement with its draft's period, photo consent, terms and fee policy replaced and both update times set to
     * {@code now} (E05-S3 spec 5.1, E05-S4 spec 5.1); its identity, unit, type, owner, basis, status, creator, creation
     * time, row version and every other version are kept. The caller checks {@link #draft()} first.
     *
     * @throws IllegalStateException when the agreement has no draft
     */
    public BrokerageAgreement editDraft(AgreementPeriod period, PhotoConsent photoConsent, VersionTerms terms,
                                        BrokerageFee fee, Instant now) {
        AgreementVersion draft = draft()
                .orElseThrow(() -> new IllegalStateException("The agreement has no draft to edit."));
        List<AgreementVersion> edited = versions.stream()
                .map(version -> version == draft ? draft.withDraftContent(period, photoConsent, terms, fee, now)
                        : version)
                .toList();
        return new BrokerageAgreement(id, unitId, unitCodeOrNull, propertyId, transactionType, ownerUserId, basisLeadId,
                status, createdByUserId, createdAt, now, rowVersionOrNull, edited, endedAtOrNull, terminationOrNull);
    }

    /**
     * This agreement with its draft sent to the owner (brokerage-agreement US-1 AC-5, BR-5; E05-S4 spec S4-D6): the
     * version AWAITING_OWNER with its sending time, its content locked from now on; the agreement AWAITING_CONFIRMATION
     * ("chờ xác nhận": its first version waits for the owner) with its update time at {@code now}. Everything else is
     * kept. The caller checks {@link #draft()} and {@link VersionSendRules} first.
     *
     * @throws IllegalStateException when the agreement has no draft, or the draft misses a part
     */
    public BrokerageAgreement sendDraft(Instant now) {
        AgreementVersion draft = draft()
                .orElseThrow(() -> new IllegalStateException("The agreement has no draft to send."));
        // brokerage-agreement BR-8 and E-13: no part of a version sent to the owner is unknown.
        if (!VersionSendRules.missingParts(draft).isEmpty()) {
            throw new IllegalStateException("The draft misses a part.");
        }
        List<AgreementVersion> sent = versions.stream()
                .map(version -> version == draft ? draft.sent(now) : version)
                .toList();
        return new BrokerageAgreement(id, unitId, unitCodeOrNull, propertyId, transactionType, ownerUserId, basisLeadId,
                AgreementStatus.AWAITING_CONFIRMATION, createdByUserId, createdAt, now, rowVersionOrNull, sent,
                endedAtOrNull, terminationOrNull);
    }

    /**
     * This agreement with its version {@code versionNo} confirmed by the owner (brokerage-agreement US-2 AC-2, BR-10;
     * E05-S5 spec S5-D6): OWNER_CONFIRMED with the account, the time and the statement version; the agreement stays
     * AWAITING_CONFIRMATION (BR-5: its first version now waits for activation). The caller checks first that the version
     * is answerable.
     *
     * @throws IllegalStateException unless that version waits for the owner and its deadline has not come
     */
    public BrokerageAgreement confirmByOwner(int versionNo, String userId, String statementVersion, Instant now) {
        AgreementVersion version = answerable(versionNo, now);
        return withVersions(status, now, versions.stream()
                .map(each -> each == version ? version.confirmedBy(userId, statementVersion, now) : each).toList());
    }

    /**
     * This agreement with its version {@code versionNo} declined by the owner (brokerage-agreement US-2 AC-3, E-6; E05-S5
     * spec S5-D7): OWNER_REJECTED with the account, the time and the optional reason; the agreement drafting again when
     * no version is pending any more (spec S5-D9). The caller checks first that the version is answerable.
     *
     * @throws IllegalStateException     unless that version waits for the owner and its deadline has not come
     * @throws vn.sunvibe.brokerage.core.domain.exception.AgreementContentException the reason breaks its rule
     */
    public BrokerageAgreement rejectByOwner(int versionNo, String userId, String reasonOrNull, Instant now) {
        AgreementVersion version = answerable(versionNo, now);
        List<AgreementVersion> answered = versions.stream()
                .map(each -> each == version ? version.rejectedBy(userId, reasonOrNull, now) : each).toList();
        return withVersions(statusWhenVersionEnded(answered), now, answered);
    }

    /**
     * This agreement with its version {@code versionNo} activated by an independent manager (brokerage-agreement US-3
     * AC-2, AC-5, BR-5, BR-11; E05-S6 spec S6-D1 step 11, S6-D6): the version ACTIVATED with the decision when the start
     * of its start date lies after {@code now}, else EFFECTIVE at once; the agreement ACTIVE, since its first activated
     * version puts it in effect. The caller checks the version first and answers its refusals.
     *
     * @throws IllegalStateException    unless the agreement awaits confirmation and that version is confirmed and may be
     *                                  activated at {@code now}
     * @throws IllegalArgumentException a decision that is not an activation
     */
    public BrokerageAgreement activate(int versionNo, ActivationDecision decision, Instant now) {
        if (decision.type() != ActivationDecisionType.ACTIVATED) {
            throw new IllegalArgumentException("An activation needs an activation decision.");
        }
        AgreementVersion version = awaitingActivation(versionNo, now);
        AgreementVersion activated = version.activated(decision, version.startsAt().isAfter(now), now);
        return withVersions(AgreementStatus.ACTIVE, now, versions.stream()
                .map(each -> each == version ? activated : each).toList());
    }

    /**
     * This agreement with the activation of its version {@code versionNo} refused (brokerage-agreement US-3 AC-3, E-7;
     * E05-S6 spec S6-D4): the version ACTIVATION_REFUSED with the decision, and the agreement drafting again when no
     * version is pending any more (E05-S5 spec S5-D9). The caller checks the version first.
     *
     * @throws IllegalStateException    unless the agreement awaits confirmation and that version is confirmed and may be
     *                                  activated at {@code now}
     * @throws IllegalArgumentException a decision that is not a refusal
     */
    public BrokerageAgreement refuseActivation(int versionNo, ActivationDecision decision, Instant now) {
        if (decision.type() != ActivationDecisionType.REFUSED) {
            throw new IllegalArgumentException("A refusal needs a refusal decision.");
        }
        AgreementVersion version = awaitingActivation(versionNo, now);
        List<AgreementVersion> refused = versions.stream()
                .map(each -> each == version ? version.activationRefused(decision, now) : each).toList();
        return withVersions(statusWhenVersionEnded(refused), now, refused);
    }

    /**
     * This agreement as it stands at {@code now} (brokerage-agreement BR-10, BR-11, US-2 AC-3, US-3 AC-4; E05-S5 spec
     * S5-D8, E05-S6 spec S6-D5): a version waiting for the owner whose 7 days have passed, or a confirmed version whose
     * 14 days of activation have passed, is LAPSED, and the agreement drafting again when no version is pending any
     * more (spec S5-D9). {@code this} when nothing is due. Every read applies it to its copy; the lapse job and the new
     * draft store it.
     */
    public BrokerageAgreement withDueLapse(Instant now) {
        AgreementVersion due = dueLapseOrNull(now);
        if (due == null) {
            return this;
        }
        AgreementVersion lapsedVersion = due.getStatus() == VersionStatus.OWNER_CONFIRMED
                ? due.activationLapsed(now) : due.lapsed(now);
        List<AgreementVersion> lapsed = versions.stream()
                .map(each -> each == due ? lapsedVersion : each).toList();
        return withVersions(statusWhenVersionEnded(lapsed), now, lapsed);
    }

    /**
     * The version that lapses at {@code now}, for its history row at its {@link AgreementVersion#lapseDeadlineOrNull()};
     * null when none is due.
     */
    public AgreementVersion dueLapseOrNull(Instant now) {
        return versions.stream().filter(version -> version.lapseDueAt(now) || version.activationLapseDueAt(now))
                .findFirst().orElse(null);
    }

    /**
     * This agreement with its activated version in effect from its start (brokerage-agreement BR-5, BR-6, US-3 AC-5;
     * E05-S6 spec S6-D6): the ACTIVATED version holding its activation record whose start has come is EFFECTIVE; the
     * agreement keeps its status. {@code this} when nothing is due. Every read applies it to its copy; the start job
     * stores it.
     */
    public BrokerageAgreement withDueStart(Instant now) {
        AgreementVersion due = dueStartOrNull(now);
        if (due == null) {
            return this;
        }
        return withVersions(status, now, versions.stream().map(each -> each == due ? due.started(now) : each).toList());
    }

    /** The activated version that takes effect at {@code now}, for its history row at its start; null when none is due. */
    public AgreementVersion dueStartOrNull(Instant now) {
        return versions.stream().filter(version -> version.startDueAt(now)).findFirst().orElse(null);
    }

    /**
     * This agreement as every read answers it at {@code now} (E05-S6 spec S6-D6, E05-S8 spec S8-D2, S8-D3, plan
     * E05S8-P3): exactly the transitions of {@link #dueTransitionsAt} applied in their time order, its due lapse, then
     * its due start, then its due expiry. {@code this} when nothing is due.
     */
    public BrokerageAgreement effectiveAt(Instant now) {
        return applyDue(now, null);
    }

    /**
     * The transitions due at {@code now} and not stored yet, in time order, for the history rows of the command or store
     * that writes {@link #effectiveAt} (E05-S8 spec S8-D8 step 5, plan E05S8-P3): VERSION_LAPSED at the version's
     * lapse deadline, VERSION_STARTED at its start, EXPIRED at the end of the version that counts. Empty when nothing is
     * due.
     */
    public List<DueTransition> dueTransitionsAt(Instant now) {
        List<DueTransition> due = new ArrayList<>();
        applyDue(now, due);
        return List.copyOf(due);
    }

    /**
     * The one ordering rule of E05-S8 spec S8-D3 (plan E05S8-P3), so the state and the history rows cannot drift: the
     * agreement holds at most one pending version (brokerage-agreement BR-5), so a lapse and a start are never both due.
     * A lapse is applied only when its deadline came before the agreement's end (one at or after the end is cancelled
     * by the expiry instead); a start comes before the end it may move; the expiry is the last step.
     */
    private BrokerageAgreement applyDue(Instant now, List<DueTransition> dueOrNull) {
        BrokerageAgreement current = this;
        AgreementVersion lapse = current.dueLapseOrNull(now);
        Instant expiryAt = current.dueExpiryAtOrNull(now);
        if (lapse != null && (expiryAt == null || lapse.lapseDeadlineOrNull().isBefore(expiryAt))) {
            record(dueOrNull, lapse.getVersionNo(), AgreementEventType.VERSION_LAPSED, lapse.lapseDeadlineOrNull());
            current = current.withDueLapse(now);
        }
        AgreementVersion start = current.dueStartOrNull(now);
        if (start != null) {
            record(dueOrNull, start.getVersionNo(), AgreementEventType.VERSION_STARTED, start.startsAt());
            current = current.withDueStart(now);
        }
        Instant expiry = current.dueExpiryAtOrNull(now);
        if (expiry != null) {
            record(dueOrNull, current.versionThatCountsOrNull().getVersionNo(), AgreementEventType.EXPIRED, expiry);
            current = current.withDueExpiry(now);
        }
        return current;
    }

    private static void record(List<DueTransition> dueOrNull, int versionNo, AgreementEventType type, Instant at) {
        if (dueOrNull != null) {
            dueOrNull.add(new DueTransition(versionNo, type, at));
        }
    }

    /**
     * The moment this agreement in effect expires when that moment has come at {@code now} (brokerage-agreement BR-6;
     * E05-S8 spec S8-D2, A-2): the {@link AgreementVersion#endsAt()} of its version that counts, the start of the day
     * after its end date in Vietnam. Null when the agreement is not ACTIVE or SUSPENDED, has no version that counts, or
     * its end has not come.
     */
    public Instant dueExpiryAtOrNull(Instant now) {
        if (status != AgreementStatus.ACTIVE && status != AgreementStatus.SUSPENDED) {
            return null;
        }
        AgreementVersion counts = versionThatCountsOrNull();
        if (counts == null || now.isBefore(counts.endsAt())) {
            return null;
        }
        return counts.endsAt();
    }

    /**
     * The last step of {@link #effectiveAt} (brokerage-agreement BR-5, BR-6, US-5 AC-1; E05-S8 spec S8-D2, S8-D3): an
     * ACTIVE or SUSPENDED agreement whose end has come is EXPIRED, its end the version's {@code endsAt}, and every
     * pending version without a lapse deadline, or with one at or after that end, CANCELLED; the EFFECTIVE version stays
     * EFFECTIVE. {@code this} when no expiry is due. A suspended agreement expires the same way. No caller stores it on
     * a raw read (plan E05S8-P3).
     *
     * @throws IllegalStateException when a start, or a lapse due before the end, is still to be applied first
     */
    public BrokerageAgreement withDueExpiry(Instant now) {
        Instant expiryAt = dueExpiryAtOrNull(now);
        if (expiryAt == null) {
            return this;
        }
        AgreementVersion lapse = dueLapseOrNull(now);
        if (dueStartOrNull(now) != null || (lapse != null && lapse.lapseDeadlineOrNull().isBefore(expiryAt))) {
            throw new IllegalStateException("A due start or an earlier lapse comes before the expiry.");
        }
        return ended(AgreementStatus.EXPIRED, expiryAt, null, now, version -> version.lapseDeadlineOrNull() == null
                || !version.lapseDeadlineOrNull().isBefore(expiryAt));
    }

    /**
     * This agreement in effect suspended by a manager (brokerage-agreement BR-15, US-5 AC-2; E05-S8 spec S8-D6): SUSPENDED,
     * its versions kept. The command checks the state first.
     *
     * @throws IllegalStateException unless the agreement is ACTIVE with nothing due at {@code now}
     */
    public BrokerageAgreement suspend(Instant now) {
        if (status != AgreementStatus.ACTIVE) {
            throw new IllegalStateException("Only an agreement in effect is suspended.");
        }
        requireNothingDue(now);
        return withVersions(AgreementStatus.SUSPENDED, now, versions);
    }

    /**
     * This suspended agreement lifted by an independent manager (brokerage-agreement BR-15, US-5 AC-5; E05-S8 spec S8-D6):
     * ACTIVE again, its versions kept; the answer returns by itself when every other condition holds.
     *
     * @throws IllegalStateException unless the agreement is SUSPENDED with nothing due at {@code now}
     */
    public BrokerageAgreement releaseSuspension(Instant now) {
        if (status != AgreementStatus.SUSPENDED) {
            throw new IllegalStateException("Only a suspended agreement is lifted.");
        }
        requireNothingDue(now);
        return withVersions(AgreementStatus.ACTIVE, now, versions);
    }

    /**
     * This agreement terminated (brokerage-agreement BR-14, US-5 AC-2; E05-S8 spec S8-D1, S8-D3, S8-D5): TERMINATED at
     * {@code now} with the record, its pending version CANCELLED; an EFFECTIVE version stays EFFECTIVE.
     *
     * @throws IllegalStateException unless the agreement has not ended and nothing is due at {@code now}
     */
    public BrokerageAgreement terminate(AgreementTermination termination, Instant now) {
        if (!status.isUnfinished()) {
            throw new IllegalStateException("An agreement that ended is not terminated.");
        }
        // the record's moment is the agreement's end, stored once (E05-S8 spec S8-D9: ended_at)
        if (termination == null || !termination.terminatedAt().equals(now)) {
            throw new IllegalArgumentException("A termination needs its record, made at the same moment.");
        }
        requireNothingDue(now);
        return ended(AgreementStatus.TERMINATED, now, termination, now, version -> true);
    }

    /**
     * This agreement that never took effect cancelled by the assigned Sale (brokerage-agreement BR-5, US-1 AC-5; E05-S8
     * spec S8-D1, S8-D3, S8-D7): CANCELLED at {@code now}, its pending version CANCELLED with its owner answer kept.
     *
     * @throws IllegalStateException unless the agreement is DRAFTING or AWAITING_CONFIRMATION with nothing due at
     *                               {@code now}
     */
    public BrokerageAgreement cancel(Instant now) {
        if (status != AgreementStatus.DRAFTING && status != AgreementStatus.AWAITING_CONFIRMATION) {
            throw new IllegalStateException("Only an agreement that never took effect is cancelled.");
        }
        requireNothingDue(now);
        return ended(AgreementStatus.CANCELLED, now, null, now, version -> true);
    }

    /**
     * This agreement with a new draft version n + 1 (brokerage-agreement US-2 AC-3, E-6; E05-S5 spec S5-D10): a DRAFT
     * copy of the latest version's period, photo consent, terms and fee policy, on its basis lead and intake, created by
     * the actor at {@code now}; earlier versions never change (BR-5). The caller applies and stores a due lapse first.
     *
     * @throws IllegalStateException unless the agreement is DRAFTING with no pending version
     */
    public BrokerageAgreement openDraft(String versionId, String actorUserId, Instant now) {
        if (status != AgreementStatus.DRAFTING || versions.stream().anyMatch(version -> version.getStatus().isPending())) {
            throw new IllegalStateException("A new draft opens only on a drafting agreement with no pending version.");
        }
        AgreementVersion latest = versions.getLast();
        List<AgreementVersion> opened = new ArrayList<>(versions);
        opened.add(latest.copiedAsDraft(versionId, latest.getVersionNo() + 1, actorUserId, now));
        return withVersions(status, now, opened);
    }

    /** The agreement's version of that number when it waits for the owner and may still be answered at {@code now}. */
    private AgreementVersion answerable(int versionNo, Instant now) {
        return versions.stream()
                .filter(version -> version.getVersionNo() == versionNo && version.answerableAt(now))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("The version does not wait for the owner's answer."));
    }

    /**
     * The agreement's version of that number when the agreement awaits confirmation and the version, confirmed by the
     * owner, may still be activated or refused at {@code now} (brokerage-agreement BR-11; E05-S6 spec S6-D1 step 6).
     */
    private AgreementVersion awaitingActivation(int versionNo, Instant now) {
        if (status != AgreementStatus.AWAITING_CONFIRMATION) {
            throw new IllegalStateException("The agreement does not await an activation.");
        }
        return versions.stream()
                .filter(version -> version.getVersionNo() == versionNo && version.activatableAt(now))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("The version does not wait for an activation."));
    }

    /**
     * brokerage-agreement BR-5 (E05-S5 spec S5-D9): an agreement awaiting confirmation is drafting again once a
     * transition ended its version without activating it and no version is pending; an agreement in effect keeps its
     * status.
     */
    private AgreementStatus statusWhenVersionEnded(List<AgreementVersion> after) {
        if (status == AgreementStatus.AWAITING_CONFIRMATION
                && after.stream().noneMatch(version -> version.getStatus().isPending())) {
            return AgreementStatus.DRAFTING;
        }
        return status;
    }

    private BrokerageAgreement withVersions(AgreementStatus newStatus, Instant now, List<AgreementVersion> newVersions) {
        return new BrokerageAgreement(id, unitId, unitCodeOrNull, propertyId, transactionType, ownerUserId, basisLeadId,
                newStatus, createdByUserId, createdAt, now, rowVersionOrNull, newVersions, endedAtOrNull,
                terminationOrNull);
    }

    /**
     * This agreement ended: the final state, its end moment and its termination record (null unless terminated), every
     * pending version that {@code cancels} selects CANCELLED (E05-S8 spec S8-D3); an EFFECTIVE version stays EFFECTIVE.
     */
    private BrokerageAgreement ended(AgreementStatus finalStatus, Instant endedAt, AgreementTermination terminationOrNull,
                                     Instant now, Predicate<AgreementVersion> cancels) {
        List<AgreementVersion> after = versions.stream()
                .map(version -> version.getStatus().isPending() && cancels.test(version) ? version.cancelled(now)
                        : version)
                .toList();
        return new BrokerageAgreement(id, unitId, unitCodeOrNull, propertyId, transactionType, ownerUserId, basisLeadId,
                finalStatus, createdByUserId, createdAt, now, rowVersionOrNull, after, endedAt, terminationOrNull);
    }

    /**
     * The guard of the stop transitions (plan E05S8-P3): they act on the effective copy the command decided on, so no
     * transition is still due on it at {@code now}.
     */
    private void requireNothingDue(Instant now) {
        if (!dueTransitionsAt(now).isEmpty()) {
            throw new IllegalStateException("A transition is still due on this copy; apply effectiveAt first.");
        }
    }

    /**
     * The version whose dates the agreement in effect reads (E05-S6 spec S6-D7): the EFFECTIVE version with the highest
     * number, else the ACTIVATED one; null when neither exists.
     */
    private AgreementVersion versionThatCountsOrNull() {
        return versions.stream()
                .filter(version -> version.getStatus() == VersionStatus.EFFECTIVE)
                .max(Comparator.comparingInt(AgreementVersion::getVersionNo))
                .or(() -> versions.stream().filter(version -> version.getStatus() == VersionStatus.ACTIVATED)
                        .findFirst())
                .orElse(null);
    }

    private void checkVersions() {
        if (versions.isEmpty()) {
            throw new IllegalStateException("An agreement has at least one version.");
        }
        for (int index = 0; index < versions.size(); index++) {
            if (versions.get(index).getVersionNo() != index + 1) {
                throw new IllegalStateException("Agreement versions are numbered 1 to n in order.");
            }
        }
        // brokerage-agreement BR-5: one pending version (from the draft to the activated one) at a time.
        if (versions.stream().filter(version -> version.getStatus().isPending()).count() > 1) {
            throw new IllegalStateException("An agreement has at most one pending version.");
        }
        if (!basisLeadId.equals(versions.get(versions.size() - 1).getBasisLeadId())) {
            throw new IllegalStateException("An agreement's basis lead is its latest version's.");
        }
    }

    private static <T> T required(T value, String name) {
        if (value == null) {
            throw new IllegalStateException("An agreement needs its " + name + ".");
        }
        return value;
    }

    public String getId() {
        return id;
    }

    public String getUnitId() {
        return unitId;
    }

    public String getUnitCodeOrNull() {
        return unitCodeOrNull;
    }

    public String getPropertyId() {
        return propertyId;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public String getOwnerUserId() {
        return ownerUserId;
    }

    public String getBasisLeadId() {
        return basisLeadId;
    }

    public AgreementStatus getStatus() {
        return status;
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

    public Long getRowVersionOrNull() {
        return rowVersionOrNull;
    }

    public List<AgreementVersion> getVersions() {
        return versions;
    }

    /**
     * When the agreement ended (E05-S8 spec 5.1): the end of its version for an expiry, the command's moment for a
     * termination or a cancellation; null while it has not ended, and for a final row stored before E05-S8.
     */
    public Instant getEndedAtOrNull() {
        return endedAtOrNull;
    }

    /** What staff recorded when they terminated it (E05-S8 spec S8-D5); null unless TERMINATED. Private (BR-17). */
    public AgreementTermination getTerminationOrNull() {
        return terminationOrNull;
    }
}
