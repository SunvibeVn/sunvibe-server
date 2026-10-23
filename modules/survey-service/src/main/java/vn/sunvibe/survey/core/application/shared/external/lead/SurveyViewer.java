package vn.sunvibe.survey.core.application.shared.external.lead;

/** The staff member a survey command or read runs for, with the lead access the lead queue gives them. */
public record SurveyViewer(String userId, ViewerAccess access) {

    public SurveyViewer {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required.");
        }
        if (access == null) {
            throw new IllegalArgumentException("access is required.");
        }
    }

    /** True for a SALE: the viewer sees only the leads assigned to them. */
    public boolean seesOwnLeadsOnly() {
        return access == ViewerAccess.OWN;
    }
}
