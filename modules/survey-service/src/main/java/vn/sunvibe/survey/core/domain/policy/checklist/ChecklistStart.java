package vn.sunvibe.survey.core.domain.policy.checklist;

/**
 * Whether the viewer can start a checklist on the current visit (survey-checklist spec 7.2 {@code checklistStart}):
 * allowed with the visit and whether an intake declaration is needed, or the first block that applies.
 */
public record ChecklistStart(boolean allowed, String visitAppointmentIdOrNull, boolean intakeDeclarationRequired,
                             StartBlock blockedByOrNull) {

    public static ChecklistStart blocked(StartBlock block) {
        return new ChecklistStart(false, null, false, block);
    }
}
