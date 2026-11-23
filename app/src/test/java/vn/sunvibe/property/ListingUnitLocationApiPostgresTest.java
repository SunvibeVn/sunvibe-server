package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.property.core.application.usecase.intake.UnitLocationUseCase;
import vn.sunvibe.property.core.application.usecase.intake.UnitLocationView;

/**
 * The real location over HTTP on PostgreSQL (listing-publish US-4 AC-2; E06-S4 spec 3.4, 4.5, E06S4-D12; RV-5): an
 * eligible reviewer gets the unit's code, its location label, the street and the ward, and one access log row with no
 * location value; a manager without the reviewer role, a decided revision and an ended agreement are refused with no
 * row; the location reaches no log line. Needs Docker.
 */
@ExtendWith(OutputCaptureExtension.class)
class ListingUnitLocationApiPostgresTest extends ListingReviewApiTestBase {

	@Autowired
	UnitLocationUseCase unitLocations;

	@Override
	String phonePrefix() {
		return "0905";
	}

	/** RV-5: the four fields; one row with user, listing, revision, action and time; no column holds the location. */
	@Test
	void unitLocation_eligible_locationAndOneLogRow() throws Exception {
		Drafting drafting = pendingDrafting();
		UnitLocationView location = unitLocations.locate(List.of(drafting.chain().unitId())).getFirst();
		assertNotNull(location.streetOrNull());
		Instant before = Instant.now().truncatedTo(ChronoUnit.MICROS);

		Result answer = openLocation(reviewer, drafting.listingId(), "1");

		Instant after = Instant.now();
		assertEquals(200, answer.status(), answer.rawBody());
		assertEquals(Stream.of(location.unitCodeOrNull(), location.locationLabelOrNull(), location.streetOrNull(),
				location.wardNameOrNull()).map(String::valueOf).toList(),
				List.of(answer.body().path("unitCode").asString("null"),
						answer.body().path("locationLabel").asString("null"),
						answer.body().path("street").asString("null"), answer.body().path("wardName").asString("null")));
		Map<String, Object> row = jdbc.queryForMap("select * from listing_access_logs where listing_id = ?",
				drafting.listingId());
		assertEquals(List.of(reviewer.userId(), drafting.listingId(), 1, "UNIT_LOCATION_OPENED"),
				List.of(row.get("user_id"), row.get("listing_id"), row.get("revision_no"), row.get("action")));
		Instant accessedAt = ((Timestamp) row.get("accessed_at")).toInstant();
		assertFalse(accessedAt.isBefore(before) || accessedAt.isAfter(after), accessedAt.toString());
		String text = jdbc.queryForObject("select a::text from listing_access_logs a where listing_id = ?", String.class,
				drafting.listingId());
		assertEquals(List.of(), Stream.of(location.streetOrNull(), location.locationLabelOrNull())
				.filter(value -> value != null && text.contains(value)).toList());
		assertEquals(1, accessLogRows(drafting.listingId()));
	}

	/** RV-5, E06S4-D2: the managers without the reviewer role get 403 FORBIDDEN; no row. */
	@Test
	void unitLocation_managerWithoutReviewerRole_forbiddenNoLog() throws Exception {
		String listingId = pending();
		Written before = written();

		List<String> answers = new ArrayList<>();
		for (TestUser caller : List.of(manager, leader, sale)) {
			answers.add(codeOf(openLocation(caller, listingId, "1")));
		}

		assertEquals(List.of("403 FORBIDDEN", "403 FORBIDDEN", "403 FORBIDDEN"), answers);
		assertEquals(before, written());
	}

	/** RV-5, E06S4-D5 step 7: a decided revision answers REVISION_NOT_PENDING to an independent reviewer; no row. */
	@Test
	void unitLocation_decidedRevision_notPendingNoLog() throws Exception {
		String listingId = pending();
		decided(reviewer, listingId, "1", returnBody(dataVersion(listingId), List.of("OTHER"), NOTE));
		Written before = written();

		Result answer = openLocation(reviewer, listingId, "1");

		assertCode(409, "REVISION_NOT_PENDING", answer);
		assertEquals(before, written());
	}

	/** RV-5, RV-12: a pending revision of an ended agreement answers LISTING_ARCHIVED; no row. */
	@Test
	void unitLocation_endedAgreement_listingArchivedNoLog() throws Exception {
		Drafting drafting = pendingDrafting();
		Result termination = terminate(sale, drafting.chain().agreementId(), unitLet());
		assertEquals(204, termination.status(), termination.rawBody());
		Written before = written();

		Result answer = openLocation(reviewer, drafting.listingId(), "1");

		assertCode(409, "LISTING_ARCHIVED", answer);
		assertEquals(before, written());
	}

	/** RV-5, E06S4-D20: after a 200, no log line holds the street or the label. */
	@Test
	void unitLocation_location_neverInLog(CapturedOutput output) throws Exception {
		Drafting drafting = pendingDrafting();
		UnitLocationView location = unitLocations.locate(List.of(drafting.chain().unitId())).getFirst();

		Result answer = openLocation(reviewer, drafting.listingId(), "1");

		assertEquals(200, answer.status(), answer.rawBody());
		assertEquals(List.of(), Stream.of(location.streetOrNull(), location.locationLabelOrNull())
				.filter(value -> value != null && output.getAll().contains(value)).toList());
	}
}
