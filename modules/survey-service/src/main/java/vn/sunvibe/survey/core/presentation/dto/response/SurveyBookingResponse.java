package vn.sunvibe.survey.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.application.query.BookingBlock;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentKind;

/**
 * Whether a booking is possible now (survey-schedule spec 7.2): the kind and the visit a re-survey follows, or the
 * first check that blocks it.
 */
@Schema(description = "Whether and how the lead can get a survey booking now")
public record SurveyBookingResponse(boolean allowed, AppointmentKind kind, String previousVisitId,
                                    BookingBlock blockedBy) {
}
