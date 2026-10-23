package vn.sunvibe.survey.core.domain.model.appointment;

/**
 * The lead's place copied at booking (survey-schedule US-1 AC-2, BR-7): address, province and ward with their names,
 * optional pin. The lead itself is never changed by a survey. Private location data: never logged.
 */
public record LeadLocationSnapshot(String addressDetail, String provinceCode, String provinceName, String wardCode,
                                   String wardName, Coordinates coordinates) {

    public LeadLocationSnapshot {
        if (addressDetail == null || provinceCode == null || provinceName == null || wardCode == null
                || wardName == null) {
            throw new IllegalArgumentException("The lead place needs address, province and ward.");
        }
    }

    @Override
    public String toString() {
        return "LeadLocationSnapshot[hidden]";
    }
}
