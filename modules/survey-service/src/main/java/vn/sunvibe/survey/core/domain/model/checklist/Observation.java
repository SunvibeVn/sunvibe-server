package vn.sunvibe.survey.core.domain.model.checklist;

/** A checked result: nothing observed, or a note of what was (survey-checklist spec 4.6, BR-16). */
public record Observation(Boolean noneObserved, String note) {

    @Override
    public String toString() {
        return "Observation[" + noneObserved + "]";
    }
}
