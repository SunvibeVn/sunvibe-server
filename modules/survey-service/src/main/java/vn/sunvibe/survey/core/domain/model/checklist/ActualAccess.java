package vn.sunvibe.survey.core.domain.model.checklist;

/** The access actually had to a space, with a note required for ENTERED_PART (spec 4.6). Note: never printed. */
public record ActualAccess(AccessExtent extent, String note) {

    @Override
    public String toString() {
        return "ActualAccess[" + extent + "]";
    }
}
