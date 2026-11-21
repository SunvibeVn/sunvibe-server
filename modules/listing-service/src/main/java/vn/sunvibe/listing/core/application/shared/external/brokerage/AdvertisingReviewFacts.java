package vn.sunvibe.listing.core.application.shared.external.brokerage;

import java.time.LocalDate;
import java.util.List;

/**
 * E05's answer for one pair as the review reads it (listing-publish E06-S4 spec E06S4-D11, E06S4-D13): ended, allowed or
 * the reason groups in E05's order, the end date, the photo consents, and the terms of the version in effect and of the
 * version waiting to start with its date, copied as E05 sends them, codes as strings; listing maps the codes and checks
 * the bounds. {@code java.*} values only. E05's terms are private (brokerage-agreement BR-17): its text names the two ids
 * only.
 */
public record AdvertisingReviewFacts(String unitId, String agreementId, boolean ended, boolean allowed,
                                     List<String> reasonGroups, LocalDate endDateOrNull, boolean interiorPhotos,
                                     boolean exteriorPhotos, AgreementTermsFacts.Terms inEffectOrNull,
                                     LocalDate pendingStartDateOrNull, AgreementTermsFacts.Terms pendingOrNull) {

    public AdvertisingReviewFacts {
        reasonGroups = List.copyOf(reasonGroups);
    }

    @Override
    public String toString() {
        return "AdvertisingReviewFacts[" + unitId + ", " + agreementId + "]";
    }
}
