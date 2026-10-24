package vn.sunvibe.survey.core.domain.policy.checklist;

/** How a later hazard addresses an earlier one (survey-checklist spec A-20). */
public enum AddressKind {
    /** The later visit saw it resolved, whatever the severity. */
    RESOLVED,
    /** The later hazard keeps it important or unclassified and warns in turn. */
    CARRIED
}
