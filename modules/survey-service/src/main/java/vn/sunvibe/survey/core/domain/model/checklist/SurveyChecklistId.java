package vn.sunvibe.survey.core.domain.model.checklist;

import com.github.f4b6a3.ulid.Ulid;
import com.github.f4b6a3.ulid.UlidCreator;

import java.util.Objects;

/** Identity of a survey checklist: a ULID (ADR-006). */
public final class SurveyChecklistId {

    private final String value;

    private SurveyChecklistId(String value) {
        if (!isValid(value)) {
            throw new IllegalArgumentException("SurveyChecklistId must be a ULID.");
        }
        this.value = value;
    }

    public static SurveyChecklistId generate() {
        return new SurveyChecklistId(UlidCreator.getUlid().toString());
    }

    public static SurveyChecklistId of(String value) {
        return new SurveyChecklistId(value);
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
        return this == other || other instanceof SurveyChecklistId id && value.equals(id.value);
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
