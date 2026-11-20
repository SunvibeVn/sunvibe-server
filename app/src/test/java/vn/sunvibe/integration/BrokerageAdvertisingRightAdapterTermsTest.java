package vn.sunvibe.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementTermsFacts;

/**
 * The terms of E05's published answer as listing's port reads them (listing-publish E06-S2 spec E06S2-D13, plan
 * E06S2-P15): one call of {@code answers} for the one pair, the terms in effect, the pending start date and the pending
 * terms copied as they are, codes as strings. The new cases beside {@link BrokerageAdvertisingRightAdapterTest}, which
 * stays untouched (plan E06S2-P1); the fake of the use case is copied from it (:99-139).
 */
class BrokerageAdvertisingRightAdapterTermsTest {

	private final FakeAdvertisingRights rights = new FakeAdvertisingRights();
	private final BrokerageAdvertisingRightAdapter adapter = new BrokerageAdvertisingRightAdapter(rights);

	@Test
	void terms_inEffectAndPending_copiedAsTheyAre() {
		AdvertisingRightView.Terms inEffect = new AdvertisingRightView.Terms(8_000_000L,
				new AdvertisingRightView.Deposit("MONTHS", 2, null), 6, 1, List.of(
				new AdvertisingRightView.MandatoryFee("Điện", "AMOUNT", 3_500L, "PER_KWH"),
				new AdvertisingRightView.MandatoryFee("Internet", "INCLUDED", null, null),
				new AdvertisingRightView.MandatoryFee("Nước", "PROVIDER_TARIFF", null, null)));
		AdvertisingRightView.Terms pending = new AdvertisingRightView.Terms(9_500_000L,
				new AdvertisingRightView.Deposit("AMOUNT", null, 5_000_000L), 12, 3, List.of(
				new AdvertisingRightView.MandatoryFee("Rác", "AMOUNT", 20_000L, "PER_MONTH")));
		rights.answers = List.of(new AdvertisingRightView("01UNITA", "01AGRA", false, true, List.of(), null, null, false,
				false, inEffect, LocalDate.parse("2026-12-01"), null, pending));

		AgreementTermsFacts facts = adapter.terms("01UNITA", "01AGRA").orElseThrow();

		assertEquals(new AgreementTermsFacts(false,
				new AgreementTermsFacts.Terms(8_000_000L, "MONTHS", 2, null, 6, 1, List.of(
						new AgreementTermsFacts.Fee("Điện", "AMOUNT", 3_500L, "PER_KWH"),
						new AgreementTermsFacts.Fee("Internet", "INCLUDED", null, null),
						new AgreementTermsFacts.Fee("Nước", "PROVIDER_TARIFF", null, null))),
				LocalDate.parse("2026-12-01"),
				new AgreementTermsFacts.Terms(9_500_000L, "AMOUNT", null, 5_000_000L, 12, 3, List.of(
						new AgreementTermsFacts.Fee("Rác", "AMOUNT", 20_000L, "PER_MONTH")))), facts);
		assertEquals(List.of("answers [AdvertisingPair[unitId=01UNITA, agreementId=01AGRA]]"), rights.calls);
	}

	@Test
	void terms_noAnswer_empty() {
		rights.answers = List.of();

		assertEquals(Optional.empty(), adapter.terms("01UNITA", "01AGRA"));
	}

	@Test
	void terms_endedAnswer_endedCopied() {
		rights.answers = List.of(new AdvertisingRightView("01UNITA", "01AGRA", true, false, List.of("ENDED"), null, null,
				false, false, null, null, null, new AdvertisingRightView.Terms(null, null, null, null, List.of())));

		AgreementTermsFacts facts = adapter.terms("01UNITA", "01AGRA").orElseThrow();

		assertEquals(true, facts.ended());
		assertNull(facts.inEffectOrNull());
		assertNull(facts.pendingStartDateOrNull());
		assertEquals(new AgreementTermsFacts.Terms(null, null, null, null, null, null, List.of()), facts.pendingOrNull());
	}

	/** Records each call and answers what the test set; the unit read is not listing's and fails. */
	private static final class FakeAdvertisingRights implements AdvertisingRightUseCase {

		private final List<String> calls = new ArrayList<>();
		private List<AgreementStandingView> standings = List.of();
		private List<AdvertisingRightView> answers = List.of();
		private Optional<AdvertisingRightView> locked = Optional.empty();
		private RuntimeException failure;

		@Override
		public List<AdvertisingRightView> answers(Collection<AdvertisingPair> pairs) {
			calls.add("answers " + pairs);
			failIfAsked();
			return answers;
		}

		@Override
		public List<UnfinishedAgreementView> unfinishedOf(Collection<String> unitIds, String transactionType) {
			throw new UnsupportedOperationException("listing reads by agreement, never by unit");
		}

		@Override
		public List<AgreementStandingView> standings(Collection<String> agreementIds) {
			calls.add("standings " + agreementIds);
			failIfAsked();
			return standings;
		}

		@Override
		public Optional<AdvertisingRightView> answerUnderShareLock(AdvertisingPair pair) {
			calls.add("answerUnderShareLock " + pair);
			failIfAsked();
			return locked;
		}

		private void failIfAsked() {
			if (failure != null) {
				throw failure;
			}
		}
	}
}
