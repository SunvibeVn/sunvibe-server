package vn.sunvibe.acquisition.core.application.shared.external.area;

/**
 * A ward with the name of its province. {@code type} is WARD, COMMUNE or SPECIAL_ZONE; a special zone is an island
 * ward where map coordinates are not accepted (acquisition-owner-intake BR-6).
 */
public record AdministrativeWard(String code, String name, String type, String provinceCode, String provinceName) {

    public boolean isSpecialZone() {
        return "SPECIAL_ZONE".equals(type);
    }
}
