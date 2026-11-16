package vn.sunvibe.brokerage.core.domain.model.agreement;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * A brokerage agreement between Sunvibe and the declared owner of one unit, for one transaction type
 * (brokerage-agreement BR-1), with its versions (BR-5). The aggregate is the only write path of its rows: it keeps the
 * agreement's basis lead equal to its latest version's (E05-S2 spec S2-D3). A draft is created and its content edited;
 * no transition exists yet.
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
     * This agreement with its draft's period, photo consent and terms replaced and both update times set to {@code now}
     * (E05-S3 spec 5.1); its identity, unit, type, owner, basis, status, creator, creation time, row version and every
     * other version are kept. The caller checks {@link #draft()} first.
     *
     * @throws IllegalStateException when the agreement has no draft
     */
    public BrokerageAgreement editDraft(AgreementPeriod period, PhotoConsent photoConsent, VersionTerms terms,
                                        Instant now) {
        AgreementVersion draft = draft()
                .orElseThrow(() -> new IllegalStateException("The agreement has no draft to edit."));
        List<AgreementVersion> edited = versions.stream()
                .map(version -> version == draft ? draft.withDraftContent(period, photoConsent, terms, now) : version)
                .toList();
        return new BrokerageAgreement(id, unitId, unitCodeOrNull, propertyId, transactionType, ownerUserId, basisLeadId,
                status, createdByUserId, createdAt, now, rowVersionOrNull, edited);
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
