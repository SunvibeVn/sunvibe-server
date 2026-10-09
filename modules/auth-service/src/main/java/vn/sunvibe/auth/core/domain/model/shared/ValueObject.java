package vn.sunvibe.auth.core.domain.model.shared;

public abstract class ValueObject<T> {

    protected abstract boolean equalsCore(T other);

    protected abstract int hashCodeCore();

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        T other = (T) obj;
        return equalsCore(other);
    }

    @Override
    public int hashCode() {
        return hashCodeCore();
    }
}
