package vn.sunvibe.survey.core.domain.repository;

import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupState;

import java.util.Collection;
import java.util.Map;

/**
 * Group rows of checklists (survey-checklist spec 5.2, 5.5): all seven inserted at start; each save updates its row
 * with a version check. A read loads all rows of a checklist in one statement, so a GET never shows half of a
 * cascade. The locking reads return the state their own select read.
 */
public interface SurveyChecklistGroupDomainRepository {

    /** Insert the seven group rows of a new checklist at version 0 and flush. */
    void insertAll(SurveyChecklistId checklistId, Map<ChecklistGroup, GroupState> groups);

    /** The seven groups of the checklist, in one statement. */
    Map<ChecklistGroup, GroupState> findAll(SurveyChecklistId checklistId);

    /** The groups of each given checklist, in one statement. */
    Map<SurveyChecklistId, Map<ChecklistGroup, GroupState>> findAllOf(Collection<SurveyChecklistId> checklistIds);

    /** The group row read with {@code FOR SHARE} (the G1 row of a G2..G8 save, spec 5.5). */
    GroupState lockForShare(SurveyChecklistId checklistId, ChecklistGroup group);

    /** The group row read with {@code FOR UPDATE} (the saved group, the G1 row of a G1 save, cascaded groups). */
    GroupState lockForUpdate(SurveyChecklistId checklistId, ChecklistGroup group);

    /** The other groups after the G1 lock of a G1 save (spec 5.5), in one statement. */
    Map<ChecklistGroup, GroupState> findOthers(SurveyChecklistId checklistId, ChecklistGroup except);

    /**
     * Write the group's next state; {@code expectedVersion} is the version the save read and the row must still have.
     * The row's version goes up by one; the returned state carries it.
     */
    GroupState update(SurveyChecklistId checklistId, ChecklistGroup group, long expectedVersion, GroupState next);
}
