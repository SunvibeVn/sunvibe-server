package vn.sunvibe.acquisition.core.testsupport;

import vn.sunvibe.acquisition.core.application.shared.external.survey.LeadSurveyPort;
import vn.sunvibe.acquisition.core.application.shared.external.survey.LeadSurveyStop;
import vn.sunvibe.acquisition.core.application.shared.external.survey.SurveyAppointmentRef;
import vn.sunvibe.acquisition.core.application.shared.external.survey.SurveyState;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;
import vn.sunvibe.acquisition.core.domain.repository.OwnerLeadDomainRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Hand-written fake of the survey hooks: records each call with the lead version stored when it came (so a test sees
 * that the lead was saved first) and answers with the appointment reference it was given (empty by default).
 */
public class RecordingLeadSurveyPort implements LeadSurveyPort {

    /** One hook call: which hook, the arguments, and the stored lead version at that moment. */
    public record Call(String hook, String leadId, String argument, String actorUserId, Instant at,
                       Long storedVersion) {
    }

    private final OwnerLeadDomainRepository leads;
    private final List<Call> calls = new ArrayList<>();
    private final List<Call> stateQuestions = new ArrayList<>();
    private Optional<SurveyAppointmentRef> answer = Optional.empty();
    private SurveyState state = SurveyState.NONE;
    public RuntimeException failNextCall;

    public RecordingLeadSurveyPort(OwnerLeadDomainRepository leads) {
        this.leads = leads;
    }

    public RecordingLeadSurveyPort answering(String appointmentId, int appointmentNo) {
        this.answer = Optional.of(new SurveyAppointmentRef(appointmentId, appointmentNo));
        return this;
    }

    public RecordingLeadSurveyPort withState(SurveyState surveyState) {
        this.state = surveyState;
        return this;
    }

    /** Each question about the survey record, with the stored lead version at that moment. */
    public List<Call> stateQuestions() {
        return List.copyOf(stateQuestions);
    }

    public List<Call> calls() {
        return List.copyOf(calls);
    }

    @Override
    public SurveyState surveyState(String leadId, int intentCycle) {
        stateQuestions.add(new Call("surveyState", leadId, String.valueOf(intentCycle), null, null,
                leads.findById(OwnerLeadId.of(leadId)).map(lead -> lead.getVersion()).orElse(null)));
        return state;
    }

    @Override
    public Optional<SurveyAppointmentRef> onReassigned(String leadId, String newAssigneeUserId, String actorUserId,
                                                       Instant at) {
        return record("reassigned", leadId, newAssigneeUserId, actorUserId, at);
    }

    @Override
    public Optional<SurveyAppointmentRef> onHeldOrClosed(String leadId, LeadSurveyStop stop, String actorUserId,
                                                         String textOrNull, Instant at) {
        return record(stop.name(), leadId, textOrNull, actorUserId, at);
    }

    @Override
    public Optional<SurveyAppointmentRef> onIntentChanged(String leadId, int newIntentCycle, String actorUserId,
                                                          Instant at) {
        return record("intentChanged", leadId, String.valueOf(newIntentCycle), actorUserId, at);
    }

    private Optional<SurveyAppointmentRef> record(String hook, String leadId, String argument, String actor,
                                                  Instant at) {
        Long version = leads.findById(OwnerLeadId.of(leadId)).map(lead -> lead.getVersion()).orElse(null);
        calls.add(new Call(hook, leadId, argument, actor, at, version));
        if (failNextCall != null) {
            RuntimeException failure = failNextCall;
            failNextCall = null;
            throw failure;
        }
        return answer;
    }
}
