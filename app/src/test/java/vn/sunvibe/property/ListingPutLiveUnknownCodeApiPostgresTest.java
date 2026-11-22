package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.integration.BrokerageAdvertisingRightAdapter;
import vn.sunvibe.integration.PropertyUnitInventoryAdapter;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingAnswerFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingReviewFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingRightPort;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementStandingFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementTermsFacts;
import vn.sunvibe.listing.core.application.shared.external.property.UnitAddressFacts;
import vn.sunvibe.listing.core.application.shared.external.property.UnitAvailabilityFacts;
import vn.sunvibe.listing.core.application.shared.external.property.UnitInventoryPort;

/**
 * An E05 reason group or an availability code listing does not know fails the Sale's put live, the two tries and the
 * publication read (listing-publish E06-S6 spec E06S6-D3, D7, D11, section 4.2; PL-3, PL-4, PL-11; plan E06S6-P4): 500
 * INTERNAL_ERROR, nothing written and nothing shown, never a code read as a known one. E05's and property's answers are
 * the real ones, with the group {@code SCRATCH_GROUP} or the availability {@code SCRATCH} in their place while the
 * class's switch says so, through primary ports that delegate every other call; a context of its own. The switch is
 * turned on after the fixture, so the fixture reads known codes. Needs Docker.
 */
@Import(ListingPutLiveUnknownCodeApiPostgresTest.ScratchCodes.class)
class ListingPutLiveUnknownCodeApiPostgresTest extends ListingPublicationApiTestBase {

	/** Which answer the primary ports replace now. */
	enum Scratch {
		NONE, GROUP, AVAILABILITY
	}

	static volatile Scratch scratch = Scratch.NONE;

	@Override
	String phonePrefix() {
		return "0981";
	}

	@AfterEach
	void switchOff() {
		scratch = Scratch.NONE;
	}

	/** PL-3: the group E05 answers under the share lock is unknown: 500, no decision, the revision still pending. */
	@Test
	void approve_unknownGroupUnderShareLock_internalErrorNoDecision() throws Exception {
		assertApprovalFails(Scratch.GROUP);
	}

	/** PL-3: the unit's availability is unknown: 500, no decision, the revision still pending. */
	@Test
	void approve_unknownAvailability_internalErrorNoDecision() throws Exception {
		assertApprovalFails(Scratch.AVAILABILITY);
	}

	/** PL-4: the group E05 answers under the share lock is unknown: 500, no record, the revision still Approved. */
	@Test
	void record_unknownGroupUnderShareLock_internalErrorNoRecord() throws Exception {
		assertRecordFails(Scratch.GROUP);
	}

	/** PL-4: the unit's availability is unknown: 500, no record, the revision still Approved. */
	@Test
	void record_unknownAvailability_internalErrorNoRecord() throws Exception {
		assertRecordFails(Scratch.AVAILABILITY);
	}

	/**
	 * E06S6-D3, D5 step (9): the group E05 answers under the share lock to the Sale's put live is unknown: 500, no code in
	 * the body, nothing written, the revision still Approved.
	 */
	@Test
	void putLive_unknownGroupUnderShareLock_internalErrorNothingWritten() throws Exception {
		assertPutLiveFails(Scratch.GROUP);
	}

	/** E06S6-D3, D5 step (9): the unit's availability is unknown: 500, nothing written, the revision still Approved. */
	@Test
	void putLive_unknownAvailability_internalErrorNothingWritten() throws Exception {
		assertPutLiveFails(Scratch.AVAILABILITY);
	}

	/** PL-11, E06S6-D11: E05's review answers an unknown group: 500 and the error envelope only. */
	@Test
	void publication_unknownGroup_internalErrorNothingShown() throws Exception {
		assertPublicationFails(Scratch.GROUP);
	}

	/** PL-11, E06S6-D11: property answers an unknown availability: 500 and the error envelope only. */
	@Test
	void publication_unknownAvailability_internalErrorNothingShown() throws Exception {
		assertPublicationFails(Scratch.AVAILABILITY);
	}

	private void assertApprovalFails(Scratch code) throws Exception {
		Drafting drafting = listingInEffect();
		String listingId = drafting.listingId();
		Result recorded = recordFreshness(sale, listingId, validRecord(dataVersion(listingId)));
		assertEquals(201, recorded.status(), recorded.rawBody());
		submitted(drafting);
		Written before = written();
		scratch = code;

		Result answer = decide(reviewer, listingId, "1", approval(dataVersion(listingId)));

		assertAll(
				() -> assertEquals("500 INTERNAL_ERROR", codeOf(answer)),
				() -> assertEquals(0, decisionRows(listingId)),
				() -> assertEquals("PENDING_REVIEW", revisionStatus(listingId, 1)),
				() -> assertEquals(before, written()));
	}

