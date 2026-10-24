package vn.sunvibe.survey.core.domain.policy.checklist;

/** Whether the viewer may edit the draft now, and why not (survey-checklist spec 4.4). */
public record Editability(boolean editable, ReadOnlyReason reason) {

    public static final Editability EDITABLE = new Editability(true, null);

    public static Editability readOnly(ReadOnlyReason reason) {
        return new Editability(false, reason);
    }
}
