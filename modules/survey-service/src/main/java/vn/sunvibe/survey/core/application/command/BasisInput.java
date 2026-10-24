package vn.sunvibe.survey.core.application.command;

import java.time.LocalDate;

/** The basis of a takeover, intake declaration or correction as sent: date and text (survey-checklist spec 4.5). */
public record BasisInput(LocalDate confirmedOn, String text) {

    /** Staff text: never print it. */
    @Override
    public String toString() {
        return "BasisInput[" + confirmedOn + "]";
    }
}
