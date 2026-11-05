package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.ChecklistApiTestSupport.Visit;
import vn.sunvibe.survey.core.domain.policy.SurveyTimeRules;

/**
 * A real PASSED revision, built through the survey API, for tests outside this package (property-create intake): a lead of
 * the assignee, its visit, every readiness item filled with a usable photo on every required pair, submitted and passed
 * by the leader. Each space gets area 20 + its index, one bedroom and one private bathroom.
 */
public final class PassedRevisionFixture {

	/** A surveyed lead: its PASSED revision, the visit's arrival and the spaces in the order of the codes. */
	public record Passed(String leadId, String checklistId, List<String> spaceIds, List<String> photoIds) {
	}

	private final SurveyApiTestSupport survey;
	private final ChecklistApiTestSupport checklists;
	private final ChecklistReviewApiSupport review;
	private final JdbcTemplate jdbc;

	public PassedRevisionFixture(AcquisitionApiTestSupport api, JdbcTemplate jdbc, IdGenerator<String> ids,
			MediaTestStore store, String phonePrefix) {
		this.jdbc = jdbc;
		survey = new SurveyApiTestSupport(api, jdbc, phonePrefix);
		checklists = new ChecklistApiTestSupport(survey, jdbc, ids::generateId);
		review = new ChecklistReviewApiSupport(checklists, new MediaApiTestSupport(checklists, jdbc), store, jdbc);
	}

	public SurveyApiTestSupport survey() {
		return survey;
	}

	public ChecklistApiTestSupport checklists() {
		return checklists;
	}

	/** A whole asset of this type with one space. */
	public Passed whole(TestUser leader, TestUser sale, String assetType, String address) throws Exception {
		return passed(leader, sale, "WHOLE", assetType, address, null, null, "A1");
	}

	/** As {@link #whole} with a pin. */
	public Passed whole(TestUser leader, TestUser sale, String assetType, String address, BigDecimal latitude,
			BigDecimal longitude) throws Exception {
		return passed(leader, sale, "WHOLE", assetType, address, latitude, longitude, "A1");
	}

	/** An asset surveyed in parts, one space per code. */
	public Passed parts(TestUser leader, TestUser sale, String assetType, String address, String... codes)
			throws Exception {
		return passed(leader, sale, "PARTS", assetType, address, null, null, codes);
	}

	/** As {@link #parts} with the amenity seen on the space of this index and nowhere else. */
	public Passed partsWithAmenity(TestUser leader, TestUser sale, String assetType, String address, String amenity,
			int spaceIndex, String... codes) throws Exception {
		Visit visit = checklists.visited(leader, sale);
		return finish(leader, sale, visit, "PARTS", assetType, address, null, null, amenity, spaceIndex, codes);
	}

