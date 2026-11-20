package vn.sunvibe.listing.core.domain.model.listing;

/**
 * The thirteen features of a revision (listing-publish BR-3; E06-S2 spec E06S2-D5): a yes-feature is true when chosen,
 * an option feature null when not chosen. Unpublished content (US-10 AC-4): its text names nothing.
 */
public record RevisionFeatures(Furnishing furnishingOrNull, boolean airConditioner, boolean waterHeater,
                               boolean fridge, ShareMode washingMachineOrNull, ShareMode kitchenOrNull,
                               boolean balcony, boolean elevator, boolean motorbikeParking, boolean carParking,
                               boolean internet, Pets petsOrNull, boolean noSharedLandlord) {

    /** No feature chosen: every yes-feature false, every option feature null. */
    public static final RevisionFeatures NONE_CHOSEN = new RevisionFeatures(null, false, false, false, null, null,
            false, false, false, false, false, null, false);

    @Override
    public String toString() {
        return "RevisionFeatures[hidden]";
    }
}
