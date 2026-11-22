package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;

/**
 * The publication read over HTTP on PostgreSQL (listing-publish US-2 AC-7, US-5 AC-3, US-9 AC-3, BR-11; E06-S6 spec 3.6,
 * 4.2, E06S6-D11; PL-11): the live revision and the Approved revision waiting, each with its display answer as the unit
 * and E05 change, the permissions and the terms source; a listing archived by its state or by its agreement's end
 * answers ARCHIVED with nothing else. Needs Docker.
 */
class ListingPublicationReadApiPostgresTest extends ListingPublicationApiTestBase {

	@Override
	String phonePrefix() {
		return "0979";
	}

	/** PL-11: revision 1 live, revision 2 a retitled copy approved while the unit was OCCUPIED, so it waits. */
	@Test
	void publication_liveAndWaiting_holdsAndReasons() throws Exception {
		Drafting drafting = waitingApproval();
		String listingId = drafting.listingId();
		String unitId = drafting.chain().unitId();
		putLiveOk(listingId);
		assertEquals(201, copy(sale, listingId, "1", dataVersion(listingId)).status());
		Result saved = saveContent(sale, listingId, "2", contentBody(dataVersion(listingId), "Phòng trọ mới, gần biển Mỹ Khê",
				DESCRIPTION, everyFeature()));
		assertEquals(200, saved.status(), saved.rawBody());
		assertEquals(200, submit(sale, listingId, "2", dataVersion(listingId)).status());
		unitAvailability(unitId, "OCCUPIED");
		decided(reviewer, listingId, "2", approval(dataVersion(listingId)));

		List<String> occupied = answers(listingId);
		unitAvailability(unitId, "AVAILABLE");
		List<String> available = answers(listingId);
		suspended(manager, drafting.chain().agreementId());
		List<String> suspended = answers(listingId);

		String fixed = "PUBLISHED " + dataVersion(listingId) + " 1 2 IN_EFFECT true true";
		assertAll(
				() -> assertEquals(List.of(fixed, "live false [\"UNIT_NOT_AVAILABLE\"] []",
						"approved false [\"UNIT_NOT_AVAILABLE\"] []"), occupied),
				() -> assertEquals(List.of(fixed, "live true [] []", "approved true [] []"), available),
				() -> assertEquals(List.of(fixed, "live false [\"ADVERTISING_RIGHT\"] [\"SUSPENDED\"]",
						"approved false [\"ADVERTISING_RIGHT\"] [\"SUSPENDED\"]"), suspended));
	}

	/** PL-11, US-9 AC-3: archived by its stored state, then by its agreement's end: ARCHIVED, no revision, no right. */
	@Test
	void publication_archivedByStateOrEnd_archivedNothingElse() throws Exception {
		Drafting drafting = waitingApproval();
		String listingId = drafting.listingId();
		long version = dataVersion(listingId);

		jdbc.update("update listings set state = 'ARCHIVED' where id = ?", listingId);
		JsonNode byState = read(listingId);
		jdbc.update("update listings set state = 'UNPUBLISHED' where id = ?", listingId);
		Result ended = terminate(sale, drafting.chain().agreementId(), ownerWithdrew());
		assertEquals(204, ended.status(), ended.rawBody());
		JsonNode byEnd = read(listingId);

		String archived = "{\"listingId\":\"" + listingId + "\",\"state\":\"ARCHIVED\",\"dataVersion\":" + version
				+ ",\"live\":null,\"approved\":null,\"viewerMayPutLive\":false,\"viewerMayCopy\":false}";
		assertAll(
				() -> assertEquals(archived, byState.toString()),
				() -> assertEquals(archived, byEnd.toString()));
	}

	/**
	 * The read by the assigned Sale as three lines: "state dataVersion liveNo approvedNo termsSource mayPutLive mayCopy",
	 * then "live holds reasons groups" and "approved holds reasons groups".
	 */
	private List<String> answers(String listingId) throws Exception {
		JsonNode body = read(listingId);
		JsonNode live = body.path("live");
		JsonNode approved = body.path("approved");
		List<String> lines = new ArrayList<>();
		lines.add(body.path("state").asString() + " " + body.path("dataVersion").asLong() + " "
				+ live.path("revisionNo").asInt() + " " + approved.path("revisionNo").asInt() + " "
				+ approved.path("termsSource").asString() + " " + body.path("viewerMayPutLive").asBoolean() + " "
				+ body.path("viewerMayCopy").asBoolean());
		lines.add("live " + display(live.path("display")));
		lines.add("approved " + display(approved.path("display")));
		return lines;
	}

	private static String display(JsonNode display) {
		return display.path("holds").asBoolean() + " " + display.path("reasons") + " " + display.path("reasonGroups");
	}

	private JsonNode read(String listingId) throws Exception {
		Result answer = publication(sale, listingId);
		assertEquals(200, answer.status(), answer.rawBody());
		return answer.body();
	}
}
