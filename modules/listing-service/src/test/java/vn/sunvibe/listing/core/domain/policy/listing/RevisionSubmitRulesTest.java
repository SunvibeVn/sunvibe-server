package vn.sunvibe.listing.core.domain.policy.listing;

import org.junit.jupiter.api.Test;
import vn.sunvibe.listing.core.domain.model.listing.DepositBasis;
import vn.sunvibe.listing.core.domain.model.listing.FeeBasis;
import vn.sunvibe.listing.core.domain.model.listing.RevisionContent;
import vn.sunvibe.listing.core.domain.model.listing.RevisionFeatures;
import vn.sunvibe.listing.core.domain.model.listing.RevisionPart;
import vn.sunvibe.listing.core.domain.model.listing.RevisionRule;
import vn.sunvibe.listing.core.domain.model.listing.RevisionTerms;
import vn.sunvibe.listing.core.domain.model.listing.RevisionViolation;
import vn.sunvibe.listing.core.domain.model.listing.TermsSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.listing.core.domain.model.listing.RevisionPart.DESCRIPTION;
import static vn.sunvibe.listing.core.domain.model.listing.RevisionPart.TERMS;
import static vn.sunvibe.listing.core.domain.model.listing.RevisionPart.TITLE;
import static vn.sunvibe.listing.core.domain.model.listing.RevisionRule.EMAIL;
import static vn.sunvibe.listing.core.domain.model.listing.RevisionRule.FORBIDDEN_WORD;
import static vn.sunvibe.listing.core.domain.model.listing.RevisionRule.LINK;
import static vn.sunvibe.listing.core.domain.model.listing.RevisionRule.PHONE_NUMBER;
import static vn.sunvibe.listing.core.domain.model.listing.RevisionRule.REQUIRED;
import static vn.sunvibe.listing.core.domain.model.listing.RevisionRule.SOCIAL_HANDLE;
import static vn.sunvibe.listing.core.domain.model.listing.RevisionRule.TOO_SHORT;

/**
 * What a submission requires (listing-publish US-2 AC-4, BR-2, BR-4; E06-S2 spec E06S2-D7): every violation once, in
 * the order of E06S2-D7, lengths in code points.
 */
class RevisionSubmitRulesTest {

    private static final RevisionTerms TERMS_IN_EFFECT = new RevisionTerms(TermsSource.IN_EFFECT, null,
            Instant.parse("2026-11-20T03:15:00.123456Z"), 8_000_000L,
            new RevisionTerms.Deposit(DepositBasis.MONTHS, 2, null), 6, 1, List.of());
    /** 30 code points of clean Vietnamese text. */
    private static final String TITLE_OK = "Phòng trọ mới gần chợ Hải Châu";
    private static final String DESCRIPTION_OK = ("Phòng sạch sẽ, có cửa sổ lớn"
            + " và ban công rộng. ").repeat(3);

    @Test
    void violations_emptyDraft_threeRequired() {
        assertEquals(List.of(v(TITLE, REQUIRED), v(DESCRIPTION, REQUIRED), v(TERMS, REQUIRED)),
                RevisionSubmitRules.violations(RevisionContent.EMPTY, null));
    }

    @Test
    void violations_shortTitleOnly_rd1Order() {
        assertEquals(List.of(v(TITLE, TOO_SHORT), v(DESCRIPTION, REQUIRED), v(TERMS, REQUIRED)),
                RevisionSubmitRules.violations(content("a".repeat(14), null), null));
        assertEquals(List.of(v(DESCRIPTION, REQUIRED), v(TERMS, REQUIRED)),
                RevisionSubmitRules.violations(content("a".repeat(15), null), null));
    }

    @Test
    void violations_lengthBounds_atFourteenAndNinetyNine() {
        assertEquals(List.of(v(TITLE, TOO_SHORT), v(DESCRIPTION, TOO_SHORT)),
                RevisionSubmitRules.violations(content("ộ".repeat(14), "ộ".repeat(99)), TERMS_IN_EFFECT));
        assertEquals(List.of(),
                RevisionSubmitRules.violations(content("ộ".repeat(15), "ộ".repeat(100)), TERMS_IN_EFFECT));
        assertEquals(List.of(), RevisionSubmitRules.violations(
                content("a".repeat(89) + "😀", "a".repeat(2999) + "😀"), TERMS_IN_EFFECT));
        assertEquals(15, RevisionSubmitRules.TITLE_MIN);
        assertEquals(100, RevisionSubmitRules.DESCRIPTION_MIN);
    }

    @Test
    void violations_everyRuleBroken_e06s2D7Order() {
        String everyRule = "0905123456 nhatro@gmail.com www.abc.vn zalo uy tín";
        RevisionContent content = content(everyRule, "Mô tả: " + everyRule);

        assertEquals(List.of(v(DESCRIPTION, TOO_SHORT), v(TERMS, REQUIRED),
                        v(TITLE, PHONE_NUMBER), v(TITLE, EMAIL), v(TITLE, LINK), v(TITLE, SOCIAL_HANDLE),
                        v(TITLE, FORBIDDEN_WORD),
                        v(DESCRIPTION, PHONE_NUMBER), v(DESCRIPTION, EMAIL), v(DESCRIPTION, LINK),
                        v(DESCRIPTION, SOCIAL_HANDLE), v(DESCRIPTION, FORBIDDEN_WORD)),
                RevisionSubmitRules.violations(content, null));
        // a title too short to hold all five machine rules still answers its length first
        assertEquals(List.of(v(TITLE, TOO_SHORT), v(DESCRIPTION, REQUIRED), v(TERMS, REQUIRED),
                        v(TITLE, PHONE_NUMBER), v(TITLE, SOCIAL_HANDLE)),
                RevisionSubmitRules.violations(content("0905123456 fb", null), null));
    }

