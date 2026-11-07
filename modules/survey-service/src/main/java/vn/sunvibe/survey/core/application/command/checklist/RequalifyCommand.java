package vn.sunvibe.survey.core.application.command.checklist;

import vn.sunvibe.survey.core.domain.model.checklist.RequalificationKind;

import java.time.LocalDate;

/**
 * A manager's request to withdraw a PASSED revision from use, as sent (property-create requalification spec 5.3, 7, A-6).
 * The texts are private: never printed.
 */
public record RequalifyCommand(String checklistId, Long expectedVersion, RequalificationKind reasonKind, String reason,
                               String eventSource, String scope, LocalDate basisDate) {

    @Override
    public String toString() {
        return "RequalifyCommand[" + checklistId + " " + reasonKind + "]";
    }
}
