package vn.sunvibe.survey.core.domain.policy.checklist;

/** One warning of the preview with the ids it concerns (survey-checklist spec 4.7). */
public record ChecklistWarning(WarningType type, String hazardId, String checklistId, Integer checklistNo,
                               String spaceId) {

    public static ChecklistWarning openHazard(String hazardId) {
        return new ChecklistWarning(WarningType.OPEN_IMPORTANT_HAZARD, hazardId, null, null, null);
    }

    public static ChecklistWarning earlierHazard(String checklistId, int checklistNo, String hazardId) {
        return new ChecklistWarning(WarningType.EARLIER_HAZARD_OPEN, hazardId, checklistId, checklistNo, null);
    }

    public static ChecklistWarning partialAccess(String spaceId) {
        return new ChecklistWarning(WarningType.PARTIAL_ACCESS, null, null, null, spaceId);
    }
}
