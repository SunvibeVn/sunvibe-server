package vn.sunvibe.property.core.domain.policy.intake;

import vn.sunvibe.property.core.domain.model.property.enums.AssetType;
import vn.sunvibe.property.core.domain.model.intake.CaseSignal;

import java.util.Collections;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The duplicate signal (property-create intake D5-14, example 4, property-create BR-21). The caller matches the ward; here the normalized
 * address must be equal or the pins within 50 m. An apartment or condotel against the same kind also needs an equal space
 * code, so A1205 and A1206 of one building do not meet. Every other pair, and a legacy row with no asset type, hits on place
 * alone. It is a signal, not proof.
 */
public final class DuplicateSignal {

    public static final double PIN_RADIUS_METERS = 50.0;

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;
    private static final Pattern NOT_A_CODE_CHARACTER = Pattern.compile("[^\\p{L}\\p{N}]");

    private DuplicateSignal() {
    }

    /** One side of a comparison. Address and codes are private: never printed. */
    public record Place(AssetType assetTypeOrNull, String addressNorm, Double latitude, Double longitude,
                        Set<String> codes) {

        @Override
        public String toString() {
            return "Place[hidden]";
        }
    }

    public static Optional<CaseSignal> hit(Place intake, Place candidate) {
        Optional<CaseSignal> place = placeSignal(intake, candidate);
        if (place.isEmpty() || !needsEqualCode(intake, candidate) || shareACode(intake, candidate)) {
            return place;
        }
        return Optional.empty();
    }

    /** Whether two places are one place, for the check of a link (property-create intake spec 4.7). */
    public static boolean samePlace(Place first, Place second) {
        return placeSignal(first, second).isPresent();
    }

    /** Uppercase, with only letters and digits kept. */
    public static String normalizedCode(String code) {
        return NOT_A_CODE_CHARACTER.matcher(code.toUpperCase(Locale.ROOT)).replaceAll("");
    }

    private static Optional<CaseSignal> placeSignal(Place first, Place second) {
        if (first.addressNorm() != null && first.addressNorm().equals(second.addressNorm())) {
            return Optional.of(CaseSignal.ADDRESS);
        }
        return withinRadius(first, second) ? Optional.of(CaseSignal.PIN) : Optional.empty();
    }

    private static boolean needsEqualCode(Place intake, Place candidate) {
        AssetType type = intake.assetTypeOrNull();
        return (type == AssetType.APARTMENT || type == AssetType.CONDOTEL) && type == candidate.assetTypeOrNull();
    }

    private static boolean shareACode(Place intake, Place candidate) {
        return !Collections.disjoint(normalized(intake.codes()), normalized(candidate.codes()));
    }

    private static Set<String> normalized(Set<String> codes) {
        return codes.stream().map(DuplicateSignal::normalizedCode).collect(Collectors.toSet());
    }

    /** The haversine distance (A-6). */
    private static boolean withinRadius(Place first, Place second) {
        if (first.latitude() == null || first.longitude() == null || second.latitude() == null
                || second.longitude() == null) {
            return false;
        }
        double latitude1 = Math.toRadians(first.latitude());
        double latitude2 = Math.toRadians(second.latitude());
        double deltaLatitude = latitude2 - latitude1;
        double deltaLongitude = Math.toRadians(second.longitude() - first.longitude());
        double a = Math.pow(Math.sin(deltaLatitude / 2), 2)
                + Math.cos(latitude1) * Math.cos(latitude2) * Math.pow(Math.sin(deltaLongitude / 2), 2);
        double meters = 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1.0, Math.sqrt(a)));
        return meters <= PIN_RADIUS_METERS;
    }
}
