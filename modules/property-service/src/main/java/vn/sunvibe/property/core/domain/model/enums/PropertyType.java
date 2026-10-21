package vn.sunvibe.property.core.domain.model.enums;


/**
 * Enum representing the property type.
 */
public enum PropertyType {
    STUDIO("Studio", 0),
    ONE_BEDROOM("1 Bedroom", 1),
    TWO_BEDROOM("2 Bedrooms", 2),
    THREE_BEDROOM("3 Bedrooms", 3),
    FOUR_BEDROOM("4 Bedrooms", 4),
    DUPLEX("Duplex", -1),
    PENTHOUSE("Penthouse", -1);

    private final String displayName;
    private final int bedroomCount;

    PropertyType(String displayName, int bedroomCount) {
        this.displayName = displayName;
        this.bedroomCount = bedroomCount;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getBedroomCount() {
        return bedroomCount;
    }

    public static PropertyType fromDisplayName(String displayName) {
        for (PropertyType type : values()) {
            if (type.displayName.equalsIgnoreCase(displayName)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid property type: " + displayName);
    }
}
