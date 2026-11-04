package vn.sunvibe.survey;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.survey.ChecklistApiTestSupport.Visit;

/**
 * What the submission and review API tests share (survey-checklist submission spec 7.1): a revision that can be
 * submitted, with every readiness item filled and a usable photo on every required pair, and the HTTP calls.
 */
final class ChecklistReviewApiSupport {

	static final String CHECKLISTS = ChecklistApiTestSupport.CHECKLISTS;

	private final ChecklistApiTestSupport checklists;
	private final MediaApiTestSupport media;
	private final MediaTestStore store;
	private final JdbcTemplate jdbc;
	private int seed = 5000;

	ChecklistReviewApiSupport(ChecklistApiTestSupport checklists, MediaApiTestSupport media, MediaTestStore store,
			JdbcTemplate jdbc) {
		this.checklists = checklists;
		this.media = media;
		this.store = store;
		this.jdbc = jdbc;
	}

	/** A revision ready to submit: its visit, its id, its one space and its four photos. */
	record Ready(Visit visit, String checklistId, String spaceId, List<String> photos) {

		String leadId() {
			return visit.leadId();
		}
	}

	/** The assignee's revision with every needed field filled and a usable photo on every required pair. */
	Ready ready(TestUser leader, TestUser sale) throws Exception {
		Visit visit = checklists.visited(leader, sale);
		String id = checklists.started(sale, visit.appointmentId());
		String space = checklists.newId();
		fill(sale, id, space);
		return new Ready(visit, id, space, photos(sale, id, space));
	}

	/** Every needed field of a WHOLE record with the one space. */
	void fill(TestUser sale, String id, String space) throws Exception {
		checklists.saved(sale, id, "identification", c -> {
			c.setAll(checklists.identification("WHOLE", checklists.space(space, "A1")));
			c.put("differenceNote", "The house number on the form is the old one");
		});
		checklists.saved(sale, id, "parts", c -> {
			ChecklistApiTestSupport.set(c, "accessRoute", checklists.value("Alley next to the market"));
			ChecklistApiTestSupport.set(c, "sharedAreas", checklists.notApplicable("Nothing shared", false));
			ChecklistApiTestSupport.set(c, "spaces.0.floor", checklists.value("Ground"));
			ChecklistApiTestSupport.set(c, "spaces.0.privateAreas", checklists.value("Whole house"));
			ObjectNode access = checklists.object();
			access.put("extent", "ENTERED_ALL");
			access.putNull("note");
			ChecklistApiTestSupport.set(c, "spaces.0.actualAccess", checklists.value(access));
		});
		checklists.saved(sale, id, "dimensions", c -> {
			ChecklistApiTestSupport.set(c, "spaces.0.area", checklists.sourced(new BigDecimal("80.5"), "MEASURED"));
			ChecklistApiTestSupport.set(c, "spaces.0.bedrooms", checklists.sourced(3, "OBSERVED_ON_SITE"));
			ChecklistApiTestSupport.set(c, "spaces.0.privateBathrooms", checklists.sourced(2, "OBSERVED_ON_SITE"));
			ChecklistApiTestSupport.set(c, "spaces.0.sharedBathroomUse", checklists.sourced("NO", "OBSERVED_ON_SITE"));
		});
		checklists.saved(sale, id, "condition", c -> {
			ChecklistApiTestSupport.set(c, "spaces.0.occupancy", checklists.value("VACANT"));
			for (String area : List.of("mainSpace", "kitchen", "bathroom")) {
				ChecklistApiTestSupport.set(c, "spaces.0." + area,
						checklists.value(checklists.areaCheck("INSPECTED", true, null, null)));
			}
		});
		checklists.saved(sale, id, "safety", c -> {
			ChecklistApiTestSupport.set(c, "egress", checklists.value("Front door and back door"));
			ObjectNode none = checklists.object();
			none.put("noneObserved", true);
			none.putNull("note");
			ChecklistApiTestSupport.set(c, "accessRestrictions", checklists.value(none));
			ChecklistApiTestSupport.set(c, "hazards", checklists.value(List.of()));
		});
	}

	/** A READY photo with stored variants on every required pair of the one space. */
	List<String> photos(TestUser uploader, String id, String space) {
		return List.of(photo(uploader, id, null, null, "EXTERIOR_ACCESS"), photo(uploader, id, space, "A1", "MAIN_SPACE"),
				photo(uploader, id, space, "A1", "KITCHEN"), photo(uploader, id, space, "A1", "BATHROOM"));
	}

	String photo(TestUser uploader, String id, String spaceOrNull, String codeOrNull, String category) {
		seed += 3;
		String mediaId = media.readyWithVariants(store, id, uploader.userId(), MediaApiTestSupport.bytes(3000, seed),
				MediaApiTestSupport.bytes(2000, seed + 1), MediaApiTestSupport.bytes(1000, seed + 2));
		jdbc.update("update survey_media set category = ?, space_id = ?, space_code_at_upload = ? where id = ?", category,
				spaceOrNull, codeOrNull, mediaId);
		return mediaId;
	}

	long version(String checklistId) {
		return jdbc.queryForObject("select version from survey_checklists where id = ?", Long.class, checklistId);
	}

	Result submit(TestUser caller, String checklistId) throws Exception {
		return submit(caller, checklistId, version(checklistId));
	}

	Result submit(TestUser caller, String checklistId, Long expectedVersion) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedVersion", expectedVersion);
		return checklists.api().post(caller, CHECKLISTS + "/" + checklistId + "/submission", body);
	}

	/** A decision without a kind or text, as sent. */
	Result decide(TestUser reviewer, String checklistId, String outcome) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedVersion", version(checklistId));
		body.put("outcome", outcome);
		body.put("rejectedEvidence", List.of());
		return checklists.api().post(reviewer, CHECKLISTS + "/" + checklistId + "/decision", body);
	}

	/** The reviewer passes the revision; the answer must be 200. */
	JsonNode passed(TestUser reviewer, String checklistId) throws Exception {
		Result answer = decide(reviewer, checklistId, "PASSED");
		SurveyApiTestSupport.ok(answer);
		return answer.body();
	}

	/** The reviewer returns the revision; the answer must be 200. */
	JsonNode needsChanges(TestUser reviewer, String checklistId, String kind) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedVersion", version(checklistId));
		body.put("outcome", "NEEDS_CHANGES");
		body.put("needsChangesKind", kind);
		body.put("reason", "Bring what is missing");
		body.put("rejectedEvidence", List.of());
		Result answer = checklists.api().post(reviewer, CHECKLISTS + "/" + checklistId + "/decision", body);
		SurveyApiTestSupport.ok(answer);
		return answer.body();
	}

	/** The revision as submitted by the caller; the answer must be 200. */
	JsonNode submitted(TestUser caller, String checklistId) throws Exception {
		Result answer = submit(caller, checklistId);
		SurveyApiTestSupport.ok(answer);
		return answer.body();
	}
}
