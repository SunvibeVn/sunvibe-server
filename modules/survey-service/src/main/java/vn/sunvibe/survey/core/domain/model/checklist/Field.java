package vn.sunvibe.survey.core.domain.model.checklist;

/**
 * An unsourced checklist field (survey-checklist spec 4.6): its state, the value exactly when VALUE and the reason
 * exactly when NOT_APPLICABLE. A plain carrier: the template rules check it (spec PD-2), the constructor does not.
 */
public record Field<V>(FieldState state, V value, String naReason) {

    public static <V> Field<V> unknown() {
        return new Field<>(FieldState.UNKNOWN, null, null);
    }

    public static <V> Field<V> value(V value) {
        return new Field<>(FieldState.VALUE, value, null);
    }

    public static <V> Field<V> notApplicable(String reason) {
        return new Field<>(FieldState.NOT_APPLICABLE, null, reason);
    }

    public boolean isUnknown() {
        return state == FieldState.UNKNOWN;
    }

    public boolean isValue() {
        return state == FieldState.VALUE;
    }

    /** Values may be private: never print them. */
    @Override
    public String toString() {
        return "Field[" + state + "]";
    }
}
