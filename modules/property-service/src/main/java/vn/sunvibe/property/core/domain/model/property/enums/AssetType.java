package vn.sunvibe.property.core.domain.model.property.enums;

/**
 * The six asset types of the rental scope (G2 D-46, D-47), as a surveyed lead declares them. Stored as text in
 * {@code properties.asset_type} and {@code inventory_intakes.asset_type}. Each has the three letters of its code and the
 * label of its title (property-create intake A-3, A-4).
 */
public enum AssetType {
    HOUSE("HOU", "Whole house"),
    WHOLE_BUILDING("BLD", "Whole building"),
    VILLA("VIL", "Villa"),
    HOMESTAY("HMS", "Homestay"),
    CONDOTEL("CDT", "Condotel apartment"),
    APARTMENT("APT", "Apartment");

    private final String codePrefix;
    private final String label;

    AssetType(String codePrefix, String label) {
        this.codePrefix = codePrefix;
        this.label = label;
    }

    public String getCodePrefix() {
        return codePrefix;
    }

    public String getLabel() {
        return label;
    }
}
