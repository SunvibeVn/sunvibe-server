package vn.sunvibe.property.core.domain.model.intake;

/** One selected space of a revision (property-create intake spec 4.5). Code and location come from the survey: never logged. */
public record SelectedSpace(String spaceId, String code, String location) {

    @Override
    public String toString() {
        return "SelectedSpace[" + spaceId + "]";
    }
}
