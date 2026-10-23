package vn.sunvibe.acquisition.core.application.shared;

import org.junit.jupiter.api.Test;
import vn.sunvibe.acquisition.core.application.exception.IntakeValidationException;
import vn.sunvibe.acquisition.core.application.shared.external.area.AdministrativeWard;
import vn.sunvibe.acquisition.core.domain.model.lead.GeoPoint;
import vn.sunvibe.acquisition.core.testsupport.InMemoryAdministrativeAreaPort;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The intake location rules shared by the intake form and the survey meeting place (acquisition-owner-intake BR-1,
 * BR-6..BR-8; survey-schedule spec A-15): province and ward from the catalog, the ward in its province, a pin inside the
 * served box and never on a special-zone ward.
 */
class IntakeLocationRulesTest {

    private static final AdministrativeWard HAI_CHAU = new AdministrativeWard("20194", "Hải Châu", "WARD", "48",
            "Đà Nẵng");
    private static final AdministrativeWard HOANG_SA = new AdministrativeWard("20333", "Hoàng Sa", "SPECIAL_ZONE",
            "48", "Đà Nẵng");
    private static final AdministrativeWard HUE_WARD = new AdministrativeWard("19999", "Phú Hội", "WARD", "46",
            "Huế");

    private final IntakeLocationRules rules = new IntakeLocationRules(
            new InMemoryAdministrativeAreaPort().with(HAI_CHAU).with(HOANG_SA).with(HUE_WARD));

    @Test
    void resolveWard_knownWardOfItsProvince_returnsItWithNames() {
        AdministrativeWard ward = rules.resolveWard("48", "20194");

        assertEquals("Hải Châu", ward.name());
        assertEquals("Đà Nẵng", ward.provinceName());
    }

    @Test
    void resolveWard_unknownProvince_failsOnProvinceCode() {
        assertEquals("provinceCode", assertThrows(IntakeValidationException.class,
                () -> rules.resolveWard("99", "20194")).getFieldName());
    }

    @Test
    void resolveWard_unknownWardOrWardOfAnotherProvince_failsOnWardCode() {
        assertEquals("wardCode", assertThrows(IntakeValidationException.class,
                () -> rules.resolveWard("48", "00000")).getFieldName());
        assertEquals("wardCode", assertThrows(IntakeValidationException.class,
                () -> rules.resolveWard("48", "19999")).getFieldName());
    }

    @Test
    void geoPoint_insideTheBox_isRoundedToSixDecimals() {
        Optional<GeoPoint> point = rules.geoPoint(new BigDecimal("16.0544071"), new BigDecimal("108.2021669"),
                HAI_CHAU);

        assertEquals(new BigDecimal("16.054407"), point.orElseThrow().getLatitude());
        assertEquals(new BigDecimal("108.202167"), point.orElseThrow().getLongitude());
        assertEquals(Optional.empty(), rules.geoPoint(null, null, HAI_CHAU));
    }

    @Test
    void geoPoint_outsideTheBoxOrOnASpecialZone_failsOnLatitude() {
        assertEquals("latitude", assertThrows(IntakeValidationException.class,
                () -> rules.geoPoint(new BigDecimal("15.84"), new BigDecimal("108.2"), HAI_CHAU)).getFieldName());
        assertEquals("latitude", assertThrows(IntakeValidationException.class,
                () -> rules.geoPoint(new BigDecimal("16.0"), new BigDecimal("108.41"), HAI_CHAU)).getFieldName());
        assertEquals("latitude", assertThrows(IntakeValidationException.class,
                () -> rules.geoPoint(new BigDecimal("16.0"), new BigDecimal("108.2"), HOANG_SA)).getFieldName());
        assertEquals("latitude", assertThrows(IntakeValidationException.class,
                () -> rules.geoPoint(new BigDecimal("16.0"), null, HAI_CHAU)).getFieldName());
    }
}
