package vn.sunvibe.survey.core.application.command;

/**
 * The intake declaration of a start by someone who did not perform the visit (survey-checklist spec 4.5, BR-14): where
 * the information comes from and its basis.
 */
public record IntakeInput(String reason, BasisInput basis) {

    /** Staff text: never print it. */
    @Override
    public String toString() {
        return "IntakeInput[hidden]";
    }
}
