package vn.sunvibe.property.core.domain.model.vo;

import java.util.Objects;

/**
 * The document type a property's document points to, with the type name the document row keeps. It is a reference,
 * not the master data itself: renewal rules are applied when the document is created (its expiry date).
 */
public class DocumentTypeRef extends ValueObject<DocumentTypeRef> {

    private final Integer id;            // null only for a stored row without its master id
    private final String name;

    private DocumentTypeRef(Integer id, String name) {
        // A stored row may have no master id (the column is nullable, without a foreign key); a new one always has
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("DocumentTypeId must be > 0");
        }
        this.id = id;
        this.name = name;
    }

    public static DocumentTypeRef of(Integer id, String name) {
        return new DocumentTypeRef(id, name);
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Override
    protected boolean equalsCore(DocumentTypeRef other) {
        return Objects.equals(id, other.id) && Objects.equals(name, other.name);
    }

    @Override
    protected int hashCodeCore() {
        return Objects.hash(id, name);
    }

    @Override
    public String toString() {
        return "DocumentTypeRef{id=" + id + ", name='" + name + "'}";
    }
}
