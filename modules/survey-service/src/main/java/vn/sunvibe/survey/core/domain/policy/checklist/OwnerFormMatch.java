package vn.sunvibe.survey.core.domain.policy.checklist;

import vn.sunvibe.survey.core.domain.model.checklist.ObservedLocation;
import vn.sunvibe.survey.core.domain.model.checklist.RentMoney;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Whether an "owner's form" value is the one the owner submitted (survey-checklist BR-21, spec A-18): both sides as
 * intake normalises them (NFC, trimmed, whitespace runs collapsed, {@code OwnerLead.normalizeText}), pins at 6
 * decimals.
 */
public final class OwnerFormMatch {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final int PIN_SCALE = 6;

    private OwnerFormMatch() {
    }

    /** Address detail, province, ward and pin; a submission without a pin matches only an address without one. */
    public static boolean matchesAddress(ObservedLocation value, SubmittedForm form) {
        return Objects.equals(text(value.addressText()), text(form.addressDetail()))
                && Objects.equals(value.provinceCode(), form.provinceCode())
                && Objects.equals(value.wardCode(), form.wardCode())
                && samePin(value.latitude(), form.latitude()) && samePin(value.longitude(), form.longitude());
    }

    /** The submitted asking price per month. */
    public static boolean matchesRent(RentMoney value, SubmittedForm form) {
        return form.monthlyAskingPrice() != null && form.monthlyAskingPrice().equals(value.amount());
    }

    /** The submitted available-from date. */
    public static boolean matchesAvailableFrom(LocalDate value, SubmittedForm form) {
        return form.availableFrom() != null && form.availableFrom().equals(value);
    }

    private static boolean samePin(BigDecimal value, BigDecimal submitted) {
        if (value == null || submitted == null) {
            return value == null && submitted == null;
        }
        return value.setScale(PIN_SCALE, RoundingMode.HALF_UP).compareTo(submitted.setScale(PIN_SCALE,
                RoundingMode.HALF_UP)) == 0;
    }

    private static String text(String value) {
        return value == null ? null
                : WHITESPACE.matcher(Normalizer.normalize(value, Normalizer.Form.NFC).strip()).replaceAll(" ");
    }
}
