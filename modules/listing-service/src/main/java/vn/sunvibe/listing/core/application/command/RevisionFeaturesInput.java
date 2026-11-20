package vn.sunvibe.listing.core.application.command;

/**
 * The thirteen features of a save as plain values (listing-publish BR-3; E06-S2 spec E06S2-D5, plan E06S2-P5): the
 * option features as codes the edge already checked, or null when not chosen; the yes-features as booleans.
 * Unpublished content (US-10 AC-4): its text names nothing.
 */
public record RevisionFeaturesInput(String furnishingOrNull, boolean airConditioner, boolean waterHeater,
                                    boolean fridge, String washingMachineOrNull, String kitchenOrNull, boolean balcony,
                                    boolean elevator, boolean motorbikeParking, boolean carParking, boolean internet,
                                    String petsOrNull, boolean noSharedLandlord) {

    @Override
    public String toString() {
        return "RevisionFeaturesInput[hidden]";
    }
}
