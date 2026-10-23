package vn.sunvibe.survey.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.appointment.VisitRecordKind;

import java.time.Instant;

/**
 * The visit of an appointment (survey-schedule US-3): own or proxy record, who reached the property and when, the
 * revision in force then, the note, who entered it and when the server stored it, and the proxy reason and basis.
 */
@Schema(description = "The visit recorded for the appointment")
public record SurveyVisitResponse(VisitRecordKind recordKind, String performedByUserId, String performedByName,
                                  Instant arrivedAt, int revisionNo, String note, String recordedByUserId,
                                  String recordedByName, Instant recordedAt, SurveyProxyResponse proxy) {
}
