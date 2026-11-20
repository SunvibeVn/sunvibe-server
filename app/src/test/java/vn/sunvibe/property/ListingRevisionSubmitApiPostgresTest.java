package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;

/**
 * Submitting, withdrawing and starting revisions over HTTP (listing-publish US-2 AC-4 to AC-6, BR-2, BR-4, BR-6, E-4;
 * E06-S2 spec 3.1, 3.4 to 3.6; RD-4 to RD-8, RD-10, RD-11): every violation at once, the machine check without echoing
 * a match, the unit snapshot of the submission, the lock of a Pending review revision, the withdrawal and the next
 * Draft, the data version, and the archive of an ended agreement. Needs Docker.
 */
@ExtendWith(OutputCaptureExtension.class)
class ListingRevisionSubmitApiPostgresTest extends ListingRevisionApiTestBase {

	@Override
	String phonePrefix() {
		return "0911";
	}

	/** RD-4: missing and short parts answer 422 with every entry in order; the revision stays a Draft. */
	@Test
	void submit_missingOrShort_unprocessableAllEntriesInOrderStaysDraft() throws Exception {
		String listingId = listingInEffect().listingId();
		RevisionRows before = revisionRows(listingId);

		Result empty = submit(sale, listingId, "1", dataVersion(listingId));

		assertCode(422, "REVISION_NOT_SUBMITTABLE", empty);
		assertEquals(List.of("title.REQUIRED", "description.REQUIRED", "terms.REQUIRED"), fields(empty));
		assertEquals("The revision cannot be submitted.", empty.body().path("message").asString());
		assertEquals(before, revisionRows(listingId));
		assertEquals(200, saveContent(sale, listingId, "1", contentBody(dataVersion(listingId), "a".repeat(14),
				"a".repeat(99), everyFeature())).status());
		RevisionRows saved = revisionRows(listingId);

		Result shortParts = submit(sale, listingId, "1", dataVersion(listingId));

		assertCode(422, "REVISION_NOT_SUBMITTABLE", shortParts);
		assertEquals(List.of("title.TOO_SHORT", "description.TOO_SHORT", "terms.REQUIRED"), fields(shortParts));
		assertEquals(saved, revisionRows(listingId));
		assertEquals("DRAFT", getRevision(sale, listingId, "1").body().path("status").asString());
	}

	/** RD-5: each vector answers its rule; neither the error, the output nor the history holds the matched text. */
	@Test
	void submit_machineRuleVectors_entryEachNoTextAnywhere(CapturedOutput output) throws Exception {
		String listingId = listingInEffect().listingId();
		assertEquals(200, captureTerms(sale, listingId, "1", dataVersion(listingId), "IN_EFFECT").status());
		Map<String, String> vectors = new LinkedHashMap<>();
		vectors.put("0905.123.456", "PHONE_NUMBER");
		vectors.put("0905 - 123 - 456", "PHONE_NUMBER");
		vectors.put("0905–123–456", "PHONE_NUMBER");
		vectors.put("0905_123_456", "PHONE_NUMBER");
		vectors.put("0905.12.10.26", "PHONE_NUMBER");
		vectors.put("nhatro@gmail.com", "EMAIL");
		vectors.put("www.abc.vn", "LINK");
		vectors.put("zalo", "SOCIAL_HANDLE");
		vectors.put("chính chủ", "FORBIDDEN_WORD");

		for (Map.Entry<String, String> vector : vectors.entrySet()) {
			assertEquals(200, saveContent(sale, listingId, "1", contentBody(dataVersion(listingId), TITLE,
					DESCRIPTION + " Liên hệ " + vector.getKey() + " nhé.", everyFeature())).status(), vector.getKey());

			Result refused = submit(sale, listingId, "1", dataVersion(listingId));

			assertCode(422, "REVISION_NOT_SUBMITTABLE", refused);
			assertEquals(List.of("description." + vector.getValue()), fields(refused), vector.getKey());
			assertFalse(refused.rawBody().contains(vector.getKey()), refused.rawBody());
		}
		String history = String.join(" ", jdbc.queryForList("select h::text from listing_history h where listing_id = ?",
				String.class, listingId));
		for (String vector : vectors.keySet()) {
			assertFalse(output.getAll().contains(vector), vector);
			assertFalse(history.contains(vector), vector);
		}
		assertEquals("DRAFT", getRevision(sale, listingId, "1").body().path("status").asString());
	}

