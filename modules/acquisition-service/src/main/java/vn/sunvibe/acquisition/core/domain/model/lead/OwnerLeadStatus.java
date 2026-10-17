package vn.sunvibe.acquisition.core.domain.model.lead;

/**
 * Where an owner lead stands in the acquisition pipeline. The queue module decides which moves are allowed.
 */
public enum OwnerLeadStatus {
    /** The owner sent the form and nobody is responsible for the lead yet. */
    NEW,
    /** Staff are checking that the lead is genuine and complete. */
    VALIDATING,
    /** A staff member is responsible for contacting the owner. */
    ASSIGNED,
    /** Staff have spoken with the owner. */
    CONTACTED,
    /** A visit to the property is planned. */
    SURVEY_SCHEDULED,
    /** The owner and the property are confirmed as suitable for the platform. */
    QUALIFIED,
    /** The owner is expected to sign the agreement. */
    AGREEMENT_PENDING,
    /** The owner has signed and the lead is complete. */
    ONBOARDED,
    /** Work is paused, for example waiting for the owner. */
    ON_HOLD,
    /** The lead repeats another lead and is closed. */
    DUPLICATE,
    /** Staff declined the lead and it is closed. */
    REJECTED,
    /** The owner withdrew the request and the lead is closed. */
    WITHDRAWN;

    /**
     * Open leads: still waiting for contact or in contact with the owner.
     */
    public boolean isOpen() {
        return this == NEW || this == ASSIGNED || this == CONTACTED || this == ON_HOLD;
    }

    /** Closed without becoming an onboarded owner. */
    public boolean isClosed() {
        return this == DUPLICATE || this == REJECTED || this == WITHDRAWN;
    }
}
