package vn.sunvibe.survey.core.presentation.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import vn.sunvibe.shared.constant.error.ErrorCode;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;
import vn.sunvibe.shared.dto.response.error.ValidationError;
import vn.sunvibe.survey.core.application.exception.appointment.NoCurrentVisitException;
import vn.sunvibe.survey.core.domain.exception.appointment.AppointmentAlreadyScheduledException;
import vn.sunvibe.survey.core.domain.exception.appointment.AppointmentStateException;
import vn.sunvibe.survey.core.domain.exception.appointment.PerformerMismatchException;

import java.util.List;

import static vn.sunvibe.survey.core.presentation.exception.CommonExceptionHandler.invalidState;

/**
 * The bodies of an appointment that is final, already scheduled, without a current visit, or performed by someone else.
 * Not an advice: {@link SurveyExceptionHandler} is the one survey advice and keeps every {@code @ExceptionHandler}
 * method in its order, so Spring picks the same handler as before (clean-up plan amendment 2026-11-08, SV-272); each
 * method here answers for the handler of the same name.
 */
final class AppointmentExceptionHandler {

    private AppointmentExceptionHandler() {
    }

    /** survey-schedule spec 4.4: a visited or cancelled appointment is final. */
    static ResponseEntity<ErrorResponse<ValidationError>> handleAppointmentState(AppointmentStateException e) {
        return invalidState(List.of());
    }

    static ResponseEntity<ErrorResponse<ValidationError>> handleAlreadyScheduled(
            AppointmentAlreadyScheduledException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ErrorCode.SURVEY_ALREADY_SCHEDULED, e.getMessage(), List.of()));
    }

    static ResponseEntity<ErrorResponse<ValidationError>> handleNoCurrentVisit(NoCurrentVisitException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ErrorCode.NO_CURRENT_VISIT, e.getMessage(), List.of()));
    }

    static ResponseEntity<ErrorResponse<ValidationError>> handlePerformerMismatch(PerformerMismatchException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(ErrorResponse.of(ErrorCode.PERFORMER_MISMATCH, e.getMessage(),
                        List.of(ValidationError.of("performedByUserId", e.getMessage()))));
    }
}