	/** RD-5: a number grouped with slashes is a stated limit: the reviewer catches it, the machine does not. */
	@Test
	void submit_statedLimitSlashGrouped_submits() throws Exception {
		Drafting drafting = listingInEffect();
		String listingId = drafting.listingId();
		completeDraft(drafting);
		assertEquals(200, saveContent(sale, listingId, "1", contentBody(dataVersion(listingId), TITLE,
				DESCRIPTION + " Mã 0905/123/456.", everyFeature())).status());

		Result submitted = submit(sale, listingId, "1", dataVersion(listingId));

		assertEquals(200, submitted.status(), submitted.rawBody());
		assertEquals("PENDING_REVIEW", submitted.body().path("status").asString());
	}

	/** RD-6: a complete Draft submits with the unit's values of this moment, then is locked; the listing stays as it is. */
	@Test
	void submit_complete_pendingSnapshotLockedListingUnpublished() throws Exception {
		Drafting drafting = listingInEffect();
		String listingId = drafting.listingId();
		completeDraft(drafting);
		// distinct room counts, so a snapshot that mixes them up cannot pass
		jdbc.update("update units set bedrooms = 3, bathrooms = 2 where id = ?", drafting.chain().unitId());
		Map<String, Object> unit = jdbc.queryForMap("select p.asset_type, pr.code as province_code, pr.name as"
				+ " province_name, w.code as ward_code, w.name as ward_name, u.bedrooms, u.bathrooms, u.area_sqm from units u"
				+ " join properties p on p.id = u.property_id left join wards w on w.code = p.ward_code left join provinces pr"
				+ " on pr.code = w.province_code where u.id = ?", drafting.chain().unitId());

		Result submitted = submit(sale, listingId, "1", dataVersion(listingId));

		assertEquals(200, submitted.status(), submitted.rawBody());
		assertEquals("PENDING_REVIEW", submitted.body().path("status").asString());
		JsonNode revision = getRevision(sale, listingId, "1").body();
		assertEquals("PENDING_REVIEW", revision.path("status").asString());
		assertFalse(revision.path("submittedAt").isNull(), revision.toString());
		assertEquals(0, revision.path("problems").size());
		JsonNode snapshot = revision.path("unitSnapshot");
		assertEquals(unit.get("asset_type"), text(snapshot.path("assetType")));
		assertEquals(unit.get("province_code"), text(snapshot.path("provinceCode")));
		assertEquals(unit.get("province_name"), text(snapshot.path("provinceName")));
		assertEquals(unit.get("ward_code"), text(snapshot.path("wardCode")));
		assertEquals(unit.get("ward_name"), text(snapshot.path("wardName")));
		assertTrue(snapshot.path("wardCode").isString(), snapshot.toString());
		assertEquals(((Number) unit.get("bedrooms")).intValue(), snapshot.path("bedrooms").asInt());
		assertEquals(((Number) unit.get("bathrooms")).intValue(), snapshot.path("bathrooms").asInt());
		assertEquals(((Number) unit.get("area_sqm")).doubleValue(), snapshot.path("areaSqm").asDouble());

		assertCode(409, "REVISION_NOT_DRAFT", saveContent(sale, listingId, "1",
				completeContentBody(dataVersion(listingId))));
		assertCode(409, "REVISION_NOT_DRAFT", captureTerms(sale, listingId, "1", dataVersion(listingId), "IN_EFFECT"));
		assertCode(409, "REVISION_NOT_DRAFT", submit(sale, listingId, "1", dataVersion(listingId)));
		assertEquals("UNPUBLISHED", getListing(sale, listingId).body().path("state").asString());
	}

