package vn.sunvibe.survey.core.domain.model.checklist;

import vn.sunvibe.survey.core.domain.model.appointment.Basis;
import vn.sunvibe.survey.core.domain.policy.checklist.CorrectedUnit;

import java.util.List;

/**
 * The correction basis of a save (survey-checklist BR-22, BR-27, spec 4.5, 5.2): why, the date and text of the basis,
 * and the units it covered (only those that required it, or every changed unit for a voluntary correction). Texts:
 * never printed.
 */
public record Correction(String reason, Basis basis, List<CorrectedUnit> units) {

    public Correction {
        if (reason == null || basis == null || units == null) {
            throw new IllegalArgumentException("A correction has its reason, basis and units.");
        }
        units = List.copyOf(units);
    }

    @Override
    public String toString() {
        return "Correction[" + units.size() + " units]";
    }
}
