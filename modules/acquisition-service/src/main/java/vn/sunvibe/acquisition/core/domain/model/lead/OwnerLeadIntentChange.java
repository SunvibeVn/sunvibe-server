package vn.sunvibe.acquisition.core.domain.model.lead;

import java.time.Instant;
import java.util.Optional;

/**
 * One requirement change of a lead (survey-schedule US-5, BR-10): the intent and asking price before and after, why
 * (reason) and on what basis (the owner's confirmation), who recorded it and when, and the intent cycle it started.
 * Insert-only history. The id is a Snowflake number given by the caller. Reason and basis are staff text: never
 * logged.
 */
public final class OwnerLeadIntentChange {

    private final long id;
    private final OwnerLeadId leadId;
    private final OwnerLeadIntent fromIntent;
    private final Optional<AskingPrice> fromPrice;
    private final OwnerLeadIntent toIntent;
    private final Optional<AskingPrice> toPrice;
    private final String reason;
    private final ConfirmationBasis basis;
    private final String actorUserId;
    private final int newIntentCycle;
    private final Instant occurredAt;

    private OwnerLeadIntentChange(long id, OwnerLeadId leadId, OwnerLeadIntent fromIntent,
                                  Optional<AskingPrice> fromPrice, OwnerLeadIntent toIntent,
                                  Optional<AskingPrice> toPrice, String reason, ConfirmationBasis basis,
                                  String actorUserId, int newIntentCycle, Instant occurredAt) {
        this.id = id;
        this.leadId = required(leadId, "leadId");
        this.fromIntent = required(fromIntent, "fromIntent");
        this.fromPrice = required(fromPrice, "fromPrice");
        this.toIntent = required(toIntent, "toIntent");
        this.toPrice = required(toPrice, "toPrice");
        this.reason = required(reason, "reason");
        this.basis = required(basis, "basis");
        required(basis.confirmedOn(), "basis.confirmedOn");
        required(basis.text(), "basis.text");
        this.actorUserId = required(actorUserId, "actorUserId");
        if (newIntentCycle < 2) {
            throw new IllegalArgumentException("newIntentCycle must be at least 2.");
        }
        this.newIntentCycle = newIntentCycle;
        this.occurredAt = required(occurredAt, "occurredAt");
    }

    /**
     * The history row of a change the lead made; the reason and the basis are the checked, cleaned values.
     */
    public static OwnerLeadIntentChange create(long id, OwnerLeadId leadId, IntentChange change, String reason,
                                               ConfirmationBasis basis, String actorUserId, Instant occurredAt) {
        return new OwnerLeadIntentChange(id, leadId, change.fromIntent(), change.fromPrice(), change.toIntent(),
                change.toPrice(), reason, basis, actorUserId, change.newIntentCycle(), occurredAt);
    }

    /**
     * Load from database (reconstruct).
     */
    public static OwnerLeadIntentChange load(long id, OwnerLeadId leadId, OwnerLeadIntent fromIntent,
                                             Optional<AskingPrice> fromPrice, OwnerLeadIntent toIntent,
                                             Optional<AskingPrice> toPrice, String reason, ConfirmationBasis basis,
                                             String actorUserId, int newIntentCycle, Instant occurredAt) {
        return new OwnerLeadIntentChange(id, leadId, fromIntent, fromPrice, toIntent, toPrice, reason, basis,
                actorUserId, newIntentCycle, occurredAt);
    }

    // ==================== Getters ====================

    public long getId() {
        return id;
    }

    public OwnerLeadId getLeadId() {
        return leadId;
    }

    public OwnerLeadIntent getFromIntent() {
        return fromIntent;
    }

    public Optional<AskingPrice> getFromPrice() {
        return fromPrice;
    }

    public OwnerLeadIntent getToIntent() {
        return toIntent;
    }

    public Optional<AskingPrice> getToPrice() {
        return toPrice;
    }

    public String getReason() {
        return reason;
    }

    public ConfirmationBasis getBasis() {
        return basis;
    }

    public String getActorUserId() {
        return actorUserId;
    }

    public int getNewIntentCycle() {
        return newIntentCycle;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    /** Reason and basis are staff text: never print them. */
    @Override
    public String toString() {
        return "OwnerLeadIntentChange[" + id + "]";
    }

    private static <T> T required(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required.");
        }
        return value;
    }
}