	/** RD-6: a property whose ward the catalog lacks gives a snapshot with no ward and no province. */
	@Test
	void submit_wardNotInCatalog_snapshotWithoutPlace() throws Exception {
		Drafting drafting = listingInEffect();
		String listingId = drafting.listingId();
		completeDraft(drafting);
		jdbc.update("update properties set ward_code = '99999' where id = ?", drafting.chain().propertyId());

		Result submitted = submit(sale, listingId, "1", dataVersion(listingId));

		assertEquals(200, submitted.status(), submitted.rawBody());
		JsonNode snapshot = getRevision(sale, listingId, "1").body().path("unitSnapshot");
		for (String place : List.of("provinceCode", "provinceName", "wardCode", "wardName")) {
			assertTrue(snapshot.path(place).isNull(), place + " " + snapshot);
		}
		assertTrue(snapshot.path("bedrooms").isNumber(), snapshot.toString());
	}

	/** RD-6: a later change of the unit leaves the snapshot as it was. */
	@Test
	void submit_unitChangedLater_snapshotUnchanged() throws Exception {
		Drafting drafting = listingInEffect();
		String listingId = drafting.listingId();
		completeDraft(drafting);
		assertEquals(200, submit(sale, listingId, "1", dataVersion(listingId)).status());
		JsonNode before = getRevision(sale, listingId, "1").body().path("unitSnapshot");

		jdbc.update("update units set bedrooms = bedrooms + 7, bathrooms = bathrooms + 5, area_sqm = area_sqm + 50"
				+ " where id = ?", drafting.chain().unitId());

		assertEquals(before, getRevision(sale, listingId, "1").body().path("unitSnapshot"));
	}

	/** RD-7: a Pending review revision is withdrawn as Cancelled, its content kept; a Draft is not withdrawn. */
	@Test
	void withdraw_pending_cancelledContentKeptDraftRefused() throws Exception {
		Drafting drafting = listingInEffect();
		String listingId = drafting.listingId();
		assertCode(409, "REVISION_NOT_PENDING", withdraw(sale, listingId, "1", dataVersion(listingId)));
		completeDraft(drafting);
		assertEquals(200, submit(sale, listingId, "1", dataVersion(listingId)).status());

		Result withdrawn = withdraw(sale, listingId, "1", dataVersion(listingId));

		assertEquals(200, withdrawn.status(), withdrawn.rawBody());
		assertEquals("CANCELLED", withdrawn.body().path("status").asString());
		JsonNode revision = getRevision(sale, listingId, "1").body();
		assertEquals("CANCELLED", revision.path("status").asString());
		assertEquals(TITLE, revision.path("title").asString());
		assertEquals(DESCRIPTION, revision.path("description").asString());
		assertFalse(revision.path("termsSnapshot").isNull(), revision.toString());
		assertCode(409, "REVISION_NOT_PENDING", withdraw(sale, listingId, "1", dataVersion(listingId)));
	}

	/** RD-8: no start while a revision is open; after the withdrawal the next Draft is revision 2, empty. */
	@Test
	void start_openThenAfterWithdrawal_nextDraftAndOpenRevisionNo() throws Exception {
		Drafting drafting = listingInEffect();
		String listingId = drafting.listingId();
		assertCode(409, "OPEN_REVISION_EXISTS", startRevision(sale, listingId, dataVersion(listingId)));
		completeDraft(drafting);
		assertEquals(200, submit(sale, listingId, "1", dataVersion(listingId)).status());
		assertCode(409, "OPEN_REVISION_EXISTS", startRevision(sale, listingId, dataVersion(listingId)));
		assertEquals(1, getListing(sale, listingId).body().path("openRevisionNo").asInt());
		assertEquals(200, withdraw(sale, listingId, "1", dataVersion(listingId)).status());
		assertTrue(getListing(sale, listingId).body().path("openRevisionNo").isNull());

		Result started = startRevision(sale, listingId, dataVersion(listingId));

		assertEquals(201, started.status(), started.rawBody());
		assertEquals(2, started.body().path("revisionNo").asInt());
		assertEquals("DRAFT", started.body().path("status").asString());
		JsonNode revision = getRevision(sale, listingId, "2").body();
		assertTrue(revision.path("title").isNull(), revision.toString());
		assertTrue(revision.path("termsSnapshot").isNull(), revision.toString());
		assertEquals(List.of("title.REQUIRED", "description.REQUIRED", "terms.REQUIRED"), problems(revision));
		assertEquals(2, getListing(sale, listingId).body().path("openRevisionNo").asInt());
	}

