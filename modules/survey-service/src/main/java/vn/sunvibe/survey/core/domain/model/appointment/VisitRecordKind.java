package vn.sunvibe.survey.core.domain.model.appointment;

/** Who entered a visit (survey-schedule US-3 AC-1a, spec A-8). */
public enum VisitRecordKind {
    /** The current surveyor, who held the appointment at the arrival, recorded their own visit. */
    OWN,
    /** A manager recorded the visit for the person who went, with a reason and a basis (survey-schedule BR-9). */
    PROXY
}
