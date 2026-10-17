package vn.sunvibe.property.core.domain.model.area;

import java.util.Objects;

/** A ward, commune or special zone of the official catalog; it belongs to exactly one province. */
public final class Ward {

    private final String code;
    private final String provinceCode;
    private final String name;
    private final WardType type;

    private Ward(String code, String provinceCode, String name, WardType type) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Ward code cannot be blank");
        }
        if (provinceCode == null || provinceCode.isBlank()) {
            throw new IllegalArgumentException("Ward province code cannot be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Ward name cannot be blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("Ward type cannot be null");
        }
        this.code = code;
        this.provinceCode = provinceCode;
        this.name = name;
        this.type = type;
    }

    /** Rebuilds a ward from the stored catalog row. */
    public static Ward load(String code, String provinceCode, String name, WardType type) {
        return new Ward(code, provinceCode, name, type);
    }

    public String getCode() {
        return code;
    }

    public String getProvinceCode() {
        return provinceCode;
    }

    public String getName() {
        return name;
    }

    public WardType getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return code.equals(((Ward) o).code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code);
    }
}
