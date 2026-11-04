package vn.sunvibe.survey.core.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.survey.core.application.shared.SurveyRecordReader;
import vn.sunvibe.survey.core.application.usecase.LeadSurveyState;
import vn.sunvibe.survey.core.application.usecase.SurveyStateUseCase;
import vn.sunvibe.survey.core.domain.policy.checklist.SurveyState;
import vn.sunvibe.survey.core.domain.repository.SurveyAppointmentDomainRepository;

/** Use Case Implementation - the survey record for the lead rules (survey-checklist submission spec 4.8, plan PD-3). */
@Service
public class SurveyStateService implements SurveyStateUseCase {

    private final SurveyRecordReader records;
    private final SurveyAppointmentDomainRepository appointments;

    public SurveyStateService(SurveyRecordReader records, SurveyAppointmentDomainRepository appointments) {
        this.records = records;
        this.appointments = appointments;
    }

    /** The lead commands that ask are open-lead moves, so a closed lead never reaches this question. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public LeadSurveyState surveyState(String leadId, int intentCycle) {
        SurveyState state = records.state(records.read(leadId, intentCycle, appointments.findByLeadId(leadId)),
                intentCycle, false);
        return new LeadSurveyState(state.hasPassed(), state.passedUsable(), state.hasSubmitted(), state.completable());
    }
}
