package vn.sunvibe.survey.core.domain.policy.checklist;

import vn.sunvibe.survey.core.domain.model.checklist.Violation;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Date rules of survey checklists on the Vietnam calendar (survey-checklist spec A-7, owner-confirmed at gate 1, C-5).
 * The caller passes today as {@code SurveyTimeRules.vietnamDate(now)}; nothing here reads a clock. Each rule returns
 * the violation on the given path, or empty when the date is in range.
 */
public final class ChecklistDateRules {

    /** A technical floor for dates staff type (spec A-7). */
    public static final LocalDate FLOOR = LocalDate.of(2000, 1, 1);
    /** How far ahead an availability or vacancy date may lie (spec A-7). */
    public static final int FUTURE_YEARS = 3;

    private ChecklistDateRules() {
    }

    /**
     * The day the owner said it ({@code confirmedOn}): 2000-01-01 to today; it may predate the lead (survey-checklist
     * BR-19).
     */
    public static Optional<Violation> requireStatementDate(String path, LocalDate date, LocalDate today) {
        return within(path, date, FLOOR, today, "2000-01-01", "today");
    }

    /** A takeover, intake or correction basis date: from the visit's arrival date to today (spec A-7). */
    public static Optional<Violation> requireBasisDate(String path, LocalDate date, LocalDate arrivalDate,
                                                       LocalDate today) {
        return within(path, date, arrivalDate, today, "the visit's arrival date", "today");
    }

    /**
     * The day the owner reported a fix ({@code ownerReportedOn}): from the arrival date of the visit where the hazard
     * was first recorded (the first hazard of its chain) to today (spec A-7).
     */
    public static Optional<Violation> requireReportDate(String path, LocalDate date, LocalDate firstSightingArrival,
                                                        LocalDate today) {
        return within(path, date, firstSightingArrival, today, "the date the hazard was first recorded", "today");
    }

    /** {@code availableFrom} and {@code expectedVacantFrom}: 2000-01-01 to three years after today (spec A-7). */
    public static Optional<Violation> requireFutureDate(String path, LocalDate date, LocalDate today) {
        return within(path, date, FLOOR, today.plusYears(FUTURE_YEARS), "2000-01-01", "three years from today");
    }

    private static Optional<Violation> within(String path, LocalDate date, LocalDate from, LocalDate to,
                                              String fromText, String toText) {
        if (date == null || date.isBefore(from) || date.isAfter(to)) {
            return Optional.of(new Violation(path, path + " must be between " + fromText + " and " + toText + "."));
        }
        return Optional.empty();
    }
}
