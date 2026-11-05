package vn.sunvibe.survey.core.domain.policy.checklist;

/**
 * Whether the viewer can withdraw the lead's current PASSED now and, when so, the revision and the version the command
 * names (property-create requalification spec 7).
 */
public record RequalificationState(boolean allowed, String checklistIdOrNull, Long expectedVersionOrNull) {

    public static final RequalificationState NOT_ALLOWED = new RequalificationState(false, null, null);
}