	/** RD-10: a stale data version changes nothing; each success moves it and writes one history row of identifiers. */
	@Test
	void commands_staleVersion_conflictUnchanged_successMovesVersionOneHistoryRow() throws Exception {
		Drafting drafting = listingInEffect();
		String listingId = drafting.listingId();
		assertEquals(List.of("id", "listing_id", "action", "revision_no", "actor_user_id", "occurred_at"),
				jdbc.queryForList("select column_name from information_schema.columns where table_schema = 'public'"
						+ " and table_name = 'listing_history' order by ordinal_position", String.class));
		Map<String, String> actions = new LinkedHashMap<>();
		actions.put("save", "REVISION_SAVED");
		actions.put("terms", "TERMS_CAPTURED");
		actions.put("submit", "REVISION_SUBMITTED");
		actions.put("withdraw", "REVISION_WITHDRAWN");
		actions.put("start", "REVISION_STARTED");

		for (Map.Entry<String, String> action : actions.entrySet()) {
			RevisionRows before = revisionRows(listingId);
			assertCode(409, "CONCURRENT_MODIFICATION", run(action.getKey(), listingId, before.rowVersion() + 1));
			assertEquals(before, revisionRows(listingId), action.getKey());

			Result done = run(action.getKey(), listingId, before.rowVersion());

			assertTrue(done.status() == 200 || done.status() == 201, done.rawBody());
			assertEquals(before.rowVersion() + 1, done.body().path("dataVersion").asLong(), action.getKey());
			assertEquals(before.rowVersion() + 1, dataVersion(listingId));
			assertEquals(before.history() + 1, revisionRows(listingId).history(), action.getKey());
			Map<String, Object> row = jdbc.queryForMap("select action, revision_no, actor_user_id, occurred_at from"
					+ " listing_history where listing_id = ? order by occurred_at desc, action limit 1", listingId);
			assertEquals(action.getValue(), row.get("action"));
			assertEquals("start".equals(action.getKey()) ? 2 : 1, ((Number) row.get("revision_no")).intValue());
			assertEquals(sale.userId(), row.get("actor_user_id"));
			assertTrue(row.get("occurred_at") != null);
		}
	}

	/** RD-11: once the agreement ended, every command answers LISTING_ARCHIVED and the reads still answer. */
	@Test
	void commands_afterTermination_listingArchivedReadsAnswer() throws Exception {
		Drafting drafting = listingInEffect();
		String listingId = drafting.listingId();
		Result termination = terminate(sale, drafting.chain().agreementId(), unitLet());
		assertEquals(204, termination.status(), termination.rawBody());
		RevisionRows before = revisionRows(listingId);
		long version = dataVersion(listingId);

		for (Result answer : List.of(startRevision(sale, listingId, version),
				saveContent(sale, listingId, "1", completeContentBody(version)),
				captureTerms(sale, listingId, "1", version, "IN_EFFECT"),
				submit(sale, listingId, "1", version),
				withdraw(sale, listingId, "1", version))) {
			assertCode(409, "LISTING_ARCHIVED", answer);
		}
		assertEquals(before, revisionRows(listingId));
		Result revision = getRevision(sale, listingId, "1");
		assertEquals(200, revision.status(), revision.rawBody());
		Result listing = getListing(manager, listingId);
		assertEquals(200, listing.status(), listing.rawBody());
		assertEquals("ARCHIVED", listing.body().path("state").asString());
	}

	/** One command at the given data version, as the assigned Sale. */
	private Result run(String action, String listingId, long dataVersion) throws Exception {
		return switch (action) {
			case "save" -> saveContent(sale, listingId, "1", completeContentBody(dataVersion));
			case "terms" -> captureTerms(sale, listingId, "1", dataVersion, "IN_EFFECT");
			case "submit" -> submit(sale, listingId, "1", dataVersion);
			case "withdraw" -> withdraw(sale, listingId, "1", dataVersion);
			case "start" -> startRevision(sale, listingId, dataVersion);
			default -> throw new IllegalArgumentException(action);
		};
	}

	private static String text(JsonNode node) {
		return node.isNull() ? null : node.asString();
	}
}
