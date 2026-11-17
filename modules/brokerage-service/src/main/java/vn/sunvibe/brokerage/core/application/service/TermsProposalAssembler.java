package vn.sunvibe.brokerage.core.application.service;

import vn.sunvibe.brokerage.core.application.query.SurveyTermsView;
import vn.sunvibe.brokerage.core.application.query.SurveyTermsView.TermSource;
import vn.sunvibe.brokerage.core.application.shared.external.survey.SourceTermsFacts;
import vn.sunvibe.brokerage.core.application.shared.external.survey.SourceTermsFacts.Term;
import vn.sunvibe.brokerage.core.domain.exception.AgreementContentException;
import vn.sunvibe.brokerage.core.domain.model.agreement.ChargeUnit;
import vn.sunvibe.brokerage.core.domain.model.agreement.DepositBasis;
import vn.sunvibe.brokerage.core.domain.model.agreement.FeeBasis;
import vn.sunvibe.brokerage.core.domain.model.agreement.MandatoryFee;
import vn.sunvibe.brokerage.core.domain.model.agreement.TermsDeposit;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionTerms;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Assembler - the survey proposal of a draft's terms (E05-S3 spec S3-D12): survey's flattened G8 terms of one space
 * mapped to the confirmed terms of brokerage-agreement BR-8. A part is taken only in the form the mapping knows and only
 * if the domain value accepts it, so each rule lives once (plan E05S3-P4); anything else is left out, never guessed,
 * rounded or adjusted. A pure function of its arguments.
 */
public final class TermsProposalAssembler {

    private static final String VND = "VND";
    private static final String MONTH = "MONTH";
    private static final String OTHER_FEES = "otherFees:";
    private static final String SPACE_FEES = "spaceFees:";
    /** survey writes a deposit in months as "N months" (IntakeTerms; pinned by SurveyIntakeSourcePostgresTest). */
    private static final Pattern MONTHS_TEXT = Pattern.compile("^(\\d{1,2}) months$");
    /** survey writes a count as the integer's text. */
    private static final Pattern INTEGER_TEXT = Pattern.compile("^\\d{1,9}$");
    /** The sources the contract lists; any other survey source reads null. */
    private static final Set<String> SOURCES = Set.of("OBSERVED_ON_SITE", "OWNER_STATED", "OWNER_FORM");
    /** survey's five named charges, in this order, with their fee names (English text, D-48). */
    private static final List<Map.Entry<String, String>> CHARGES = List.of(Map.entry("electricity", "Electricity"),
            Map.entry("water", "Water"), Map.entry("internet", "Internet"), Map.entry("parking", "Parking"),
            Map.entry("serviceFee", "Service fee"));

    private TermsProposalAssembler() {
    }

    /** The proposal of the space with this id; the empty proposal when the revision holds no such space. */
    public static SurveyTermsView proposal(SourceTermsFacts source, String spaceId) {
        Optional<String> spaceCode = source.spaces().stream().filter(space -> space.spaceId().equals(spaceId))
                .map(SourceTermsFacts.Space::code).filter(Objects::nonNull).findFirst();
        if (spaceCode.isEmpty()) {
            return SurveyTermsView.EMPTY;
        }
        return new Proposal(source, spaceCode.get()).build();
    }

    /** One assembly: the terms of the space and the sources of the parts taken. */
    private static final class Proposal {

        private final SourceTermsFacts source;
        private final String spaceCode;
        private final List<TermSource> sources = new ArrayList<>();

        private Proposal(SourceTermsFacts source, String spaceCode) {
            this.source = source;
            this.spaceCode = spaceCode;
        }

        private SurveyTermsView build() {
            Long rent = rent();
            TermsDeposit deposit = deposit();
            Integer minimum = count("minimumTermMonths", () -> minimumTerm(spaceTerm("minimumTermMonths")));
            Integer payment = count("paymentPeriodMonths", () -> paymentPeriod(spaceTerm("paymentPeriodMonths")));
            List<MandatoryFee> fees = fees();
            return new SurveyTermsView(new VersionTerms(rent, deposit, minimum, payment, fees), sources);
        }

        private Long rent() {
            Term term = spaceTerm("monthlyRent");
            if (term == null || term.amountOrNull() == null || !VND.equals(term.currencyOrNull())
                    || !MONTH.equals(term.unitOrNull())
                    || !accepted(() -> new VersionTerms(term.amountOrNull(), null, null, null, null))) {
                return null;
            }
            taken("monthlyRent", term);
            return term.amountOrNull();
        }

        private TermsDeposit deposit() {
            Term term = spaceTerm("deposit");
            if (term == null) {
                return null;
            }
            TermsDeposit deposit = null;
            if (term.amountOrNull() != null) {
                if (VND.equals(term.currencyOrNull())) {
                    deposit = valueOrNull(() -> new TermsDeposit(DepositBasis.AMOUNT, null, term.amountOrNull()));
                }
            } else if (term.textOrNull() != null) {
                Matcher months = MONTHS_TEXT.matcher(term.textOrNull());
                if (months.matches()) {
                    int count = Integer.parseInt(months.group(1));
                    deposit = valueOrNull(() -> new TermsDeposit(DepositBasis.MONTHS, count, null));
                }
            }
            if (deposit != null) {
                taken("deposit", term);
            }
            return deposit;
        }

