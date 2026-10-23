package vn.sunvibe.acquisition.core.application.shared;

import org.springframework.stereotype.Component;
import vn.sunvibe.acquisition.core.application.exception.IntakeValidationException;
import vn.sunvibe.acquisition.core.application.shared.external.area.AdministrativeAreaPort;
import vn.sunvibe.acquisition.core.application.shared.external.area.AdministrativeWard;
import vn.sunvibe.acquisition.core.domain.exception.InvalidIntakeValueException;
import vn.sunvibe.acquisition.core.domain.model.lead.GeoPoint;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * The location rules of an owner's property, shared by the intake form and the survey meeting place
 * (acquisition-owner-intake BR-1, BR-6..BR-8; survey-schedule spec A-15: one implementation): province and ward come
 * from the catalog and the ward belongs to the province; a map pin is optional, given as a pair, inside the served
 * box and never on a special-zone ward.
 */
@Component
public class IntakeLocationRules {

    private final AdministrativeAreaPort administrativeAreas;

    public IntakeLocationRules(AdministrativeAreaPort administrativeAreas) {
        this.administrativeAreas = administrativeAreas;
    }

    /**
     * The ward from the catalog. An unknown province is reported on the province; an unknown ward, or a ward of
     * another province, on the ward (acquisition-owner-intake BR-1).
     *
     * @throws IntakeValidationException field {@code provinceCode} or {@code wardCode}
     */
    public AdministrativeWard resolveWard(String provinceCode, String wardCode) {
        if (administrativeAreas.findWards(provinceCode).isEmpty()) {
            throw new IntakeValidationException("provinceCode", "Province is not in the catalog.");
        }
        return administrativeAreas.findWard(wardCode)
                .filter(ward -> ward.provinceCode().equals(provinceCode))
                .orElseThrow(() -> new IntakeValidationException("wardCode",
                        "Ward is not in the catalog of the chosen province."));
    }

    /**
     * The map pin in the ward, rounded to 6 decimals, or empty when both coordinates are left out
     * (acquisition-owner-intake BR-6..BR-8).
     *
     * @throws IntakeValidationException field {@code latitude}
     */
    public Optional<GeoPoint> geoPoint(BigDecimal latitude, BigDecimal longitude, AdministrativeWard ward) {
        try {
            return GeoPoint.of(latitude, longitude, ward.isSpecialZone());
        } catch (InvalidIntakeValueException e) {
            throw IntakeValidationException.from(e);
        }
    }
}