	/** Withdraws the revision from use, as a manager does; the answer must be 200. */
	public void requalify(TestUser manager, String checklistId, String reasonKind) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedVersion", review.version(checklistId));
		body.put("reasonKind", reasonKind);
		body.put("reason", "The revision no longer stands");
		body.put("eventSource", "The owner called");
		body.put("scope", "The whole revision");
		body.put("basisDate", LocalDate.now(SurveyTimeRules.ZONE).toString());
		SurveyApiTestSupport.ok(checklists.api().post(manager,
				ChecklistApiTestSupport.CHECKLISTS + "/" + checklistId + "/requalification", body));
	}

	/** A new visit and a new PASSED on a lead whose earlier revision was withdrawn (the lead stands at CONTACTED). */
	public Passed revisit(TestUser leader, TestUser sale, String leadId, String scope, String assetType, String address,
			String... codes) throws Exception {
		Instant start = SurveyApiTestSupport.startIn(24);
		Map<String, Object> booking = new LinkedHashMap<>(survey.booking(leadId, start));
		booking.put("resurveyReason", "The requalification asks for a new visit");
		Result booked = survey.book(sale, leadId, booking);
		assertEquals(201, booked.status(), booked.rawBody());
		String appointmentId = booked.body().path("survey").path("appointments").path(0).path("id").asString();
		Duration back = Duration.ofDays(2).plusHours(1);
		Instant arrivedAt = start.minus(back);
		survey.shiftBack(appointmentId, back);
		SurveyApiTestSupport.ok(checklists.recordVisit(sale, leadId, appointmentId, arrivedAt));
		return finish(leader, sale, new Visit(leadId, appointmentId, arrivedAt), scope, assetType, address, null, null,
				null, -1, codes);
	}

	private Passed passed(TestUser leader, TestUser sale, String scope, String assetType, String address,
			BigDecimal latitude, BigDecimal longitude, String... codes) throws Exception {
		Visit visit = checklists.visited(leader, sale);
		return finish(leader, sale, visit, scope, assetType, address, latitude, longitude, null, -1, codes);
	}

	private Passed finish(TestUser leader, TestUser sale, Visit visit, String scope, String assetType, String address,
			BigDecimal latitude, BigDecimal longitude, String amenityOrNull, int amenitySpace, String... codes)
			throws Exception {
		String id = checklists.started(sale, visit.appointmentId());
		List<String> spaces = new ArrayList<>();
		for (int i = 0; i < codes.length; i++) {
			spaces.add(checklists.newId());
		}
		fill(sale, id, scope, assetType, address, latitude, longitude, spaces, codes);
		if (amenityOrNull != null) {
			checklists.saved(sale, id, "amenities", c -> ((ArrayNode) c.path("spaces").path(amenitySpace).path("items"))
					.add(amenity(amenityOrNull)));
		}
		List<String> photos = photos(sale, id, scope, spaces, codes);
		review.submitted(sale, id);
		review.passed(leader, id);
		return new Passed(visit.leadId(), id, List.copyOf(spaces), photos);
	}

	private void fill(TestUser sale, String id, String scope, String assetType, String address, BigDecimal latitude,
			BigDecimal longitude, List<String> spaces, String[] codes) throws Exception {
		boolean parts = "PARTS".equals(scope);
		checklists.saved(sale, id, "identification", c -> {
			ObjectNode[] observed = new ObjectNode[codes.length];
			for (int i = 0; i < codes.length; i++) {
				observed[i] = checklists.space(spaces.get(i), codes[i]);
				observed[i].put("location", "Floor " + (i + 1));
			}
			c.setAll(checklists.identification(scope, observed));
			c.set("observedAddress", checklists.sourced(checklists.place(address, SurveyApiTestSupport.WARD, latitude,
					longitude), "OBSERVED_ON_SITE"));
			c.set("propertyType", checklists.value(assetType));
			// The lead's own address differs from the one seen here, so G1 needs the note
			c.put("differenceNote", "The survey address replaces the one on the form");
		});
		checklists.saved(sale, id, "parts", c -> {
			ChecklistApiTestSupport.set(c, "accessRoute", checklists.value("Alley next to the market"));
			ChecklistApiTestSupport.set(c, "sharedAreas", checklists.notApplicable("Nothing shared", false));
			for (int i = 0; i < codes.length; i++) {
				ChecklistApiTestSupport.set(c, "spaces." + i + ".floor", checklists.value("Floor " + (i + 1)));
				ChecklistApiTestSupport.set(c, "spaces." + i + ".privateAreas", checklists.value("The room"));
				ObjectNode access = checklists.object();
				access.put("extent", "ENTERED_ALL");
				access.putNull("note");
				ChecklistApiTestSupport.set(c, "spaces." + i + ".actualAccess", checklists.value(access));
			}
		});
		checklists.saved(sale, id, "dimensions", c -> {
			for (int i = 0; i < codes.length; i++) {
				ChecklistApiTestSupport.set(c, "spaces." + i + ".area",
						checklists.sourced(new BigDecimal(20 + i), "MEASURED"));
				ChecklistApiTestSupport.set(c, "spaces." + i + ".bedrooms", checklists.sourced(1, "OBSERVED_ON_SITE"));
				ChecklistApiTestSupport.set(c, "spaces." + i + ".privateBathrooms",
						checklists.sourced(1, "OBSERVED_ON_SITE"));
				ChecklistApiTestSupport.set(c, "spaces." + i + ".sharedBathroomUse",
						checklists.sourced("NO", "OBSERVED_ON_SITE"));
			}
		});
		checklists.saved(sale, id, "condition", c -> {
			if (parts) {
				ChecklistApiTestSupport.set(c, "sharedKitchen",
						checklists.value(checklists.areaCheck("INSPECTED", true, null, null)));
				ChecklistApiTestSupport.set(c, "sharedBathroom",
						checklists.value(checklists.areaCheck("INSPECTED", true, null, null)));
			}
			for (int i = 0; i < codes.length; i++) {
				ChecklistApiTestSupport.set(c, "spaces." + i + ".occupancy", checklists.value("VACANT"));
				for (String area : List.of("mainSpace", "kitchen", "bathroom")) {
					ChecklistApiTestSupport.set(c, "spaces." + i + "." + area,
							checklists.value(checklists.areaCheck("INSPECTED", true, null, null)));
				}
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

	private List<String> photos(TestUser sale, String id, String scope, List<String> spaces, String[] codes) {
		List<String> photos = new ArrayList<>();
		photos.add(review.photo(sale, id, null, null, "EXTERIOR_ACCESS"));
		if ("PARTS".equals(scope)) {
			photos.add(review.photo(sale, id, null, null, "SHARED_KITCHEN"));
			photos.add(review.photo(sale, id, null, null, "SHARED_BATHROOM"));
		}
		for (int i = 0; i < codes.length; i++) {
			for (String category : List.of("MAIN_SPACE", "KITCHEN", "BATHROOM")) {
				photos.add(review.photo(sale, id, spaces.get(i), codes[i], category));
			}
		}
		return photos;
	}

	private ObjectNode amenity(String code) {
		ObjectNode item = checklists.object();
		item.put("code", code);
		item.put("presence", "YES");
		item.put("source", "OBSERVED_ON_SITE");
		item.putNull("confirmedOn");
		item.putNull("receivedByUserId");
		item.putNull("relayBasis");
		return item;
	}

	public JdbcTemplate jdbc() {
		return jdbc;
	}
}
