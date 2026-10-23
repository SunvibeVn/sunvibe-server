package vn.sunvibe.acquisition.core.application.usecase.survey;

/** The staff member a survey command or read runs for, with the lead access the lead queue gives them. */
public record SurveyLeadViewer(String userId, SurveyLeadAccess access) {

    public SurveyLeadViewer {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required.");
        }
        if (access == null) {
            throw new IllegalArgumentException("access is required.");
        }
    }
}