	private void assertRecordFails(Scratch code) throws Exception {
		String listingId = waitingApproval().listingId();
		List<String> records = freshnessRecords(listingId);
		int history = count("select count(*) from listing_history where listing_id = ?", listingId);
		Written before = written();
		scratch = code;

		Result answer = recordFreshness(sale, listingId, validRecord(dataVersion(listingId)));

		assertAll(
				() -> assertEquals("500 INTERNAL_ERROR", codeOf(answer)),
				() -> assertEquals(records, freshnessRecords(listingId)),
				() -> assertEquals(history, count("select count(*) from listing_history where listing_id = ?",
						listingId)),
				() -> assertEquals("APPROVED", revisionStatus(listingId, 1)),
				() -> assertEquals(before, written()));
	}

	private void assertPutLiveFails(Scratch code) throws Exception {
		String listingId = waitingApproval().listingId();
		long version = dataVersion(listingId);
		Written before = written();
		scratch = code;

		Result answer = putLive(sale, listingId, "1", version);

		assertAll(
				() -> assertEquals("500 INTERNAL_ERROR", codeOf(answer)),
				() -> assertFalse(answer.rawBody().contains("SCRATCH"), answer.rawBody()),
				() -> assertEquals("APPROVED", revisionStatus(listingId, 1)),
				() -> assertEquals("UNPUBLISHED", state(listingId)),
				() -> assertEquals(version, dataVersion(listingId)),
				() -> assertEquals(List.of(), listingEventRows(listingId)),
				() -> assertEquals(before, written()));
	}

	private void assertPublicationFails(Scratch code) throws Exception {
		String listingId = waitingApproval().listingId();
		Written before = written();
		scratch = code;

		Result answer = publication(sale, listingId);

		List<String> shown = new ArrayList<>();
		answer.body().properties().forEach(property -> shown.add(property.getKey()));
		assertAll(
				() -> assertEquals("500 INTERNAL_ERROR", codeOf(answer)),
				() -> assertEquals(List.of("code", "correlationId", "errors", "message"), shown.stream().sorted()
						.toList(), answer.rawBody()),
				() -> assertEquals(0, answer.body().path("errors").size(), answer.rawBody()),
				() -> assertFalse(answer.rawBody().contains("SCRATCH"), answer.rawBody()),
				() -> assertEquals(before, written()));
	}

	/**
	 * E05's answers with the group "SCRATCH_GROUP" (under the share lock and in the review answer) and property's
	 * availability "SCRATCH", each only while the switch says so; every other call the real adapter's.
	 */
	@TestConfiguration
	static class ScratchCodes {

		@Bean
		@Primary
		AdvertisingRightPort scratchGroupRights(BrokerageAdvertisingRightAdapter real) {
			return new AdvertisingRightPort() {

				@Override
				public List<AgreementStandingFacts> standings(Collection<String> agreementIds) {
					return real.standings(agreementIds);
				}

				@Override
				public Optional<AdvertisingAnswerFacts> answer(String unitId, String agreementId) {
					return real.answer(unitId, agreementId);
				}

				@Override
				public Optional<AdvertisingAnswerFacts> answerUnderShareLock(String unitId, String agreementId) {
					Optional<AdvertisingAnswerFacts> answer = real.answerUnderShareLock(unitId, agreementId);
					return scratch != Scratch.GROUP ? answer : answer.map(facts -> new AdvertisingAnswerFacts(
							facts.unitId(), facts.agreementId(), facts.ended(), false, List.of("SCRATCH_GROUP")));
				}

				@Override
				public Optional<AgreementTermsFacts> terms(String unitId, String agreementId) {
					return real.terms(unitId, agreementId);
				}

				@Override
				public Optional<AdvertisingReviewFacts> review(String unitId, String agreementId) {
					Optional<AdvertisingReviewFacts> review = real.review(unitId, agreementId);
					return scratch != Scratch.GROUP ? review : review.map(facts -> new AdvertisingReviewFacts(
							facts.unitId(), facts.agreementId(), facts.ended(), false, List.of("SCRATCH_GROUP"),
							facts.endDateOrNull(), facts.interiorPhotos(), facts.exteriorPhotos(), facts.inEffectOrNull(),
							facts.pendingStartDateOrNull(), facts.pendingOrNull()));
				}
			};
		}

		@Bean
		@Primary
		UnitInventoryPort scratchAvailabilityUnits(PropertyUnitInventoryAdapter real) {
			return new UnitInventoryPort() {

				@Override
				public List<UnitAvailabilityFacts> availability(Collection<String> unitIds) {
					List<UnitAvailabilityFacts> facts = real.availability(unitIds);
					return scratch != Scratch.AVAILABILITY ? facts : facts.stream()
							.map(unit -> new UnitAvailabilityFacts(unit.unitId(), unit.unitCodeOrNull(), "SCRATCH",
									unit.availabilityDiffers()))
							.toList();
				}

				@Override
				public Optional<UnitAddressFacts> address(String unitId) {
					return real.address(unitId);
				}
			};
		}
	}
}
