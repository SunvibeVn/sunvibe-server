package vn.sunvibe.property.core.application.usecase.area;

/**
 * A ward with the name of its province, so a caller needs no second lookup. {@code type} is the name of the ward type
 * (WARD, COMMUNE or SPECIAL_ZONE), so a caller in another module needs no domain class.
 */
public record WardView(String code, String name, String type, String provinceCode, String provinceName) {
}
