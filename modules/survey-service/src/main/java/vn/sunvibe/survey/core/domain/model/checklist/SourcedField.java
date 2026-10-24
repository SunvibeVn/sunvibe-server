package vn.sunvibe.survey.core.domain.model.checklist;

import java.time.LocalDate;

/**
 * A sourced checklist field (survey-checklist spec 4.6): state, value and reason as {@link Field}, plus where the
 * value comes from. An owner statement carries the day the owner said it, the staff member who heard it and, when
 * someone else enters it, how it reached them (BR-21). A plain carrier: the template rules check it (spec PD-2).
 */
public record SourcedField<V>(FieldState state, V value, String naReason, ChecklistSource source,
                              LocalDate confirmedOn, String receivedByUserId, String relayBasis) {

    public static <V> SourcedField<V> unknown() {
        return new SourcedField<>(FieldState.UNKNOWN, null, null, null, null, null, null);
    }

    /** A value of a source without date or receiver (on site, owner's form). */
    public static <V> SourcedField<V> of(V value, ChecklistSource source) {
        return new SourcedField<>(FieldState.VALUE, value, null, source, null, null, null);
    }

    /** A value the owner said on the date to the receiver; the relay basis when someone else enters it. */
    public static <V> SourcedField<V> ownerStated(V value, LocalDate confirmedOn, String receivedByUserId,
                                                  String relayBasisOrNull) {
        return new SourcedField<>(FieldState.VALUE, value, null, ChecklistSource.OWNER_STATED, confirmedOn,
                receivedByUserId, relayBasisOrNull);
    }

    public static <V> SourcedField<V> notApplicable(String reason) {
        return new SourcedField<>(FieldState.NOT_APPLICABLE, null, reason, null, null, null, null);
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
        return "SourcedField[" + state + ", " + source + "]";
    }
}
