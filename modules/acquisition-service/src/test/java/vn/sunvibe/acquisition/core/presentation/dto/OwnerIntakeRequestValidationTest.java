package vn.sunvibe.acquisition.core.presentation.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadIntent;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadPropertyType;
import vn.sunvibe.acquisition.core.domain.model.lead.PriceUnit;
import vn.sunvibe.acquisition.core.presentation.dto.request.OwnerIntakeRequest;
import vn.sunvibe.acquisition.core.presentation.dto.request.OwnerIntakeRequest.AskingPriceRequest;
import vn.sunvibe.acquisition.core.presentation.dto.request.OwnerIntakeRequest.ConsentRequest;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bean validation of the public intake body (acquisition-owner-intake spec 6.3; W9-3, W9-4): each rule reports on the
 * field the client sent, with an English message that never repeats the submitted value. Rules that need the catalog
 * or the domain (phone format, unit for intent, notice version, coordinates) are checked by the service.
 */
class OwnerIntakeRequestValidationTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void closeFactory() {
        FACTORY.close();
    }

    @Test
    void validate_validBody_hasNoViolation() {
        assertEquals(Set.of(), fields(valid().build()));
    }

    @Test
    void validate_onlyRequiredFields_hasNoViolation() {
        Body body = new Body();
        body.contactName = "Lan";
        body.phone = "0909123456";
        body.intent = OwnerLeadIntent.SALE;
        body.provinceCode = "48";
        body.wardCode = "20242";
        body.addressDetail = "1 Bạch Đằng";
        body.consent = new ConsentRequest(true, "owner-intake-2026-10");

        assertEquals(Set.of(), fields(body.build()));
    }

    @Test
    void validate_missingContactName_reportsContactName() {
        assertViolationOn("contactName", b -> b.with(x -> x.contactName = null));
    }

    @Test
    void validate_contactName101Chars_reportsContactName() {
        assertViolationOn("contactName", b -> b.with(x -> x.contactName = "a".repeat(101)));
    }

    @Test
    void validate_contactName100Chars_isAccepted() {
        assertEquals(Set.of(), fields(valid().with(x -> x.contactName = "a".repeat(100)).build()));
    }

    @Test
    void validate_contactName100CharsWithSpacesAround_isAcceptedBecauseLengthCountsAfterTrim() {
        assertEquals(Set.of(), fields(valid().with(x -> x.contactName = "  " + "a".repeat(100) + " ").build()));
    }

    @Test
    void validate_contactNameBlankAfterTrim_reportsContactName() {
        assertViolationOn("contactName", b -> b.with(x -> x.contactName = "   "));
    }

    @Test
    void validate_missingPhone_reportsPhone() {
        assertViolationOn("phone", b -> b.with(x -> x.phone = null));
    }

    @Test
    void validate_missingIntent_reportsIntent() {
        assertViolationOn("intent", b -> b.with(x -> x.intent = null));
    }

    @Test
    void validate_missingProvinceCode_reportsProvinceCode() {
        assertViolationOn("provinceCode", b -> b.with(x -> x.provinceCode = null));
    }

    @Test
    void validate_missingWardCode_reportsWardCode() {
        assertViolationOn("wardCode", b -> b.with(x -> x.wardCode = null));
    }

    @Test
    void validate_missingAddressDetail_reportsAddressDetail() {
        assertViolationOn("addressDetail", b -> b.with(x -> x.addressDetail = null));
    }

    @Test
    void validate_addressDetail256Chars_reportsAddressDetail() {
        assertViolationOn("addressDetail", b -> b.with(x -> x.addressDetail = "a".repeat(256)));
    }

    @Test
    void validate_addressDetail255Chars_isAccepted() {
        assertEquals(Set.of(), fields(valid().with(x -> x.addressDetail = "a".repeat(255)).build()));
    }

    /** A 64-character local part and 63-character labels: only the 254 total limit can refuse it. */
    @Test
    void validate_email255Chars_reportsEmailLengthOnly() {
        String email = emailOfLength(255);

        Set<ConstraintViolation<OwnerIntakeRequest>> violations =
                VALIDATOR.validate(valid().with(x -> x.email = email).build());

        assertEquals(255, email.length());
        assertEquals(Set.of("Email must be at most 254 characters."), messagesOn("email", violations));
        assertEquals(Set.of("email"), pathsOf(violations));
    }

    @Test
    void validate_email254Chars_isAccepted() {
        String email = emailOfLength(254);

        assertEquals(254, email.length());
        assertEquals(Set.of(), fields(valid().with(x -> x.email = email).build()));
    }

    /** A blank name is reported once, as missing, never with the length message. */
    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t \n"})
    void validate_contactNameBlank_reportsOnlyTheRequiredMessage(String name) {
        Set<ConstraintViolation<OwnerIntakeRequest>> violations =
                VALIDATOR.validate(valid().with(x -> x.contactName = name).build());

        assertEquals(Set.of("Contact name is required."), messagesOn("contactName", violations));
        assertEquals(1, violations.size());
    }

    @Test
    void validate_emailWithoutDotInDomain_reportsEmail() {
        assertViolationOn("email", b -> b.with(x -> x.email = "a@b"));
    }

    @Test
    void validate_emailWithDotInDomain_isAccepted() {
        assertEquals(Set.of(), fields(valid().with(x -> x.email = "a@b.vn").build()));
    }

    @Test
    void validate_emailLeftOut_isAccepted() {
        assertEquals(Set.of(), fields(valid().with(x -> x.email = null).build()));
    }

    /**
     * Security gate F3: a body-sized email made of dots between two {@code @} is refused in linear time (the old
     * pattern backtracked quadratically on it).
     */
    @Test
    void validate_emailOfManyDotsBetweenTwoAts_isRefusedQuickly() {
        String email = "a@" + ".".repeat(64_000) + "@";

        Set<String> fields = assertTimeoutPreemptively(Duration.ofSeconds(1),
                () -> fields(valid().with(x -> x.email = email).build()));

        assertEquals(Set.of("email"), fields);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a@.b.vn", "a@b..vn", "a@b.vn.", "a@b.", "a@.vn", "a@b@c.vn"})
    void validate_emailWithAnEmptyDomainLabel_reportsEmail(String email) {
        assertViolationOn("email", b -> b.with(x -> x.email = email));
    }

    @ParameterizedTest
    @ValueSource(strings = {"lan@example.com", "lan.nguyen@mail.example.com.vn", "a@b.vn", "a+b@c-d.vn"})
    void validate_emailWithDottedDomain_isAccepted(String email) {
        assertEquals(Set.of(), fields(valid().with(x -> x.email = email).build()));
    }

    /**
     * Security gate F1: an absurd exponent is refused by bean validation on its field, cheaply (the checks compare
     * exponents, they never write the number out).
     */
    @ParameterizedTest
    @CsvSource({"latitude,1E+999999999", "latitude,-1E+999999999", "latitude,1E-999999999",
            "longitude,1E+999999999", "longitude,-1E+999999999", "longitude,1E-999999999"})
    void validate_coordinateWithAbsurdExponent_reportsThatFieldQuickly(String field, String value) {
        Body body = valid().with(x -> {
            if (field.equals("latitude")) {
                x.latitude = new BigDecimal(value);
            } else {
                x.longitude = new BigDecimal(value);
            }
        });

        Set<ConstraintViolation<OwnerIntakeRequest>> violations = assertTimeoutPreemptively(Duration.ofSeconds(1),
                () -> VALIDATOR.validate(body.build()));

        assertEquals(Set.of(field), pathsOf(violations));
        violations.forEach(v -> assertFalse(v.getMessage().contains("999999999"), v.getMessage()));
    }

    @ParameterizedTest
    @CsvSource({"latitude,90.0000001", "latitude,-90.0000001", "longitude,180.0000001", "longitude,-180.0000001",
            "latitude,16.12345678901", "longitude,108.12345678901"})
    void validate_coordinateOutOfRangeOrWithMoreThan10Decimals_reportsThatField(String field, String value) {
        assertViolationOn(field, b -> b.with(x -> {
            if (field.equals("latitude")) {
                x.latitude = new BigDecimal(value);
            } else {
                x.longitude = new BigDecimal(value);
            }
        }));
    }

    /** The box and the rounding to 6 decimals stay with the domain: a pin with 7 to 10 decimals reaches it. */
    @ParameterizedTest
    @CsvSource({"15.8499996,108.0000004", "16.0678,108.2208", "90,180", "-90,-180", "16.1234567891,108.1234567891",
            "21.0285,105.8542"})
    void validate_coordinateInRangeWithAtMost10Decimals_isAccepted(String latitude, String longitude) {
        assertEquals(Set.of(), fields(valid().with(x -> {
            x.latitude = new BigDecimal(latitude);
            x.longitude = new BigDecimal(longitude);
        }).build()));
    }

    @Test
    void validate_notes1001Chars_reportsNotes() {
        assertViolationOn("notes", b -> b.with(x -> x.notes = "a".repeat(1001)));
    }

    @Test
    void validate_notes1000Chars_isAccepted() {
        assertEquals(Set.of(), fields(valid().with(x -> x.notes = "a".repeat(1000)).build()));
    }

    @Test
    void validate_preferredCallbackTime101Chars_reportsPreferredCallbackTime() {
        assertViolationOn("preferredCallbackTime", b -> b.with(x -> x.preferredCallbackTime = "a".repeat(101)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5", "1000000000000000", "1500.5", "1500.0"})
    void validate_askingPriceAmountNotAWholeNumberFrom1To15Digits_reportsAskingPriceAmount(String amount) {
        assertViolationOn("askingPrice.amount", b -> b.with(x -> x.askingPrice =
                new AskingPriceRequest(new BigDecimal(amount), "VND", PriceUnit.MONTH)));
    }

    @Test
    void validate_askingPriceAmount15Digits_isAccepted() {
        assertEquals(Set.of(), fields(valid().with(x -> x.askingPrice =
                new AskingPriceRequest(new BigDecimal("999999999999999"), "VND", PriceUnit.MONTH)).build()));
    }

    @Test
    void validate_askingPriceCurrencyUsd_reportsAskingPriceCurrency() {
        assertViolationOn("askingPrice.currency", b -> b.with(x -> x.askingPrice =
                new AskingPriceRequest(new BigDecimal("15000000"), "USD", PriceUnit.MONTH)));
    }

    /** The service reports a price without amount on the same field (acquisition-owner-intake spec 6.3). */
    @Test
    void validate_askingPriceWithoutAmount_reportsAskingPriceAmount() {
        assertViolationOn("askingPrice.amount", b -> b.with(x -> x.askingPrice =
                new AskingPriceRequest(null, "VND", PriceUnit.MONTH)));
    }

    @Test
    void validate_askingPriceLeftOut_isAccepted() {
        assertEquals(Set.of(), fields(valid().with(x -> x.askingPrice = null).build()));
    }

    @Test
    void validate_missingConsent_reportsConsent() {
        assertViolationOn("consent", b -> b.with(x -> x.consent = null));
    }

    @Test
    void validate_consentNotAccepted_reportsConsentAccepted() {
        assertViolationOn("consent.accepted", b -> b.with(x -> x.consent =
                new ConsentRequest(false, "owner-intake-2026-10")));
    }

    @Test
    void validate_consentAcceptedMissing_reportsConsentAccepted() {
        assertViolationOn("consent.accepted", b -> b.with(x -> x.consent =
                new ConsentRequest(null, "owner-intake-2026-10")));
    }

    @Test
    void validate_consentNoticeVersionMissing_reportsConsentNoticeVersion() {
        assertViolationOn("consent.noticeVersion", b -> b.with(x -> x.consent = new ConsentRequest(true, null)));
    }

    /** acquisition module: messages are English and never echo what was submitted. */
    @Test
    void validate_violationMessages_neverContainTheSubmittedValue() {
        String marker = "Zq9Marker";
        Body body = valid().with(x -> {
            x.contactName = marker.repeat(20);
            x.email = marker + "@nodot";
            x.notes = marker.repeat(200);
        });

        Set<ConstraintViolation<OwnerIntakeRequest>> violations = VALIDATOR.validate(body.build());

        assertEquals(Set.of("contactName", "email", "notes"), pathsOf(violations));
        violations.forEach(v -> assertFalse(v.getMessage().contains(marker), v.getMessage()));
    }

    @Test
    void toString_body_hidesPersonalData() {
        String text = valid().build().toString();

        assertFalse(text.contains("0909"), text);
        assertFalse(text.contains("Lan"), text);
        assertTrue(text.contains("hidden"), text);
    }

    private static void assertViolationOn(String field, UnaryOperator<Body> change) {
        assertEquals(Set.of(field), fields(change.apply(valid()).build()));
    }

    private static Set<String> fields(OwnerIntakeRequest request) {
        return pathsOf(VALIDATOR.validate(request));
    }

    private static Set<String> pathsOf(Set<ConstraintViolation<OwnerIntakeRequest>> violations) {
        return violations.stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    private static Set<String> messagesOn(String field, Set<ConstraintViolation<OwnerIntakeRequest>> violations) {
        return violations.stream()
                .filter(v -> v.getPropertyPath().toString().equals(field))
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    /**
     * An email that passes every format rule (64-character local part, labels of at most 63 characters, a dot in the
     * domain) and has exactly {@code length} characters, from 197 up.
     */
    static String emailOfLength(int length) {
        String local = "a".repeat(64);
        String fixedLabels = "b".repeat(63) + "." + "c".repeat(63) + ".";
        int lastLabel = length - local.length() - 1 - fixedLabels.length() - ".vn".length();
        return local + "@" + fixedLabels + "d".repeat(lastLabel) + ".vn";
    }

    /** The example body of spec 6.3. */
    private static Body valid() {
        Body body = new Body();
        body.contactName = "Nguyễn Thị Lan";
        body.phone = "0909 123 456";
        body.email = "lan@example.com";
        body.intent = OwnerLeadIntent.LONG_TERM_RENT;
        body.provinceCode = "48";
        body.wardCode = "20242";
        body.addressDetail = "12 Bạch Đằng, căn A-1203";
        body.latitude = new BigDecimal("16.0678");
        body.longitude = new BigDecimal("108.2208");
        body.propertyType = OwnerLeadPropertyType.TWO_BEDROOM;
        body.askingPrice = new AskingPriceRequest(new BigDecimal("15000000"), "VND", PriceUnit.MONTH);
        body.availableFrom = LocalDate.parse("2026-11-01");
        body.notes = "Có nội thất";
        body.preferredCallbackTime = "Buổi tối";
        body.consent = new ConsentRequest(true, "owner-intake-2026-10");
        return body;
    }

    private static final class Body {
        String contactName;
        String phone;
        String email;
        OwnerLeadIntent intent;
        String provinceCode;
        String wardCode;
        String addressDetail;
        BigDecimal latitude;
        BigDecimal longitude;
        OwnerLeadPropertyType propertyType;
        AskingPriceRequest askingPrice;
        LocalDate availableFrom;
        String notes;
        String preferredCallbackTime;
        ConsentRequest consent;

        Body with(java.util.function.Consumer<Body> change) {
            change.accept(this);
            return this;
        }

        OwnerIntakeRequest build() {
            return new OwnerIntakeRequest(contactName, phone, email, intent, provinceCode, wardCode, addressDetail,
                    latitude, longitude, propertyType, askingPrice, availableFrom, notes, preferredCallbackTime,
                    consent);
        }
    }
}
