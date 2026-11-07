package vn.sunvibe.survey.core.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistSwitches;

/** The checklist switch as configured (survey-checklist submission spec A-3); off unless the owner opens review. */
@Component
public class RuntimeChecklistSwitches implements ChecklistSwitches {

    private final boolean submissionEnabled;

    public RuntimeChecklistSwitches(@Value("${app.survey.checklist.submission-enabled:false}") boolean submissionEnabled) {
        this.submissionEnabled = submissionEnabled;
    }

    @Override
    public boolean submissionEnabled() {
        return submissionEnabled;
    }
}
