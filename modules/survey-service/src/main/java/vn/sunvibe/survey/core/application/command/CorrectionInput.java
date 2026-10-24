package vn.sunvibe.survey.core.application.command;

/** The correction basis of a save as sent (survey-checklist spec 4.5, 7.1): why, and the date and text of the basis. */
public record CorrectionInput(String reason, BasisInput basis) {

    /** Staff text: never print it. */
    @Override
    public String toString() {
        return "CorrectionInput[hidden]";
    }
}
