package vn.sunvibe.brokerage.core.domain.model.agreement;

import java.util.Arrays;
import java.util.Optional;

/**
 * The unit of a mandatory fee charged as an amount (brokerage-agreement BR-8: per month, per kWh, per m3, per person and
 * month, per vehicle and month, once), the names of survey-checklist BR-5.
 */
public enum ChargeUnit {
    PER_MONTH,
    PER_KWH,
    PER_M3,
    PER_PERSON_PER_MONTH,
    PER_VEHICLE_PER_MONTH,
    ONE_OFF;

    /** The unit of this exact name; empty for null or any other text, a different case included. */
    public static Optional<ChargeUnit> of(String codeOrNull) {
        return Arrays.stream(values()).filter(unit -> unit.name().equals(codeOrNull)).findFirst();
    }
}
