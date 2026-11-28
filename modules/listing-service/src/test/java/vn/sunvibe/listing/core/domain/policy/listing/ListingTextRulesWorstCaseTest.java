package vn.sunvibe.listing.core.domain.policy.listing;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import vn.sunvibe.listing.core.domain.model.listing.RevisionRule;

/**
 * listing-publish E06-S7 criterion PB-20, a pin of plan E06S7-P7: the machine check of {@link ListingTextRules} on the
 * longest texts a submission checks, built as worst cases for each pattern (long runs of digits and separators, of
 * {@code @} and letters, of dotted labels, near-misses of a forbidden phrase): a 90-character title, a 3,000-character
 * description and 20 fee names of 100 characters, each checked alone, as E06S7-D10 checks fee names. The 22 checks end
 * within 5 seconds, and each input built to break a rule answers that rule.
 */
class ListingTextRulesWorstCaseTest {

	/** Digits and separators that never read as a phone number: no 0 and no 84 in the joined run. */
	private static final String DIGIT_RUN = "9 . 9-";
	/** Letters and {@code @} with no dot: never an e-mail. */
	private static final String AT_RUN = "a@";
	/** Dotted labels ending in no listed top-level domain. */
	private static final String DOTTED_RUN = "a.";
	/** {@code @} followed by one letter only: never a handle. */
	private static final String HANDLE_RUN = "@a ";
	/** A near-miss of the phrase "cam kết". */
	private static final String PHRASE_RUN = "cam ke, ";
	private static final List<String> RUNS = List.of(DIGIT_RUN, AT_RUN, DOTTED_RUN, HANDLE_RUN, PHRASE_RUN);

	/** PB-20: the title, the description and 20 fee names, each checked alone, within 5 seconds. */
	@Test
	void broken_worstCaseTexts_withinFiveSeconds() {
		List<String> texts = new ArrayList<>();
		texts.add(filled(DIGIT_RUN, 90));
		StringBuilder description = new StringBuilder();
		for (String run : RUNS) {
			description.append(filled(run, 600));
		}
		texts.add(description.toString());
		texts.addAll(feeNames());
		assertEquals(22, texts.size());
		assertEquals(List.of(90, 3_000), List.of(texts.get(0).length(), texts.get(1).length()));

		assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
			for (String text : texts) {
				ListingTextRules.broken(text);
			}
		});
	}

	/** PB-20: each fee name built on a worst-case run to break one rule answers that rule. */
	@Test
	void broken_worstCaseBreakingInputs_answerTheirRule() {
		List<String> names = feeNames();

		assertAll(
				() -> assertTrue(ListingTextRules.broken(names.get(0)).contains(RevisionRule.PHONE_NUMBER)),
				() -> assertTrue(ListingTextRules.broken(names.get(1)).contains(RevisionRule.EMAIL)),
				() -> assertTrue(ListingTextRules.broken(names.get(2)).contains(RevisionRule.LINK)),
				() -> assertTrue(ListingTextRules.broken(names.get(3)).contains(RevisionRule.SOCIAL_HANDLE)),
				() -> assertTrue(ListingTextRules.broken(names.get(4)).contains(RevisionRule.FORBIDDEN_WORD)),
				() -> assertEquals(Set.of(), ListingTextRules.broken(names.get(5))));
	}

	/**
	 * 20 names of 100 characters: the first five each a worst-case run with a tail that breaks one rule, the others the
	 * runs alone, in turn.
	 */
	private static List<String> feeNames() {
		List<String> names = new ArrayList<>();
		names.add(withTail(DIGIT_RUN, " 0905 123 456"));
		names.add(withTail(AT_RUN, " b@c.de"));
		names.add(withTail(DOTTED_RUN, " x.vn"));
		names.add(withTail(HANDLE_RUN, " @ab"));
		names.add(withTail(PHRASE_RUN, " cam kết"));
		for (int index = 5; index < 20; index++) {
			names.add(filled(RUNS.get(index % RUNS.size()), 100));
		}
		return names;
	}

	private static String withTail(String run, String tail) {
		return filled(run, 100 - tail.length()) + tail;
	}

	private static String filled(String run, int length) {
		return run.repeat(length / run.length() + 1).substring(0, length);
	}
}
