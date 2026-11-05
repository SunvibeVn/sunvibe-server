package vn.sunvibe.property.core.domain.model.vo;

import vn.sunvibe.property.core.domain.model.enums.AssetType;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The code an intake property gets (property-create intake A-3): {@code SV-<TYP>-<6 digits>}, the number drawn from
 * {@code property_intake_code_seq}. The six prefixes are reserved: the legacy endpoint refuses them, so a client cannot
 * take a code the sequence will reach.
 */
public final class IntakePropertyCode {

    private static final Pattern RESERVED = Pattern.compile("^SV-(HOU|BLD|VIL|HMS|CDT|APT)-.*$");

    private IntakePropertyCode() {
    }

    public static PropertyCode of(AssetType assetType, long number) {
        return PropertyCode.of(String.format(Locale.ROOT, "SV-%s-%06d", assetType.getCodePrefix(), number));
    }

    public static boolean isReserved(String propertyCode) {
        return propertyCode != null && RESERVED.matcher(propertyCode).matches();
    }
}
