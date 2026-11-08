package vn.sunvibe.survey.core.domain.policy.checklist;

/** One warning of the preview with the ids it concerns (survey-checklist spec 4.7). */
public record PreviewWarning(WarningType type, String hazardId, String checklistId, Integer checklistNo,
                             String spaceId) {

    public static PreviewWarning openHazard(String hazardId) {
        return new PreviewWarning(WarningType.OPEN_IMPORTANT_HAZARD, hazardId, null, null, null);
    }

    public static PreviewWarning earlierHazard(String checklistId, int checklistNo, String hazardId) {
        return new PreviewWarning(WarningType.EARLIER_HAZARD_OPEN, hazardId, checklistId, checklistNo, null);
    }

    public static PreviewWarning partialAccess(String spaceId) {
        return new PreviewWarning(WarningType.PARTIAL_ACCESS, null, null, null, spaceId);
    }
}
