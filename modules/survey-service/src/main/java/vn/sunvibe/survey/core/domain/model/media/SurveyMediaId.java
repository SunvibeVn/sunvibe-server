package vn.sunvibe.survey.core.domain.model.media;

import com.github.f4b6a3.ulid.Ulid;
import com.github.f4b6a3.ulid.UlidCreator;

import java.util.Objects;

/** Identity of a survey photo: a server ULID (ADR-006), time-ordered, so the orphan sweep can bound its listing. */
public final class SurveyMediaId {

    private final String value;

    private SurveyMediaId(String value) {
        if (!isValid(value)) {
            throw new IllegalArgumentException("SurveyMediaId must be a ULID.");
        }
        this.value = value;
    }

    public static SurveyMediaId generate() {
        return new SurveyMediaId(UlidCreator.getMonotonicUlid().toString());
    }

    public static SurveyMediaId of(String value) {
        return new SurveyMediaId(value);
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
        return this == other || other instanceof SurveyMediaId id && value.equals(id.value);
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
