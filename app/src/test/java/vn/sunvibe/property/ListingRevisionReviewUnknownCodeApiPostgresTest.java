package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.integration.BrokerageAdvertisingRightAdapter;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingAnswerFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingReviewFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingRightPort;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementStandingFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementTermsFacts;

/**
 * An E05 reason group listing does not know fails the review read (listing-publish E06-S4 spec E06S4-D11, plan
 * E06S4-P7; listing-publish E06-S1 spec E06S1-D9): 500 INTERNAL_ERROR and nothing of the review shown, never a group read
 * as a known one. E05's answer is the real one with the groups replaced, through a primary port that delegates every
 * other call to the real adapter; a context of its own. Needs Docker.
 */
@Import(ListingRevisionReviewUnknownCodeApiPostgresTest.ScratchGroupRights.class)
class ListingRevisionReviewUnknownCodeApiPostgresTest extends ListingReviewApiTestBase {

	@Override
	String phonePrefix() {
		return "0955";
	}

	@Test
	void review_unknownE05ReasonGroup_internalErrorNothingShown() throws Exception {
		String listingId = pending();

		Result answer = review(reviewer, listingId, "1");

		assertCode(500, "INTERNAL_ERROR", answer);
		List<String> shown = new ArrayList<>();
		answer.body().properties().forEach(property -> shown.add(property.getKey()));
		assertEquals(List.of("code", "correlationId", "errors", "message"), shown.stream().sorted().toList(),
				answer.rawBody());
		assertEquals(0, answer.body().path("errors").size(), answer.rawBody());
	}

	/** E05's answer with the reason group "SCRATCH_GROUP" in place of its own groups; everything else the real one. */
	@TestConfiguration
	static class ScratchGroupRights {

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
					return real.answerUnderShareLock(unitId, agreementId);
				}

				@Override
				public Optional<AgreementTermsFacts> terms(String unitId, String agreementId) {
					return real.terms(unitId, agreementId);
				}

				@Override
				public Optional<AdvertisingReviewFacts> review(String unitId, String agreementId) {
					return real.review(unitId, agreementId).map(facts -> new AdvertisingReviewFacts(facts.unitId(),
							facts.agreementId(), facts.ended(), false, List.of("SCRATCH_GROUP"), facts.endDateOrNull(),
							facts.interiorPhotos(), facts.exteriorPhotos(), facts.inEffectOrNull(),
							facts.pendingStartDateOrNull(), facts.pendingOrNull()));
				}
			};
		}
	}
}
