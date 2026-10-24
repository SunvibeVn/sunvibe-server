package vn.sunvibe.survey.core.domain.model.checklist;

import java.time.LocalDate;

/** An amenity outside the v1 codes (survey-checklist spec 4.6 G6): a client ULID, a label, an optional space. */
public record OtherItem(String itemId, String label, String spaceId, YesNo presence, ChecklistSource source,
                        LocalDate confirmedOn, String receivedByUserId, String relayBasis) {

    @Override
    public String toString() {
        return "OtherItem[" + itemId + "]";
    }
}
