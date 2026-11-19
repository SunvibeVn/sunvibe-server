package vn.sunvibe.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingAnswerFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementStandingFacts;

/**
 * The adapter answers listing's port with E05's published, viewer-less advertising read (listing-publish E06-S1 spec
 * E06S1-D9, LC-16; ADR-009): each call forwarded with its arguments, every field copied, the codes passed unchanged in
 * their order, absence kept as absence, an exception of the use case reaching the caller.
 */
class BrokerageAdvertisingRightAdapterTest {

	private final FakeAdvertisingRights rights = new FakeAdvertisingRights();
	private final BrokerageAdvertisingRightAdapter adapter = new BrokerageAdvertisingRightAdapter(rights);

	@Test
	void standings_forwarded_everyFieldCopied() {
		rights.standings = List.of(new AgreementStandingView("01AGRA", "01UNITA", "LONG_TERM_RENT", false, "01SALE"),
				new AgreementStandingView("01AGRB", "01UNITB", "OTHER_TYPE", true, null));

		List<AgreementStandingFacts> facts = adapter.standings(List.of("01AGRA", "01AGRB", "01UNKNOWN"));

		assertEquals(List.of(new AgreementStandingFacts("01AGRA", "01UNITA", "LONG_TERM_RENT", false, "01SALE"),
				new AgreementStandingFacts("01AGRB", "01UNITB", "OTHER_TYPE", true, null)), facts);
		assertEquals(List.of("standings [01AGRA, 01AGRB, 01UNKNOWN]"), rights.calls);
	}

	@Test
	void answer_onePair_codesUnchangedInOrder() {
		rights.answers = List.of(view("01UNITA", "01AGRA", false, false, List.of("NOT_STARTED", "OWNER", "FUTURE_GROUP")));

		Optional<AdvertisingAnswerFacts> facts = adapter.answer("01UNITA", "01AGRA");

		assertEquals(Optional.of(new AdvertisingAnswerFacts("01UNITA", "01AGRA", false, false,
				List.of("NOT_STARTED", "OWNER", "FUTURE_GROUP"))), facts);
		assertEquals(List.of("answers [AdvertisingPair[unitId=01UNITA, agreementId=01AGRA]]"), rights.calls);
	}

	@Test
	void answer_absent_staysAbsent() {
		rights.answers = List.of();

		assertEquals(Optional.empty(), adapter.answer("01UNITA", "01AGRA"));
	}

	@Test
	void answerUnderShareLock_absent_staysAbsent() {
		rights.locked = Optional.empty();

		assertEquals(Optional.empty(), adapter.answerUnderShareLock("01UNITA", "01AGRA"));
		assertEquals(List.of("answerUnderShareLock AdvertisingPair[unitId=01UNITA, agreementId=01AGRA]"), rights.calls);

		rights.locked = Optional.of(view("01UNITA", "01AGRA", true, false, List.of("ENDED")));

		assertEquals(Optional.of(new AdvertisingAnswerFacts("01UNITA", "01AGRA", true, false, List.of("ENDED"))),
				adapter.answerUnderShareLock("01UNITA", "01AGRA"));
		assertEquals(Optional.of(new AdvertisingAnswerFacts("01UNITA", "01AGRA", false, true, List.of())),
				answerUnderShareLockOf(view("01UNITA", "01AGRA", false, true, List.of())));
	}

	@Test
	void answer_useCaseThrows_reachesTheCaller() {
		IllegalStateException failure = new IllegalStateException("the read failed");
		rights.failure = failure;

		assertSame(failure, assertThrows(IllegalStateException.class, () -> adapter.answer("01UNITA", "01AGRA")));
		assertSame(failure, assertThrows(IllegalStateException.class,
				() -> adapter.answerUnderShareLock("01UNITA", "01AGRA")));
		assertSame(failure, assertThrows(IllegalStateException.class, () -> adapter.standings(List.of("01AGRA"))));
	}

	private Optional<AdvertisingAnswerFacts> answerUnderShareLockOf(AdvertisingRightView view) {
		rights.locked = Optional.of(view);
		return adapter.answerUnderShareLock(view.unitId(), view.agreementId());
	}

	private static AdvertisingRightView view(String unitId, String agreementId, boolean ended, boolean allowed,
			List<String> groups) {
		return new AdvertisingRightView(unitId, agreementId, ended, allowed, groups, null, null, false, false, null, null,
				null, null);
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
