package vn.sunvibe.property.core.domain.model.intake;

/** Why a case opened (property-create intake D5-14, property-create intake spec 4.7). */
public enum CaseSignal {
    /** The normalized address of a property is equal. */
    ADDRESS,
    /** The pin of a property is within 50 m. */
    PIN,
    /** Another open intake competes for the place. */
    INTAKE,
    /** A link onto the owner's property whose place differs. */
    LINK_PLACE
}
