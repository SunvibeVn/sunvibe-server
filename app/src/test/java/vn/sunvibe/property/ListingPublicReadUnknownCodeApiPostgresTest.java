package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
 * The public read fails closed (listing-publish E06-S7 spec E06S7-D5; criterion PB-26; ADR-012 "an error of a port
 * answers 500, never visible"): for a listing whose conditions all hold, each cause in turn answers 500 INTERNAL_ERROR
 * with {@code Cache-Control: no-store} and the error envelope only, naming no code or value of the cause, never 200 or
 * 404: (a) E05's review answers an unknown reason group; (b) property answers an unknown availability; (c) E05's terms in
 * effect carry an unknown deposit basis, fee basis or charge unit; (d) a port throws. With the class's switch back off
 * the same listing answers 200. E05's and property's answers are the real ones, replaced only while the switch says so,
 * through primary ports that delegate every other call ({@code ListingPutLiveUnknownCodeApiPostgresTest}); a context of
 * its own, the rate limiter off. Needs Docker.
 */
@Import(ListingPublicReadUnknownCodeApiPostgresTest.ScratchAnswers.class)
class ListingPublicReadUnknownCodeApiPostgresTest extends ListingPublicReadApiTestBase {

	/** Which answer the primary ports replace now. */
	enum Scratch {
		NONE, GROUP, AVAILABILITY, DEPOSIT_BASIS, FEE_BASIS, CHARGE_UNIT, THROW
	}

	static volatile Scratch scratch = Scratch.NONE;

	@Override
	String phonePrefix() {
		return "0987";
	}

	@AfterEach
	void switchOff() {
		scratch = Scratch.NONE;
	}

	/** PB-26 (a): E05's review answers a reason group listing does not know: 500, the envelope only. */
	@Test
	void publicRead_unknownReasonGroup_internalError() throws Exception {
		assertFailsClosed(List.of(Scratch.GROUP));
	}

	/** PB-26 (b): property answers an availability listing does not know: 500, the envelope only. */
	@Test
	void publicRead_unknownAvailability_internalError() throws Exception {
		assertFailsClosed(List.of(Scratch.AVAILABILITY));
	}

	/** PB-26 (c): E05's terms in effect carry an unknown deposit basis, fee basis, charge unit: 500 each. */
	@Test
	void publicRead_unknownTermsCodes_internalErrorEach() throws Exception {
		assertFailsClosed(List.of(Scratch.DEPOSIT_BASIS, Scratch.FEE_BASIS, Scratch.CHARGE_UNIT));
	}

	/** PB-26 (d): the unit inventory port throws: 500, the envelope only. */
	@Test
	void publicRead_portThrows_internalError() throws Exception {
		assertFailsClosed(List.of(Scratch.THROW));
	}

	/**
	 * The visible listing answers 200; with each cause on it answers 500 with no-store and exactly the envelope, its
	 * message naming neither the cause's code nor the listing's; with the switch back off, 200 again.
	 */
	private void assertFailsClosed(List<Scratch> causes) throws Exception {
		String code = publicCodeOf(liveListing().listingId());
		assertEquals(200, publicRead(code).status());

		List<String> answers = new ArrayList<>();
		for (Scratch cause : causes) {
			scratch = cause;
			Result answer = publicRead(code);
			scratch = Scratch.NONE;
			List<String> shown = new ArrayList<>();
			answer.body().properties().forEach(property -> shown.add(property.getKey()));
			String message = answer.body().path("message").asString();
			answers.add(cause + " " + answer.status() + " " + answer.body().path("code").asString() + " "
					+ answer.header("Cache-Control") + " " + shown.stream().sorted().toList() + " errors "
					+ answer.body().path("errors").size() + (message.contains("SCRATCH") || message.contains(code)
					|| answer.rawBody().contains("SCRATCH") ? " names the cause" : ""));
		}
		Result off = publicRead(code);

		List<String> expected = new ArrayList<>();
		for (Scratch cause : causes) {
			expected.add(cause + " 500 INTERNAL_ERROR no-store [code, correlationId, errors, message] errors 0");
		}
		assertEquals(expected, answers);
		assertEquals(200, off.status(), off.rawBody());
	}

	/** E05's review terms with one code replaced as the switch says; the other values as E05 sent them. */
	private static AgreementTermsFacts.Terms scratchTerms(AgreementTermsFacts.Terms terms) {
		if (terms == null || (scratch != Scratch.DEPOSIT_BASIS && scratch != Scratch.FEE_BASIS
				&& scratch != Scratch.CHARGE_UNIT)) {
			return terms;
		}
		List<AgreementTermsFacts.Fee> fees = new ArrayList<>(terms.fees());
		AgreementTermsFacts.Fee first = fees.getFirst();
		if (scratch == Scratch.FEE_BASIS) {
			fees.set(0, new AgreementTermsFacts.Fee(first.name(), "SCRATCH", first.amountOrNull(),
					first.chargeUnitOrNull()));
		} else if (scratch == Scratch.CHARGE_UNIT) {
			fees.set(0, new AgreementTermsFacts.Fee(first.name(), first.basis(), first.amountOrNull(), "SCRATCH"));
		}
		return new AgreementTermsFacts.Terms(terms.monthlyRentOrNull(),
				scratch == Scratch.DEPOSIT_BASIS ? "SCRATCH" : terms.depositBasisOrNull(), terms.depositMonthsOrNull(),
				terms.depositAmountOrNull(), terms.minimumTermMonthsOrNull(), terms.paymentPeriodMonthsOrNull(), fees);
	}

	/**
	 * E05's review answer with the group "SCRATCH_GROUP" or a terms code "SCRATCH", property's availability "SCRATCH",
	 * or a throwing availability read, each only while the switch says so; every other call the real adapter's.
	 */
	@TestConfiguration
	static class ScratchAnswers {

		@Bean
		@Primary
		AdvertisingRightPort scratchReviewRights(BrokerageAdvertisingRightAdapter real) {
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
					return real.answerUnderShareLock(unitId, agreementId);
				}

				@Override
				public Optional<AgreementTermsFacts> terms(String unitId, String agreementId) {
					return real.terms(unitId, agreementId);
				}

				@Override
				public Optional<AdvertisingReviewFacts> review(String unitId, String agreementId) {
					Optional<AdvertisingReviewFacts> review = real.review(unitId, agreementId);
					if (scratch == Scratch.GROUP) {
						return review.map(facts -> new AdvertisingReviewFacts(facts.unitId(), facts.agreementId(),
								facts.ended(), false, List.of("SCRATCH_GROUP"), facts.endDateOrNull(),
								facts.interiorPhotos(), facts.exteriorPhotos(), facts.inEffectOrNull(),
								facts.pendingStartDateOrNull(), facts.pendingOrNull()));
					}
					return review.map(facts -> new AdvertisingReviewFacts(facts.unitId(), facts.agreementId(),
							facts.ended(), facts.allowed(), facts.reasonGroups(), facts.endDateOrNull(),
							facts.interiorPhotos(), facts.exteriorPhotos(), scratchTerms(facts.inEffectOrNull()),
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
					if (scratch == Scratch.THROW) {
						throw new IllegalStateException("the unit inventory read failed");
					}
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
