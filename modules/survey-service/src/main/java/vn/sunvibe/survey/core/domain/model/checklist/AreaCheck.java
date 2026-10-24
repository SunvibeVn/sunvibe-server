package vn.sunvibe.survey.core.domain.model.checklist;

/**
 * The check of a main space, kitchen, bathroom or shared wet area (survey-checklist spec 4.6, BR-17): INSPECTED needs
 * {@code noneObserved} and a note when something was seen; NOT_ENTERED needs what was not reached and why and may
 * carry a note of what was seen from outside or what the owner said, never a check. Texts: never printed.
 */
public record AreaCheck(AreaCheckResult result, Boolean noneObserved, String note, String notEnteredReason) {

    @Override
    public String toString() {
        return "AreaCheck[" + result + "]";
    }
}
