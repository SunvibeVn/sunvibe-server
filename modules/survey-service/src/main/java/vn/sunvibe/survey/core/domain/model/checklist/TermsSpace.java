package vn.sunvibe.survey.core.domain.model.checklist;

import java.util.List;

/**
 * G8 values of one space (survey-checklist spec 4.6): rent, deposit, payment period, minimum term and, in PARTS scope
 * only, the charge overrides and the space's own fees (null otherwise, spec PD-4).
 */
public record TermsSpace(String spaceId, SourcedField<RentMoney> monthlyRent, SourcedField<Deposit> deposit,
                         SourcedField<Integer> paymentPeriodMonths, SourcedField<Integer> minimumTermMonths,
                         ChargeOverrides chargeOverrides, SourcedField<List<Fee>> spaceFees) {

    @Override
    public String toString() {
        return "TermsSpace[" + spaceId + "]";
    }
}
