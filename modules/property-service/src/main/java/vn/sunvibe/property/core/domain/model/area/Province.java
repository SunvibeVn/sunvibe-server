package vn.sunvibe.property.core.domain.model.area;

import java.util.Objects;

/** A province or centrally run city of the official catalog, identified by its official code (e.g. 48 Đà Nẵng). */
public final class Province {

    private final String code;
    private final String name;

    private Province(String code, String name) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Province code cannot be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Province name cannot be blank");
        }
        this.code = code;
        this.name = name;
    }

    /** Rebuilds a province from the stored catalog row. */
    public static Province load(String code, String name) {
        return new Province(code, name);
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return code.equals(((Province) o).code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code);
    }
}
