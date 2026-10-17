package vn.sunvibe.acquisition.core.domain.model;

/**
 * Base class of the value objects of this module: equality by value through {@code equalsCore}/{@code hashCodeCore}.
 * Same shape as the property-service base class; a module may not depend on another feature module (ADR-009).
 */
public abstract class ValueObject<T> {

    protected abstract boolean equalsCore(T other);

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
