package vn.sunvibe.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import vn.sunvibe.brokerage.core.application.usecase.AdvertisingPair;
import vn.sunvibe.brokerage.core.application.usecase.AdvertisingRightUseCase;
import vn.sunvibe.brokerage.core.application.usecase.AdvertisingRightView;
import vn.sunvibe.brokerage.core.application.usecase.AgreementStandingView;
import vn.sunvibe.brokerage.core.application.usecase.UnfinishedAgreementView;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingReviewFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementTermsFacts;

/**
 * E05's published answer as the review reads it (listing-publish E06-S4 spec E06S4-D13; RV-15): one call of
 * {@code answers} for the one pair, every field copied as it is (codes as strings, terms translated as {@code terms}
 * translates them), nothing for a pair E05 does not answer. The fake of the use case is copied from
 * {@link BrokerageAdvertisingRightAdapterTermsTest}, which stays untouched.
 */
class BrokerageAdvertisingRightAdapterReviewTest {

	private final FakeAdvertisingRights rights = new FakeAdvertisingRights();
	private final BrokerageAdvertisingRightAdapter adapter = new BrokerageAdvertisingRightAdapter(rights);

	@Test
	void review_viewFieldsCopied() {
		AdvertisingRightView.Terms inEffect = new AdvertisingRightView.Terms(8_000_000L,
				new AdvertisingRightView.Deposit("MONTHS", 2, null), 6, 1, List.of(
				new AdvertisingRightView.MandatoryFee("Điện", "AMOUNT", 3_500L, "PER_KWH"),
				new AdvertisingRightView.MandatoryFee("Internet", "INCLUDED", null, null)));
		AdvertisingRightView.Terms pending = new AdvertisingRightView.Terms(9_500_000L,
				new AdvertisingRightView.Deposit("AMOUNT", null, 5_000_000L), 12, 3, List.of());
		rights.answers = List.of(new AdvertisingRightView("01UNITA", "01AGRA", false, false, List.of("OWNER", "SUSPENDED"),
				LocalDate.parse("2027-06-30"), null, true, false, inEffect, LocalDate.parse("2026-12-01"), null, pending));

		AdvertisingReviewFacts facts = adapter.review("01UNITA", "01AGRA").orElseThrow();

		assertEquals(new AdvertisingReviewFacts("01UNITA", "01AGRA", false, false, List.of("OWNER", "SUSPENDED"),
				LocalDate.parse("2027-06-30"), true, false,
				new AgreementTermsFacts.Terms(8_000_000L, "MONTHS", 2, null, 6, 1, List.of(
						new AgreementTermsFacts.Fee("Điện", "AMOUNT", 3_500L, "PER_KWH"),
						new AgreementTermsFacts.Fee("Internet", "INCLUDED", null, null))),
				LocalDate.parse("2026-12-01"),
				new AgreementTermsFacts.Terms(9_500_000L, "AMOUNT", null, 5_000_000L, 12, 3, List.of())), facts);
		assertEquals(List.of("answers [AdvertisingPair[unitId=01UNITA, agreementId=01AGRA]]"), rights.calls);
		assertEquals("AdvertisingReviewFacts[01UNITA, 01AGRA]", facts.toString());
		assertFalse(facts.toString().contains("OWNER"));
	}

	@Test
	void review_noView_empty() {
		rights.answers = List.of();

		assertEquals(Optional.empty(), adapter.review("01UNITA", "01AGRA"));
		assertEquals(List.of("answers [AdvertisingPair[unitId=01UNITA, agreementId=01AGRA]]"), rights.calls);
	}

	/** Records each call and answers what the test set; the unit read is not listing's and fails. */
	private static final class FakeAdvertisingRights implements AdvertisingRightUseCase {

		private final List<String> calls = new ArrayList<>();
		private List<AgreementStandingView> standings = List.of();
		private List<AdvertisingRightView> answers = List.of();
		private Optional<AdvertisingRightView> locked = Optional.empty();

		@Override
		public List<AdvertisingRightView> answers(Collection<AdvertisingPair> pairs) {
			calls.add("answers " + pairs);
			return answers;
		}

		@Override
		public List<UnfinishedAgreementView> unfinishedOf(Collection<String> unitIds, String transactionType) {
			throw new UnsupportedOperationException("listing reads by agreement, never by unit");
		}

		@Override
		public List<AgreementStandingView> standings(Collection<String> agreementIds) {
			calls.add("standings " + agreementIds);
			return standings;
		}

		@Override
		public Optional<AdvertisingRightView> answerUnderShareLock(AdvertisingPair pair) {
			calls.add("answerUnderShareLock " + pair);
			return locked;
		}
	}
}
