package vn.sunvibe.survey.core.domain.policy.checklist;

/** Warnings of the preview (survey-checklist spec 4.7, A-8). */
public enum WarningType {
    /** An important or unclassified hazard of this checklist not seen resolved (BR-18). */
    OPEN_IMPORTANT_HAZARD,
    /** An important or unclassified hazard of an earlier checklist no later one addresses (BR-24). */
    EARLIER_HAZARD_OPEN,
    /** A space was only partly entered (BR-17). */
    PARTIAL_ACCESS
}
