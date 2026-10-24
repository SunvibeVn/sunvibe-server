package vn.sunvibe.survey.core.domain.model.checklist;

/** G2 values of one space: floor, private areas, actual access (survey-checklist spec 4.6). */
public record PartsSpace(String spaceId, Field<String> floor, Field<String> privateAreas,
                         Field<ActualAccess> actualAccess) {

    @Override
    public String toString() {
        return "PartsSpace[" + spaceId + "]";
    }
}
