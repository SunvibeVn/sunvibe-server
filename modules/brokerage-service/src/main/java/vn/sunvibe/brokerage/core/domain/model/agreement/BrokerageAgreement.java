package vn.sunvibe.brokerage.core.domain.model.agreement;

import vn.sunvibe.brokerage.core.domain.policy.agreement.VersionSendRules;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A brokerage agreement between Sunvibe and the declared owner of one unit, for one transaction type
 * (brokerage-agreement BR-1), with its versions (BR-5). The aggregate is the only write path of its rows: it keeps the
 * agreement's basis lead equal to its latest version's (E05-S2 spec S2-D3). A draft is created, its content edited, and
 * it is sent to the owner (E05-S4); the owner confirms or declines it, or it lapses after 7 days, and a new draft opens
 * after a rejection or a lapse (E05-S5).
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

    private BrokerageAgreement(String id, String unitId, String unitCodeOrNull, String propertyId,
                               TransactionType transactionType, String ownerUserId, String basisLeadId,
                               AgreementStatus status, String createdByUserId, Instant createdAt, Instant updatedAt,
                               Long rowVersionOrNull, List<AgreementVersion> versions) {
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
                AgreementStatus.DRAFTING, actorUserId, now, now, null, List.of(first));
    }

    /** Rebuilds a stored agreement in any state, its versions by number. */
    public static BrokerageAgreement load(String id, String unitId, String unitCodeOrNull, String propertyId,
                                          TransactionType type, String ownerUserId, String basisLeadId,
                                          AgreementStatus status, String createdByUserId, Instant createdAt,
                                          Instant updatedAt, Long rowVersion, List<AgreementVersion> versions) {
        return new BrokerageAgreement(id, unitId, unitCodeOrNull, propertyId, type, ownerUserId, basisLeadId, status,
                createdByUserId, createdAt, updatedAt, rowVersion, versions);
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
                status, createdByUserId, createdAt, now, rowVersionOrNull, edited);
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
                AgreementStatus.AWAITING_CONFIRMATION, createdByUserId, createdAt, now, rowVersionOrNull, sent);
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
     * This agreement as it stands at {@code now} (brokerage-agreement BR-10, US-2 AC-3; E05-S5 spec S5-D8): a version
     * waiting for the owner whose 7 days have passed is LAPSED, and the agreement drafting again when no version is
     * pending any more (spec S5-D9). {@code this} when nothing is due. Every read applies it to its copy; the lapse job
     * and the new draft store it.
     */
    public BrokerageAgreement withDueLapse(Instant now) {
        AgreementVersion due = dueLapseOrNull(now);
        if (due == null) {
            return this;
        }
        List<AgreementVersion> lapsed = versions.stream()
                .map(each -> each == due ? due.lapsed(now) : each).toList();
        return withVersions(statusWhenVersionEnded(lapsed), now, lapsed);
    }

    /** The version that lapses at {@code now}, for its history row at its deadline; null when none is due. */
    public AgreementVersion dueLapseOrNull(Instant now) {
        return versions.stream().filter(version -> version.lapseDueAt(now)).findFirst().orElse(null);
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
                newStatus, createdByUserId, createdAt, now, rowVersionOrNull, newVersions);
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
}
