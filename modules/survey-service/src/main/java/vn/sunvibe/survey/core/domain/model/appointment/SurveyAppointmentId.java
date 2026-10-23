package vn.sunvibe.survey.core.domain.model.appointment;

import com.github.f4b6a3.ulid.Ulid;
import com.github.f4b6a3.ulid.UlidCreator;

import java.util.Objects;

/**
 * Value Object representing SurveyAppointmentId: a ULID of 26 characters (ADR-006), stored as
 * {@code survey_appointments.id}.
 */
public final class SurveyAppointmentId {

    private final String value;

    private SurveyAppointmentId(String value) {
        if (!isValid(value)) {
            throw new IllegalArgumentException("SurveyAppointmentId must be a ULID.");
        }
        this.value = value;
    }

    public static SurveyAppointmentId generate() {
        return new SurveyAppointmentId(UlidCreator.getUlid().toString());
    }

    public static SurveyAppointmentId of(String value) {
        return new SurveyAppointmentId(value);
    }

    /** True when the text is a ULID, so an id from a URL can be looked up; false for null. */
    public static boolean isValid(String value) {
        return value != null && Ulid.isValid(value);
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof SurveyAppointmentId id && value.equals(id.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
