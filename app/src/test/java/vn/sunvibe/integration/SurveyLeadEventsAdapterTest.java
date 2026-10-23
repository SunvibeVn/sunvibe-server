package vn.sunvibe.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import vn.sunvibe.acquisition.core.application.shared.external.survey.LeadSurveyStop;
import vn.sunvibe.acquisition.core.application.shared.external.survey.SurveyAppointmentRef;
import vn.sunvibe.survey.core.application.usecase.AppointmentRef;
import vn.sunvibe.survey.core.application.usecase.LeadStopReason;
import vn.sunvibe.survey.core.application.usecase.LeadSurveyEventsUseCase;

/**
 * The adapter forwards acquisition's lead hooks to survey's published use case (survey-schedule spec 5.4) and
 * translates the stop reason and the appointment reference between the two modules' types.
 */
class SurveyLeadEventsAdapterTest {

	private static final String LEAD = "01LEAD00000000000000000000";
	private static final String ACTOR = "01ACTOR0000000000000000000";
	private static final String NEW_ASSIGNEE = "01NEWASSIGNEE0000000000000";
	private static final Instant AT = Instant.parse("2026-10-23T05:00:00Z");
	private static final AppointmentRef REF = new AppointmentRef("01APPOINTMENT0000000000000", 3);

	private final List<String> calls = new ArrayList<>();
	private Optional<AppointmentRef> answer = Optional.of(REF);
	private final SurveyLeadEventsAdapter adapter = new SurveyLeadEventsAdapter(new LeadSurveyEventsUseCase() {
		@Override
		public Optional<AppointmentRef> onReassigned(String leadId, String newAssigneeUserId, String actorUserId,
				Instant at) {
			calls.add("reassigned:" + leadId + ":" + newAssigneeUserId + ":" + actorUserId + ":" + at);
			return answer;
		}

		@Override
		public Optional<AppointmentRef> onHeldOrClosed(String leadId, LeadStopReason reason, String actorUserId,
				String textOrNull, Instant at) {
			calls.add("stopped:" + leadId + ":" + reason + ":" + actorUserId + ":" + textOrNull + ":" + at);
			return answer;
		}

		@Override
		public Optional<AppointmentRef> onIntentChanged(String leadId, int newIntentCycle, String actorUserId,
				Instant at) {
			calls.add("intent:" + leadId + ":" + newIntentCycle + ":" + actorUserId + ":" + at);
			return answer;
		}
	});

	@Test
	void onReassigned_forwardsAndTranslatesTheReference() {
		assertEquals(Optional.of(new SurveyAppointmentRef(REF.appointmentId(), 3)),
				adapter.onReassigned(LEAD, NEW_ASSIGNEE, ACTOR, AT));
		assertEquals(List.of("reassigned:" + LEAD + ":" + NEW_ASSIGNEE + ":" + ACTOR + ":" + AT), calls);
	}

	@Test
	void onHeldOrClosed_translatesEachStopReason() {
		adapter.onHeldOrClosed(LEAD, LeadSurveyStop.LEAD_ON_HOLD, ACTOR, "Owner abroad", AT);
		adapter.onHeldOrClosed(LEAD, LeadSurveyStop.LEAD_CLOSED, ACTOR, null, AT);

		assertEquals(List.of("stopped:" + LEAD + ":LEAD_ON_HOLD:" + ACTOR + ":Owner abroad:" + AT,
				"stopped:" + LEAD + ":LEAD_CLOSED:" + ACTOR + ":null:" + AT), calls);
	}

	@Test
	void onIntentChanged_forwardsTheNewCycle() {
		assertEquals(Optional.of(new SurveyAppointmentRef(REF.appointmentId(), 3)),
				adapter.onIntentChanged(LEAD, 2, ACTOR, AT));
		assertEquals(List.of("intent:" + LEAD + ":2:" + ACTOR + ":" + AT), calls);
	}

	@Test
	void eachHook_nothingChanged_answersEmpty() {
		answer = Optional.empty();

		assertEquals(Optional.empty(), adapter.onReassigned(LEAD, NEW_ASSIGNEE, ACTOR, AT));
		assertEquals(Optional.empty(), adapter.onHeldOrClosed(LEAD, LeadSurveyStop.LEAD_CLOSED, ACTOR, null, AT));
		assertEquals(Optional.empty(), adapter.onIntentChanged(LEAD, 2, ACTOR, AT));
	}
}
