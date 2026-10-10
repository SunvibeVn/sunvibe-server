package vn.sunvibe.auth.core.domain.model.shared;

/**
 * Base class of value objects: two instances are equal when they are of the
 * same class and their attributes are equal, never because of identity.
 *
 * @param <T> the concrete value-object type
 */
public abstract class ValueObject<T> {

    /** Attribute-wise comparison with another instance of the same class. */
    protected abstract boolean equalsCore(T other);

    /** Hash code consistent with {@link #equalsCore}. */
    protected abstract int hashCodeCore();

    @Override
    @SuppressWarnings("unchecked")
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        return equalsCore((T) obj);
    }

    @Override
    public int hashCode() {
        return hashCodeCore();
    }
}
