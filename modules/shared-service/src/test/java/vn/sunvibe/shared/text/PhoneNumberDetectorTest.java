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
}
