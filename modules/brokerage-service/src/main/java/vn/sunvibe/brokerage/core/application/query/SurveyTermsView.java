package vn.sunvibe.brokerage.core.application.query;

import vn.sunvibe.brokerage.core.domain.model.agreement.VersionTerms;

import java.time.LocalDate;
import java.util.List;

/**
 * The survey proposal of a draft's terms (E05-S3 spec S3-D11 to S3-D13): the parts the survey holds within the rules of
 * brokerage-agreement BR-8, each with its source and date. Never stored: a proposal the Sale may copy into the form.
 *
 * @param sources one per part held, in the order monthlyRent, deposit, minimumTermMonths, paymentPeriodMonths,
 *                mandatoryFees[i]
 */
public record SurveyTermsView(VersionTerms terms, List<TermSource> sources) {

    /** The survey holds no value for these terms. */
    public static final SurveyTermsView EMPTY = new SurveyTermsView(VersionTerms.NONE, List.of());

    public SurveyTermsView {
        sources = List.copyOf(sources);
    }

    /**
     * Where one part of the proposal came from.
     *
     * @param field        the part's path, such as {@code monthlyRent} or {@code mandatoryFees[2]}
     * @param sourceOrNull OBSERVED_ON_SITE, OWNER_STATED or OWNER_FORM; null when the survey gives none of them
     * @param date         the date of the survey's statement, else the day of the visit
     */
    public record TermSource(String field, String sourceOrNull, LocalDate date) {
    }
}
