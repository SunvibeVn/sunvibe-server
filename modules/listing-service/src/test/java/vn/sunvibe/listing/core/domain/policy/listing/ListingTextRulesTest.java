package vn.sunvibe.listing.core.domain.policy.listing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import vn.sunvibe.listing.core.domain.model.listing.RevisionRule;

import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The machine check of a listing's public text (listing-publish BR-2, E-4; listing BR-10, BR-11; E06-S2 spec E06S2-D8,
 * plan E06S2-P9): the test vectors of E06S2-D8, verbatim, special characters written as escapes.
 */
class ListingTextRulesTest {

    /** E06S2-D8, refused: the first list (23 vectors). */
    static Stream<String> refusedPhoneVectors() {
        return Stream.concat(Stream.of(
                "0905.123.456",
                "0905 123 456",
                "0905-123-456",
                "090 5123 456",
                "+84 905 123 456",
                "84.905.123.456",
                "(028) 3823 4567",
                "Tầng 3 0905 123 456",
                "0905.12.10.26",
                "0905-12-10-26",
                "0912.03.2025",
                "0905 - 123 - 456",
                "0905 . 123 . 456",
                "0905–123–456",
                "0905—123—456",
                "0905−123−456",
                "0905·123·456",
                "0905 - - - 123 - 456",
                "0905      123 456",
                "0905\n123\n456",
                "0905 123 456",
                "liên hệ ０９０５１２３４５６",
                "0905 123​456"), separatorVectors());
    }

    /** E06S2-D8, refused: one vector per other kind of separator (15 vectors). */
    static Stream<String> separatorVectors() {
        return Stream.of(
                "0905_123_456",
                "0905*123*456",
                "0905+123+456",
                "0905:123:456",
                "0905;123;456",
                "0905~123~456",
                "0905|123|456",
                "0905#123#456",
                "0905=123=456",
                "0905'123'456",
                "\"0905\"123\"456\"",
                "0905★123★456",
                "0905🙂123🙂456",
                "0905́123 456",
                "٠٩٠٥١٢٣٤٥٦");
    }

    /** E06S2-D8, not refused by the phone rule (12 vectors). */
    static Stream<String> notRefusedVectors() {
        return Stream.of(
                "8.500.000 đồng/tháng",
                "cọc 2 tháng",
                "30 m2",
                "30 m²",
                "01/12/2026",
                "01/12/2026 09:00",
                "từ 01/12/2026 đến 01/12/2027",
                "2020-2024",
                "tầng 3, phòng 302",
                "từ 8:00 đến 17:30",
                "giờ giấc 06:00 - 23:00",
                "giảm 50% phí");
    }

    /** E06S2-D8, refused on purpose: the accepted over-matches (6 vectors). */
    static Stream<String> acceptedOverMatches() {
        return Stream.of(
                "trống từ 06.11.2026 09:00",
                "01.12.2026 - 01.12.2027",
                "8.500.000 - 9.000.000 đồng",
                "8.500.000 + 500.000 đồng",
                "120.000.000.000",
                "phòng 101 - 102 - 103 - 104");
    }

    /** E06S2-D8, not refused: the stated limits the reviewer catches (5 vectors). */
    static Stream<String> statedLimits() {
        return Stream.of(
                "0905/123/456",
                "0905,123,456",
                "0905a123b456",
                "o905 123 456",
                "không chín không năm một hai ba bốn năm sáu");
    }

    /** listing BR-11's ten phrases, "100 %", the eight unaccented forms and an all-capitals one. */
    static Stream<String> forbiddenPhrases() {
        return Stream.of(
                "đã kiểm chứng",
                "đã xác minh",
                "xác thực",
                "verified",
                "chính chủ",
                "uy tín",
                "an toàn",
                "đảm bảo",
                "cam kết",
                "100%",
                "100 %",
                "chinh chu",
                "uy tin",
                "an toan",
                "dam bao",
                "cam ket",
                "da kiem chung",
                "da xac minh",
                "xac thuc",
                "CHÍNH CHỦ");
    }

