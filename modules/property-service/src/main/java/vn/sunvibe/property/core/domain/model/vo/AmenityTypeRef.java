package vn.sunvibe.property.core.domain.model.vo;

import java.util.Objects;

/**
 * The amenity type a property's amenity points to, with the names the amenity row keeps (amenity name, parent
 * category name, description). It is a reference, not the master data itself: the master row may change later.
 */
public class AmenityTypeRef extends ValueObject<AmenityTypeRef> {

    private final Integer id;            // null only for a stored row without its master id
    private final String name;
    private final String categoryName;   // Parent category name; null for a top-level type
    private final String description;

    private AmenityTypeRef(Integer id, String name, String categoryName, String description) {
        // A stored row may have no master id (the column is nullable, without a foreign key); a new one always has
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("AmenityTypeId must be > 0");
        }
        this.id = id;
        this.name = name;
        this.categoryName = categoryName;
        this.description = description;
    }

    public static AmenityTypeRef of(Integer id, String name, String categoryName, String description) {
        return new AmenityTypeRef(id, name, categoryName, description);
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public String getDescription() {
        return description;
    }

    @Override
    protected boolean equalsCore(AmenityTypeRef other) {
        return Objects.equals(id, other.id) && Objects.equals(name, other.name)
                && Objects.equals(categoryName, other.categoryName) && Objects.equals(description, other.description);
    }

    @Override
    protected int hashCodeCore() {
        return Objects.hash(id, name, categoryName, description);
    }

    @Override
    public String toString() {
        return "AmenityTypeRef{id=" + id + ", name='" + name + "'}";
    }
}