    @Test
    void violations_complete_none() {
        assertEquals(List.of(), RevisionSubmitRules.violations(content(TITLE_OK, DESCRIPTION_OK), TERMS_IN_EFFECT));
    }

    @Test
    void violations_refusedPhoneVectorInEachPart_bothParts() {
        List<String> storable = new ArrayList<>();
        ListingTextRulesTest.refusedPhoneVectors()
                .filter(vector -> !vector.contains("\n") && !vector.contains("​"))
                .forEach(storable::add);
        assertEquals(36, storable.size());
        for (String vector : storable) {
            RevisionContent content = content(padded(vector, 15), padded(vector, 100));
            assertEquals(List.of(v(TITLE, PHONE_NUMBER), v(DESCRIPTION, PHONE_NUMBER)),
                    RevisionSubmitRules.violations(content, TERMS_IN_EFFECT), vector);
        }
        String lineFeeds = "0905\n123\n456";
        assertTrue(ListingTextRulesTest.refusedPhoneVectors().anyMatch(lineFeeds::equals));
        assertEquals(List.of(v(DESCRIPTION, PHONE_NUMBER)),
                RevisionSubmitRules.violations(content(TITLE_OK, padded(lineFeeds, 100)), TERMS_IN_EFFECT));
    }

    /**
     * listing-publish E06-S7 spec E06S7-D10 (LP-15, E-20): after the description's machine rules, each fee name of the
     * snapshot in E05's order, alone, each broken rule once in rule order, with the fee's 1-based position.
     */
    @Test
    void violations_feeNames_afterDescriptionInFeeOrderWithPosition() {
        String everyRule = "0905123456 nhatro@gmail.com www.abc.vn zalo uy tín";
        RevisionTerms terms = withFees("Điện", "Gửi xe, gọi 0905 123 456", "Phí dịch vụ đảm bảo");

        assertEquals(List.of(v(DESCRIPTION, TOO_SHORT), v(TITLE, PHONE_NUMBER), v(TITLE, EMAIL), v(TITLE, LINK),
                        v(TITLE, SOCIAL_HANDLE), v(TITLE, FORBIDDEN_WORD), v(DESCRIPTION, PHONE_NUMBER),
                        v(DESCRIPTION, EMAIL), v(DESCRIPTION, LINK), v(DESCRIPTION, SOCIAL_HANDLE),
                        v(DESCRIPTION, FORBIDDEN_WORD), fee(PHONE_NUMBER, 2), fee(FORBIDDEN_WORD, 3)),
                RevisionSubmitRules.violations(content(everyRule, "Mô tả: " + everyRule), terms));
        assertEquals(List.of(fee(PHONE_NUMBER, 2)), RevisionSubmitRules.violations(content(TITLE_OK, DESCRIPTION_OK),
                withFees("Điện", "Gửi xe, gọi 0905 123 456")));
    }

    /** E06S7-D10: each machine rule of a fee name once, in rule order, the same patterns as title and description. */
    @Test
    void violations_feeNameBreakingEveryMachineRule_eachOnce() {
        assertEquals(List.of(fee(PHONE_NUMBER, 1), fee(EMAIL, 1), fee(LINK, 1), fee(SOCIAL_HANDLE, 1),
                        fee(FORBIDDEN_WORD, 1)),
                RevisionSubmitRules.violations(content(TITLE_OK, DESCRIPTION_OK),
                        withFees("0905123456 nhatro@gmail.com www.abc.vn zalo uy tín")));
    }

    /** E06S7-D10: a clean name of 100 characters passes; no length rule applies to a fee name (S3-D8, E05's own). */
    @Test
    void violations_cleanFeeNames_none() {
        String clean100 = "Phí gửi xe máy hằng tháng ".repeat(4).substring(0, 100);
        assertEquals(100, clean100.codePointCount(0, clean100.length()));

        assertEquals(List.of(), RevisionSubmitRules.violations(content(TITLE_OK, DESCRIPTION_OK),
                withFees(clean100, "Đ", "Internet")));
    }

    private static RevisionTerms withFees(String... names) {
        List<RevisionTerms.Fee> fees = new ArrayList<>();
        for (String name : names) {
            fees.add(new RevisionTerms.Fee(name, FeeBasis.INCLUDED, null, null));
        }
        return new RevisionTerms(TermsSource.IN_EFFECT, null, Instant.parse("2026-11-20T03:15:00.123456Z"), 8_000_000L,
                new RevisionTerms.Deposit(DepositBasis.MONTHS, 2, null), 6, 1, fees);
    }

    private static RevisionViolation fee(RevisionRule rule, int position) {
        return new RevisionViolation(RevisionPart.FEE_NAME, rule, position);
    }

    /** The vector, then a space and letters up to the minimum length. */
    private static String padded(String vector, int minimum) {
        int missing = minimum - vector.codePointCount(0, vector.length()) - 1;
        return vector + " " + "x".repeat(Math.max(1, missing));
    }

    private static RevisionContent content(String title, String description) {
        return new RevisionContent(title, description, RevisionFeatures.NONE_CHOSEN);
    }

    private static RevisionViolation v(RevisionPart part, RevisionRule rule) {
        return new RevisionViolation(part, rule);
    }
}
