package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

/** How the observed address differs from the lead and from the meeting place (survey-checklist spec 4.6, BR-19). */
@Schema(description = "How the observed address differs from the lead and from the meeting place")
public record ChecklistAddressDifference(
        boolean fromLead,
        boolean fromMeetingPlace) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistAddressDifference[" + fromLead + "]";
    }
}
