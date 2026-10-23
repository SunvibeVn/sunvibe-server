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
    /**
     * A survey of the property is booked, or a visit was recorded and the survey work goes on (survey-schedule
     * BR-3). Reached only through survey commands, never by a manual status change.
     */
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
     * Open leads: still waiting for contact, in contact with the owner or with survey work going on
     * (survey-schedule BR-3). Open leads take notes and reassignments and count for duplicate detection.
     */
    public boolean isOpen() {
        return this == NEW || this == ASSIGNED || this == CONTACTED || this == ON_HOLD || this == SURVEY_SCHEDULED;
    }

    /** Closed without becoming an onboarded owner. */
    public boolean isClosed() {
        return this == DUPLICATE || this == REJECTED || this == WITHDRAWN;
    }
}
