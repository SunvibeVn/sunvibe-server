package vn.sunvibe.shared.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Vietnamese phone numbers in staff free text (survey-schedule spec A-14, W15-7 vectors). */
class PhoneNumberDetectorTest {

    @Test
    void containsVietnamesePhoneNumber_phoneWrittenInAnyCommonWay_isDetected() {
        for (String text : new String[]{"0905 123 456", "+84.905.123.456", "(+84) 905 123 456", "84905123456",
                "0236 3812 345", "gọi 0905-123-456 nhé", "(0236) 3812-345", "SĐT:0905123456."}) {
            assertTrue(PhoneNumberDetector.containsVietnamesePhoneNumber(text), text);
        }
    }

    /** Text pasted from a web page, a document or a phone's contacts often carries no-break spaces or wide digits. */
    @Test
    void containsVietnamesePhoneNumber_unicodeSpacesOrFullwidthDigits_isDetected() {
        for (String text : new String[]{"0905 123 456", "0905 123 456", "0905 123 456",
                "０９０５１２３４５６", "gọi +84 905 123 456"}) {
            assertTrue(PhoneNumberDetector.containsVietnamesePhoneNumber(text), text);
        }
    }

    /** Editors turn " - " between numbers into an en dash; invisible characters can sit between the digits. */
    @Test
    void containsVietnamesePhoneNumber_unicodeDashesOrInvisibleCharacters_isDetected() {
        for (String text : new String[]{"0905–123–456", "0905 – 123 – 456", "0905‑123‑456",
                "0905—123—456", "0905−123−456", "0905​123​456", "0905­123­456",
                "0905⁠123⁠456"}) {
            assertTrue(PhoneNumberDetector.containsVietnamesePhoneNumber(text), text);
        }
        assertFalse(PhoneNumberDetector.containsVietnamesePhoneNumber("giá 15 000 000 đồng"));
        assertFalse(PhoneNumberDetector.containsVietnamesePhoneNumber("25–10–2026"));
    }

    @Test
    void containsVietnamesePhoneNumber_addressesDatesAndBlank_areNotPhones() {
        for (String text : new String[]{"tầng 12, căn 1203", "K12/5 Lê Duẩn", "25-10-2026", "", "   ",
                "giá 15 000 000 đồng", "0905 123 45", "10:00 - 11:30"}) {
            assertFalse(PhoneNumberDetector.containsVietnamesePhoneNumber(text), text);
        }
        assertFalse(PhoneNumberDetector.containsVietnamesePhoneNumber(null));
    }

    @Test
    void containsVietnamesePhoneNumber_longerDigitRun_isNotAPhone() {
        assertFalse(PhoneNumberDetector.containsVietnamesePhoneNumber("mã 1090512345678"));
    }

    /**
     * Pin rows P-1 to P-10 of the SV-212 vector table (survey-schedule phone detector spec 2026-11-15, section 5):
     * amounts, space codes, times and dates that are not numbers, and the numbers that are, keep their answer.
     */
    @Test
    void containsVietnamesePhoneNumber_amountsCodesAndTimes_keepTodaysAnswer() {
        for (String text : new String[]{"giá 15.000.000 đồng", "1.250.000", "T3-01.05", "từ 08:30 đến 17:00",
                "06.11.2026-07.11.2026", "2026-11-06 09:00"}) {
            assertFalse(PhoneNumberDetector.containsVietnamesePhoneNumber(text), text);
        }
        for (String text : new String[]{"84.000.000.000", "Owner mobile 0905 123 456",
                "Call the owner on 0901 234 567", "0901234567"}) {
            assertTrue(PhoneNumberDetector.containsVietnamesePhoneNumber(text), text);
        }
    }

    /** SV-212 rows R-1 to R-4: a valid date and a clock time in a note are not a phone number (owner 2026-11-14). */
    @Test
    void containsVietnamesePhoneNumber_dateWithTime_isNotAPhone() {
        for (String text : new String[]{"Gặp chủ 06.11.2026 09:00", "Gặp chủ 06.11.2026 09h00",
                "Gặp chủ 06-11-2026 10 giờ", "Gặp chủ 06. 11. 2026 09:00"}) {
            assertFalse(PhoneNumberDetector.containsVietnamesePhoneNumber(text), text);
        }
    }

    /**
     * SV-212 rows R-5 to R-8: a number written right after a floor number, before a duration or after a date is
     * still found, and a date does not hide a number (owner 2026-11-14).
     */
    @Test
    void containsVietnamesePhoneNumber_numberRightAfterAnotherNumber_isDetected() {
        for (String text : new String[]{"Tầng 3 0905 123 456", "0905 123 456 15 phút", "06.11.2026 0905 123 456",
                "Gặp chủ 06.11.2026 09:00, gọi 0905123456"}) {
            assertTrue(PhoneNumberDetector.containsVietnamesePhoneNumber(text), text);
        }
    }

    /** SV-212 rows R-9 and R-10: a colon is an edge, and the clock time mask does not hide a number next to it. */
    @Test
    void containsVietnamesePhoneNumber_numberNextToAColon_isDetected() {
        for (String text : new String[]{"SĐT1:0905123456", "0905123456:15"}) {
            assertTrue(PhoneNumberDetector.containsVietnamesePhoneNumber(text), text);
        }
    }
}
