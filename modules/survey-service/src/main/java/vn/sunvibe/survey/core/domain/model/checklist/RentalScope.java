package vn.sunvibe.survey.core.domain.model.checklist;

/** What the draft declares was viewed (survey-checklist BR-3, US-1 AC-3; the draft's declaration only, L-5). */
public enum RentalScope {
    /** The whole asset as one space. */
    WHOLE,
    /** Several non-overlapping spaces, 1 to 50. */
    PARTS
}
