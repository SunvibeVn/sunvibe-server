package vn.sunvibe.survey.core.domain.policy;

import vn.sunvibe.survey.core.domain.model.appointment.Coordinates;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Whether two places differ (survey-schedule spec A-16): by their address (NFC, single-spaced, lower case), their
 * ward or their pin. Used for the meeting place against the lead and for the survey checklist's observed address
 * against the lead and the meeting place (survey-checklist spec 4.6).
 */
public final class PlaceComparison {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private PlaceComparison() {
    }

    public static boolean differs(String address, String wardCode, Coordinates coordinates, String otherAddress,
                                  String otherWardCode, Coordinates otherCoordinates) {
        return !addressKey(address).equals(addressKey(otherAddress)) || !Objects.equals(wardCode, otherWardCode)
                || !Objects.equals(coordinates, otherCoordinates);
    }

    /** The address as compared: NFC, trimmed, whitespace runs single-spaced, lower case. */
    public static String addressKey(String address) {
        return WHITESPACE.matcher(Normalizer.normalize(address, Normalizer.Form.NFC).strip()).replaceAll(" ")
                .toLowerCase(Locale.ROOT);
    }
}
