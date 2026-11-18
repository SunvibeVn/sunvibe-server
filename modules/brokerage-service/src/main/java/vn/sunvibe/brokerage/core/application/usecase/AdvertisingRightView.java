package vn.sunvibe.brokerage.core.application.usecase;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The advertising answer of one pair as brokerage-service publishes it (brokerage-agreement BR-12, BR-13, US-4 AC-1,
 * AC-2; E05-S6 spec S6-D7, S6-D9): plain values and codes only, nothing else of the agreement. Private terms
 * (brokerage-agreement BR-17): its text names the pair, the flags and the groups only.
 *
 * @param ended                  the agreement reached a final state or the end of its end date (group (ii))
 * @param allowed                exactly when {@code reasonGroups} is empty
 * @param reasonGroups           the codes of the reason groups, in their order: NO_AGREEMENT, NOT_IN_EFFECT, NOT_STARTED,
 *                               SUSPENDED, ENDED, BASIS, PROPERTY, UNIT, OWNER
 * @param endDateOrNull          the end date of the version that counts
 * @param endsAtOrNull           the start of the day after that end date in Vietnam
 * @param interiorPhotos         interior and common-area photos may be public now
 * @param exteriorPhotos         exterior photos may be public now
 * @param termsInEffectOrNull    the rental terms of the version in effect
 * @param pendingStartDateOrNull the start date of an activated version not in effect yet
 * @param pendingStartsAtOrNull  the start of that date in Vietnam
 * @param pendingTermsOrNull     that version's rental terms
 */
public record AdvertisingRightView(String unitId, String agreementId, boolean ended, boolean allowed,
                                   List<String> reasonGroups, LocalDate endDateOrNull, Instant endsAtOrNull,
                                   boolean interiorPhotos, boolean exteriorPhotos, Terms termsInEffectOrNull,
                                   LocalDate pendingStartDateOrNull, Instant pendingStartsAtOrNull,
                                   Terms pendingTermsOrNull) {

    public AdvertisingRightView {
        reasonGroups = List.copyOf(reasonGroups);
    }

    /**
     * Rental terms of a version (brokerage-agreement BR-8), as codes and whole VND.
     *
     * @param mandatoryFees the fees besides the rent; empty when none is stated
     */
    public record Terms(Long monthlyRentOrNull, Deposit depositOrNull, Integer minimumTermMonthsOrNull,
                        Integer paymentPeriodMonthsOrNull, List<MandatoryFee> mandatoryFees) {

        public Terms {
            mandatoryFees = List.copyOf(mandatoryFees);
        }

        /** brokerage-agreement BR-17: the terms are private, so no amount and no fee name. */
        @Override
        public String toString() {
            return "Terms[hidden]";
        }
    }

    /** The deposit: its basis code with a number of months or an amount. */
    public record Deposit(String basis, Integer monthsOrNull, Long amountOrNull) {

        @Override
        public String toString() {
            return "Deposit[hidden]";
        }
    }

    /** One mandatory fee besides the rent: its name, basis code, amount and charge unit code. */
    public record MandatoryFee(String name, String basis, Long amountOrNull, String chargeUnitOrNull) {

        @Override
        public String toString() {
            return "MandatoryFee[hidden]";
        }
    }

    /** brokerage-agreement BR-17: the pair, the flags and the groups; never the terms or the dates. */
    @Override
    public String toString() {
        return "AdvertisingRightView[" + unitId + ", " + agreementId + ", allowed=" + allowed + ", ended=" + ended
                + ", groups=" + reasonGroups + "]";
    }
}
