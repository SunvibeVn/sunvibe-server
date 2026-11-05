package vn.sunvibe.property.core.domain.model.intake;

import java.time.LocalDate;

/**
 * The owner's word on one space, on the day of the contact (property-create intake spec 4.9). The note is the Sale's own
 * text: never logged.
 *
 * @param expectedDateOrNull when the space is expected free, as the owner said
 * @param receivedByUserId   the caller who took the word
 */
public record AvailabilityItem(String spaceId, AvailabilityChoice state, LocalDate contactedOn, String note,
                               LocalDate expectedDateOrNull, String receivedByUserId) {

    @Override
    public String toString() {
        return "AvailabilityItem[" + spaceId + " " + state + "]";
    }
}
