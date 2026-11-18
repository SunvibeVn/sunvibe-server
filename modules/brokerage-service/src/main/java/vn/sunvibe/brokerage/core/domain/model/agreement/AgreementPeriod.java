package vn.sunvibe.brokerage.core.domain.model.agreement;

import vn.sunvibe.brokerage.core.domain.exception.AgreementPeriodException;

import java.time.LocalDate;

/**
 * The start and end dates of an agreement version, calendar dates in Vietnam time. The first failing rule refuses it
 * with the field it is about.
 */
public record AgreementPeriod(LocalDate startDate, LocalDate endDate) {

    /** Technical lower bound of both dates, against date arithmetic on absurd values. */
    public static final LocalDate EARLIEST = LocalDate.of(2000, 1, 1);
    /** Technical upper bound of both dates. */
    public static final LocalDate LATEST = LocalDate.of(2099, 12, 31);

    private static final int MAX_MONTHS = 12;

    public AgreementPeriod {
        if (startDate == null) {
            throw new AgreementPeriodException("startDate");
        }
        if (endDate == null) {
            throw new AgreementPeriodException("endDate");
        }
        if (outsideBounds(startDate)) {
            throw new AgreementPeriodException("startDate");
        }
        if (outsideBounds(endDate)) {
            throw new AgreementPeriodException("endDate");
        }
        // brokerage-agreement BR-6: both dates are required and the end date is not before the start date.
        if (endDate.isBefore(startDate)) {
            throw new AgreementPeriodException("endDate");
        }
        // brokerage-agreement BR-6: the end date is at most 12 months after the start date; plusMonths keeps the day
        // and clamps it to the month's last day, as PostgreSQL's + INTERVAL '12 months' does in the version CHECK.
        if (endDate.isAfter(startDate.plusMonths(MAX_MONTHS))) {
            throw new AgreementPeriodException("endDate");
        }
    }

    private static boolean outsideBounds(LocalDate date) {
        return date.isBefore(EARLIEST) || date.isAfter(LATEST);
    }
}
