package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.brokerage.AgreementFixture;
import vn.sunvibe.survey.PassedRevisionFixture.Passed;
import vn.sunvibe.survey.SurveyApiTestSupport;

/**
 * Sending to owners switched off (brokerage-agreement BR-19, evidence gap Q-1 / SV-180; E05-S4 spec S4-D8, SD-7): the
 * send of a complete eligible draft answers 409 AGREEMENT_SENDING_NOT_AVAILABLE and writes nothing, every other answer
 * of the send comes first, and drafting and reading work as with the switch on. The annotations of
 * {@link AgreementDraftApiPostgresTest} plus the switch, so this class has a context of its own (E05-S4 spec A-3).
 * Needs Docker.
 */
@TestPropertySource(properties = "app.brokerage.owner-sending.enabled=false")
class AgreementSendSwitchOffApiPostgresTest extends IntakeApiTestBase {

	private static final String AGREEMENTS = "/api/v1/staff/brokerage-agreements";

	private final List<String> created = new ArrayList<>();
	private final List<String> owners = new ArrayList<>();

	@Override
	String phonePrefix() {
		return "0941";
	}

	@AfterAll
	void deleteAgreementsAndNotices() {
		AgreementFixture.delete(jdbc, created);
		for (String owner : owners) {
			jdbc.update("delete from auth_email_notifications where user_id = ?", owner);
		}
	}

	private record Done(TestUser owner, String agreementId) {
	}

	/** A house lead of {@code sale} with a completed intake, and the Sale's draft on its unit, not edited yet. */
	private Done drafted() throws Exception {
		Passed passed = house(unique("Switch Street"));
		TestUser owner = owner();
		owners.add(owner.userId());
		checked(passed, owner);
		SurveyApiTestSupport.ok(createTarget(passed.leadId(), null));
		available(passed.leadId(), passed.spaceIds(), "VACANT");
		completed(passed.leadId());
		String unit = blockOk(sale, passed.leadId()).path("readiness").path("units").path(0).path("unitId").asString();
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("leadId", passed.leadId());
		body.put("unitId", unit);
		body.put("transactionType", "LONG_TERM_RENT");
		body.put("startDate", "2027-01-01");
		body.put("endDate", "2027-12-31");
		Result answer = api.post(sale, AGREEMENTS, body);
		assertEquals(201, answer.status(), answer.rawBody());
		String id = answer.body().path("agreementId").asString();
		created.add(id);
		return new Done(owner, id);
	}

	private Result send(String agreementId, long expectedDataVersion) throws Exception {
		return api.post(sale, AGREEMENTS + "/" + agreementId + "/draft/submission",
				Map.of("expectedDataVersion", expectedDataVersion));
	}

	private Map<String, Object> written(Done done) {
		Map<String, Object> written = new LinkedHashMap<>();
		written.put("agreement", jdbc.queryForMap("select * from brokerage_agreements where id = ?", done.agreementId()));
		written.put("version", jdbc.queryForMap("select * from brokerage_agreement_versions where agreement_id = ?",
				done.agreementId()));
		written.put("events", jdbc.queryForList("select event_type from brokerage_agreement_events where agreement_id = ?",
				String.class, done.agreementId()));
		written.put("notices", jdbc.queryForObject("select count(*) from auth_email_notifications where user_id = ?"
				+ " and kind = 'AGREEMENT_PENDING'", Integer.class, done.owner().userId()));
		return written;
	}

	@Test
	void send_switchOff_notAvailableNothingWritten() throws Exception {
		Done done = drafted();
		assertEquals(204, api.put(sale, AGREEMENTS + "/" + done.agreementId() + "/draft",
				AgreementFeePolicyApiPostgresTest.complete(0)).status());
		Map<String, Object> before = written(done);

		Result answer = send(done.agreementId(), 1);

		assertEquals(409, answer.status(), answer.rawBody());
		assertEquals("AGREEMENT_SENDING_NOT_AVAILABLE", answer.body().path("code").asString(), answer.rawBody());
		assertEquals("Sending to owners is not open on this server.", answer.body().path("message").asString());
		assertEquals("[]", answer.body().path("errors").toString(), answer.rawBody());
		assertEquals(before, written(done));
		assertEquals(0, before.get("notices"));
	}

	/** Spec S4-D6: the switch answers last, so the Sale still learns what to fix while sending is off. */
	@Test
	void send_switchOffIncomplete_incompleteFirst() throws Exception {
		Done done = drafted();
		Map<String, Object> before = written(done);

		Result answer = send(done.agreementId(), 0);

		assertEquals(409, answer.status(), answer.rawBody());
		assertEquals("AGREEMENT_VERSION_INCOMPLETE", answer.body().path("code").asString(), answer.rawBody());
		assertEquals(11, answer.body().path("errors").size(), answer.rawBody());
		assertEquals(before, written(done));
	}

	@Test
	void draftAndDetail_switchOff_asWithTheSwitchOn() throws Exception {
		Done done = drafted();

		Result saved = api.put(sale, AGREEMENTS + "/" + done.agreementId() + "/draft",
				AgreementFeePolicyApiPostgresTest.complete(0));
		Result detail = api.get(sale, AGREEMENTS + "/" + done.agreementId());

		assertEquals(204, saved.status(), saved.rawBody());
		assertEquals(200, detail.status(), detail.rawBody());
		assertEquals("DRAFTING", detail.body().path("status").asString());
		JsonNode version = detail.body().path("versions").path(0);
		assertEquals("DRAFT", version.path("status").asString());
		assertEquals("PERCENT_OF_RENT", version.path("feePolicy").path("formula").path("type").asString(),
				detail.rawBody());
		assertEquals(true, version.path("sentAt").isNull(), detail.rawBody());
		assertEquals(1L, detail.body().path("dataVersion").asLong());
	}
}
