package vn.sunvibe.survey.core.domain.model.checklist;

import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointmentId;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The facts of one submission (survey-checklist submission spec 4.3, BR-13, D3-1): who and when, the template, the lead
 * intent and cycle, the visit as it stood, the G1 scope, property type and spaces, the group versions, the media policy
 * and the hash of the canonical content and evidence. Insert-only, one per revision (I-2).
 */
public record ChecklistSubmission(SurveyChecklistId checklistId, String submittedByUserId, Instant submittedAt,
                                  String templateCode, int templateVersion, ChecklistKind kind, String leadIntent,
                                  int intentCycle, SurveyAppointmentId visitAppointmentId, String visitPerformedBy,
                                  Instant visitArrivedAt, String visitRecordedBy, Instant visitRecordedAt,
                                  RentalScope rentalScope, PropertyTypeCode propertyTypeOrNull,
                                  List<SubmittedSpace> spaces, Map<ChecklistGroup, Long> groupVersions,
                                  String mediaPolicy, String contentSha256) {

    /** survey-checklist D3-1, C-1: submitted while media is v1; photo permission was not recorded. */
    public static final String MEDIA_V1_NO_PERMISSION = "MEDIA_V1_NO_PERMISSION";

    public ChecklistSubmission {
        if (checklistId == null || submittedByUserId == null || submittedAt == null || templateCode == null
                || kind == null || leadIntent == null || visitAppointmentId == null || visitPerformedBy == null
                || visitArrivedAt == null || visitRecordedBy == null || visitRecordedAt == null
                || rentalScope == null || mediaPolicy == null || contentSha256 == null) {
            throw new IllegalArgumentException("A submission needs every fact of spec 4.3.");
        }
        spaces = spaces == null ? List.of() : List.copyOf(spaces);
        groupVersions = groupVersions == null ? Map.of() : Map.copyOf(groupVersions);
    }

    @Override
    public String toString() {
        return "ChecklistSubmission[" + checklistId + "]";
    }
}
