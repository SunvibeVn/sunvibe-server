package vn.sunvibe.survey.core.domain.model.checklist;

import java.time.LocalDate;

/** One checked amenity of a level (survey-checklist spec 4.6 G6): present or not, with its source. */
public record AmenityItem(AmenityCode code, YesNo presence, ChecklistSource source, LocalDate confirmedOn,
                          String receivedByUserId, String relayBasis) {

    @Override
    public String toString() {
        return "AmenityItem[" + code + "]";
    }
}
