package vn.sunvibe.survey.core.application.command;

/**
 * Take a draft over from its holder (survey-checklist spec 4.5, 7.1): the checklist version read, the reason and the
 * basis. A null version is refused as a missing value.
 */
public record TakeOverChecklistCommand(String checklistId, Long expectedVersion, String reason, BasisInput basis) {

    /** Staff text: never print it. */
    @Override
    public String toString() {
        return "TakeOverChecklistCommand[" + checklistId + "]";
    }
}
