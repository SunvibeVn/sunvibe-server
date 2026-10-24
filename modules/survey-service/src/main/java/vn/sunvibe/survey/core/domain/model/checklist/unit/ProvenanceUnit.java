package vn.sunvibe.survey.core.domain.model.checklist.unit;

import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSource;
import vn.sunvibe.survey.core.domain.model.checklist.FieldState;

import java.time.LocalDate;

/**
 * One provenance unit of a group's content (survey-checklist spec 4.5, A-19): its key and kind, its state, the
 * business value of the correction rule (a wrapper's value, a G6 item's presence, an other item's label, space and
 * presence, an owner fix report's note), its source parts, the owner fix report of a hazard and the whole value the
 * unit records, which decides whether it changed.
 */
public record ProvenanceUnit(UnitKey key, UnitKind kind, FieldState state, Object businessValue,
                             ChecklistSource source, LocalDate confirmedOn, String receivedByUserId, String relayBasis,
                             OwnerReport ownerReportOrNull, Object wholeValue) {

    public boolean isUnknown() {
        return state == FieldState.UNKNOWN;
    }

    /** Values may be private: never print them. */
    @Override
    public String toString() {
        return "ProvenanceUnit[" + key + ", " + state + ", " + source + "]";
    }
}
