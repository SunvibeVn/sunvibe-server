package vn.sunvibe.survey.core.application.usecase.appointment;

import vn.sunvibe.survey.core.application.command.appointment.BookAppointmentCommand;
import vn.sunvibe.survey.core.application.command.appointment.CancelAppointmentCommand;
import vn.sunvibe.survey.core.application.command.appointment.ChangeAppointmentCommand;
import vn.sunvibe.survey.core.application.command.appointment.ConfirmationCommand;
import vn.sunvibe.survey.core.application.command.appointment.RecordVisitCommand;
import vn.sunvibe.survey.core.application.command.appointment.ResumeSurveyCommand;
import vn.sunvibe.survey.core.application.query.appointment.SurveyCommandResult;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;

/**
 * Use Case - SurveyAppointmentCommandUseCase
 * <p>
 * The survey commands of staff (survey-schedule US-1..US-3, BR-11; spec 4.6, 7.1). Each runs in one transaction with
 * the lead move it needs, checks in the order of spec 5.5 and answers with the whole survey block.
 */
public interface SurveyAppointmentCommandUseCase {

    SurveyCommandResult book(SurveyViewer viewer, BookAppointmentCommand command);

    SurveyCommandResult change(SurveyViewer viewer, ChangeAppointmentCommand command);

    SurveyCommandResult setConfirmation(SurveyViewer viewer, ConfirmationCommand command);

    SurveyCommandResult cancel(SurveyViewer viewer, CancelAppointmentCommand command);

    SurveyCommandResult recordVisit(SurveyViewer viewer, RecordVisitCommand command);

    SurveyCommandResult resume(SurveyViewer viewer, ResumeSurveyCommand command);
}