        private Integer count(String field, Supplier<Integer> read) {
            Integer value = read.get();
            if (value != null) {
                taken(field, spaceTerm(field));
            }
            return value;
        }

        private static Integer minimumTerm(Term term) {
            Integer value = integer(term);
            return value != null && accepted(() -> new VersionTerms(null, null, value, null, null)) ? value : null;
        }

        private static Integer paymentPeriod(Term term) {
            Integer value = integer(term);
            return value != null && accepted(() -> new VersionTerms(null, null, null, value, null)) ? value : null;
        }

        /** The five charges (the space's override when listed, else the common term), the common fees, the space's. */
        private List<MandatoryFee> fees() {
            List<Candidate> candidates = new ArrayList<>();
            for (Map.Entry<String, String> charge : CHARGES) {
                Term term = Optional.ofNullable(spaceTerm(charge.getKey())).orElseGet(() -> commonTerm(charge.getKey()));
                if (term != null) {
                    candidates.add(new Candidate(term, () -> charge(charge.getValue(), term)));
                }
            }
            for (Term term : source.terms()) {
                if (term.spaceCodeOrNull() == null && term.name().startsWith(OTHER_FEES)) {
                    candidates.add(new Candidate(term, () -> namedFee(term.name().substring(OTHER_FEES.length()), term)));
                }
            }
            for (Term term : source.terms()) {
                if (spaceCode.equals(term.spaceCodeOrNull()) && term.name().startsWith(SPACE_FEES)) {
                    candidates.add(new Candidate(term, () -> namedFee(term.name().substring(SPACE_FEES.length()), term)));
                }
            }
            List<MandatoryFee> fees = new ArrayList<>();
            for (Candidate candidate : candidates) {
                if (fees.size() == VersionTerms.MAX_FEES) {
                    break;
                }
                MandatoryFee fee = valueOrNull(candidate.fee());
                if (fee != null) {
                    taken("mandatoryFees[" + fees.size() + "]", candidate.term());
                    fees.add(fee);
                }
            }
            return fees.isEmpty() ? null : fees;
        }

        /** A charge by survey's basis text: an amount with its unit, included, or the provider's rate; else nothing. */
        private static MandatoryFee charge(String name, Term term) {
            String basis = term.textOrNull();
            if (FeeBasis.AMOUNT.name().equals(basis)) {
                return namedFee(name, term);
            }
            if (FeeBasis.INCLUDED.name().equals(basis) || FeeBasis.PROVIDER_TARIFF.name().equals(basis)) {
                return new MandatoryFee(name, FeeBasis.valueOf(basis), null, null);
            }
            return null;
        }

        /** A fee charged as an amount in VND with a unit the confirmed terms know; else nothing. */
        private static MandatoryFee namedFee(String name, Term term) {
            Optional<ChargeUnit> unit = ChargeUnit.of(term.unitOrNull());
            if (term.amountOrNull() == null || !VND.equals(term.currencyOrNull()) || unit.isEmpty()) {
                return null;
            }
            return new MandatoryFee(name, FeeBasis.AMOUNT, term.amountOrNull(), unit.get());
        }

        private Term spaceTerm(String name) {
            return find(name, spaceCode);
        }

        private Term commonTerm(String name) {
            return find(name, null);
        }

        private Term find(String name, String codeOrNull) {
            return source.terms().stream()
                    .filter(term -> name.equals(term.name()) && Objects.equals(codeOrNull, term.spaceCodeOrNull()))
                    .findFirst().orElse(null);
        }

        private void taken(String field, Term term) {
            // Set.of(...).contains(null) throws, so a missing source is tested first
            String sourceOrNull = term.sourceOrNull() != null && SOURCES.contains(term.sourceOrNull())
                    ? term.sourceOrNull() : null;
            LocalDate date = term.dateOrNull() != null ? term.dateOrNull() : source.sourceDate();
            sources.add(new TermSource(field, sourceOrNull, date));
        }

        private static Integer integer(Term term) {
            if (term == null || term.textOrNull() == null || !INTEGER_TEXT.matcher(term.textOrNull()).matches()) {
                return null;
            }
            return Integer.parseInt(term.textOrNull());
        }

        private static boolean accepted(Supplier<?> value) {
            return valueOrNull(value) != null;
        }

        /** The value, or null when its domain rule refuses it (brokerage-agreement BR-8): the part is left out. */
        private static <T> T valueOrNull(Supplier<T> value) {
            try {
                return value.get();
            } catch (AgreementContentException refused) {
                return null;
            }
        }
    }

    /** One fee the survey may hold, with the term it would come from. */
    private record Candidate(Term term, Supplier<MandatoryFee> fee) {
    }
}
