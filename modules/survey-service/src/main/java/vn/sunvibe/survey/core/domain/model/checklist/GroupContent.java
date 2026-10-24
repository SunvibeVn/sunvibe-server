package vn.sunvibe.survey.core.domain.model.checklist;

/** The content of one stored checklist group of template v1 (survey-checklist spec 4.6). */
public sealed interface GroupContent permits IdentificationContent, PartsContent, DimensionsContent, ConditionContent,
        SafetyContent, AmenitiesContent, TermsContent {

    ChecklistGroup group();
}
