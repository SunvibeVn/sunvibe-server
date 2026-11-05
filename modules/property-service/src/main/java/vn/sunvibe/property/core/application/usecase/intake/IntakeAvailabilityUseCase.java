package vn.sunvibe.property.core.application.usecase.intake;

import java.time.LocalDate;
import java.util.List;

/** Use Case - what the owner said about each selected space, on the day of the contact (property-create intake spec 4.9). */
public interface IntakeAvailabilityUseCase {

    IntakeBlockView availability(String leadId, IntakeActor actor, List<Item> items, Long expectedVersion);

    /** The note is the Sale's own text: never logged. */
    record Item(String spaceId, String state, LocalDate contactedOn, String note, LocalDate expectedDateOrNull) {

        @Override
        public String toString() {
            return "Item[" + spaceId + " " + state + "]";
        }
    }
}
