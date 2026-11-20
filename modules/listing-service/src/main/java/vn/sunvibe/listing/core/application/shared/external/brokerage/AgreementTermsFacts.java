package vn.sunvibe.listing.core.application.shared.external.brokerage;

import java.time.LocalDate;
import java.util.List;

/**
 * The rental terms in E05's answer for one pair, as listing's terms snapshot reads them (listing-publish E06-S2 spec
 * E06S2-D6, E06S2-D13): the terms of the version in effect, and of the activated version waiting for its start date
 * with that date, copied as E05 sends them, codes as strings; listing checks the bounds. {@code java.*} values only.
 * E05's terms are private (brokerage-agreement BR-17): no text here names a value.
 *
 * @param ended the agreement reached a final state or the end of its end date
 */
public record AgreementTermsFacts(boolean ended, Terms inEffectOrNull, LocalDate pendingStartDateOrNull,
                                  Terms pendingOrNull) {

    /** One version's terms: rent, deposit basis with months or amount, minimum term, payment period, fees in order. */
    public record Terms(Long monthlyRentOrNull, String depositBasisOrNull, Integer depositMonthsOrNull,
                        Long depositAmountOrNull, Integer minimumTermMonthsOrNull, Integer paymentPeriodMonthsOrNull,
                        List<Fee> fees) {

        public Terms {
            fees = List.copyOf(fees);
        }

        @Override
        public String toString() {
            return "Terms[hidden]";
        }
    }

    /** One mandatory fee: its name, basis code, amount and charge unit code. */
    public record Fee(String name, String basis, Long amountOrNull, String chargeUnitOrNull) {

        @Override
        public String toString() {
            return "Fee[hidden]";
        }
    }

    @Override
    public String toString() {
        return "AgreementTermsFacts[hidden]";
    }
}