    @Test
    void vectorLists_haveTheSpecsCounts() {
        assertEquals(38, refusedPhoneVectors().count());
        assertEquals(12, notRefusedVectors().count());
        assertEquals(6, acceptedOverMatches().count());
        assertEquals(5, statedLimits().count());
        assertEquals(20, forbiddenPhrases().count());
    }

    @ParameterizedTest
    @MethodSource("refusedPhoneVectors")
    void broken_refusedPhoneVectors_phoneNumber(String vector) {
        assertTrue(ListingTextRules.broken(vector).contains(RevisionRule.PHONE_NUMBER));
        assertTrue(ListingTextRules.broken("Phòng đẹp, liên hệ " + vector + " nhé")
                .contains(RevisionRule.PHONE_NUMBER));
    }

    @ParameterizedTest
    @MethodSource("notRefusedVectors")
    void broken_notRefusedVectors_noPhoneNumber(String vector) {
        assertFalse(ListingTextRules.broken(vector).contains(RevisionRule.PHONE_NUMBER));
    }

    @ParameterizedTest
    @MethodSource("acceptedOverMatches")
    void broken_acceptedOverMatches_phoneNumber(String vector) {
        assertTrue(ListingTextRules.broken(vector).contains(RevisionRule.PHONE_NUMBER));
    }

    @ParameterizedTest
    @MethodSource("statedLimits")
    void broken_statedLimits_noPhoneNumber(String vector) {
        assertFalse(ListingTextRules.broken(vector).contains(RevisionRule.PHONE_NUMBER));
    }

    @Test
    void broken_email_emailOnly() {
        assertEquals(EnumSet.of(RevisionRule.EMAIL), ListingTextRules.broken("nhatro@gmail.com"));
        assertEquals(EnumSet.of(RevisionRule.EMAIL), ListingTextRules.broken("Liên hệ NhaTro@Gmail.com nhé"));
    }

    @Test
    void broken_links_link() {
        for (String text : List.of("www.abc.vn", "https://x", "nha-tro-abc.com", "facebook.com/abc", "HTTP://X.Y")) {
            assertTrue(ListingTextRules.broken(text).contains(RevisionRule.LINK), text);
        }
    }

    @Test
    void broken_decimalsAndPrices_noLink() {
        for (String text : List.of("8.500.000", "25,5 m2", "Q.1")) {
            assertFalse(ListingTextRules.broken(text).contains(RevisionRule.LINK), text);
        }
    }

    @Test
    void broken_handles_socialHandle() {
        for (String text : List.of("@nhatro.abc", "zalo", "Zalo: nhatro", "FB", "facebook.com/abc")) {
            assertTrue(ListingTextRules.broken(text).contains(RevisionRule.SOCIAL_HANDLE), text);
        }
        assertTrue(ListingTextRules.broken("facebook.com/abc").contains(RevisionRule.LINK));
    }

    @Test
    void broken_wordInsideAnother_noSocialHandle() {
        for (String text : List.of("fbi", "zalovn")) {
            assertFalse(ListingTextRules.broken(text).contains(RevisionRule.SOCIAL_HANDLE), text);
        }
    }

    @ParameterizedTest
    @MethodSource("forbiddenPhrases")
    void broken_forbiddenPhrases_forbiddenWord(String phrase) {
        assertTrue(ListingTextRules.broken(phrase).contains(RevisionRule.FORBIDDEN_WORD));
        assertTrue(ListingTextRules.broken("Nhà " + phrase + " nhé.").contains(RevisionRule.FORBIDDEN_WORD));
    }

    @Test
    void broken_accentedLookalike_noForbiddenWord() {
        for (String text : List.of("ăn toàn", "bảo đảm", "Phòng 1000% đẹp")) {
            assertFalse(ListingTextRules.broken(text).contains(RevisionRule.FORBIDDEN_WORD), text);
        }
    }

    @Test
    void broken_null_empty() {
        assertTrue(ListingTextRules.broken(null).isEmpty());
        assertTrue(ListingTextRules.broken("Phòng trọ mới, sạch sẽ, gần chợ.").isEmpty());
    }
}
