package vn.sunvibe.listing.core.application.shared.external.brokerage;

import java.util.List;

/**
 * E05's advertising answer for one (unit, agreement) pair (brokerage-agreement BR-12, BR-13; listing-publish E06-S1 spec
 * E06S1-D9): plain values only. The services turn the codes into listing's domain values.
 *
 * @param ended        the agreement reached a final state or the end of its end date
 * @param allowed      advertising is allowed now (E05: exactly when the groups are empty)
 * @param reasonGroups the codes of the groups that stop advertising, in E05's order, as E05 sends them
 */
public record AdvertisingAnswerFacts(String unitId, String agreementId, boolean ended, boolean allowed,
                                     List<String> reasonGroups) {

    public AdvertisingAnswerFacts {
        reasonGroups = List.copyOf(reasonGroups);
    }
}
