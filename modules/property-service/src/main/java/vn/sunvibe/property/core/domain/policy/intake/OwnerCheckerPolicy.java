package vn.sunvibe.property.core.domain.policy.intake;

import java.util.Collection;

/** Who may run the owner check (property-create intake spec 4.3, D5-7): nobody who has worked the lead. */
public final class OwnerCheckerPolicy {

    private OwnerCheckerPolicy() {
    }

    /**
     * What the lead's work leaves behind. A manager who only reviewed the survey or suspended the lead is none of these.
     *
     * @param earlierAssignees everyone who held the lead before
     * @param intakeActors     who ran an assignee command on any intake of the lead
     */
    public record Work(String assigneeOrNull, Collection<String> earlierAssignees, String surveyorOrNull,
                       String recorderOrNull, Collection<String> intakeActors) {
    }

    public static boolean hasWorked(String userId, Work work) {
        return userId.equals(work.assigneeOrNull()) || work.earlierAssignees().contains(userId)
                || userId.equals(work.surveyorOrNull()) || userId.equals(work.recorderOrNull())
                || work.intakeActors().contains(userId);
    }
}
