package vn.sunvibe.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.property.core.application.shared.external.survey.IntakeSource;
import vn.sunvibe.property.core.application.shared.external.survey.SourceStandingFacts;
import vn.sunvibe.property.core.application.shared.external.survey.SourceStandingPort;
import vn.sunvibe.survey.core.application.usecase.checklist.SourceStandingUseCase;
import vn.sunvibe.survey.core.application.usecase.checklist.SourceStandingView;

import java.util.HashMap;
import java.util.Map;

/**
 * Cross-module composition (ADR-009; ADR-012 decision 2, read 2): answers property-service's source standing of a set of
 * leads with survey-service's published {@link SourceStandingUseCase}. It only translates records, and joins the caller's
 * transaction.
 */
@Component
@RequiredArgsConstructor
public class SurveySourceStandingAdapter implements SourceStandingPort {

    private final SourceStandingUseCase standings;

    @Override
    public Map<String, SourceStandingFacts> standings(Map<String, Integer> intentCycleByLead) {
        Map<String, SourceStandingFacts> facts = new HashMap<>();
        standings.standings(intentCycleByLead).forEach((leadId, view) -> facts.put(leadId, facts(view)));
        return Map.copyOf(facts);
    }

    private static SourceStandingFacts facts(SourceStandingView view) {
        return new SourceStandingFacts(view.revisionIdOrNull(), view.usable(), view.reasonOrNull(),
                view.markedRevisions().stream()
                        .map(mark -> new IntakeSource.MarkedRevision(mark.revisionId(), mark.kind(), mark.markedAt()))
                        .toList());
    }
}
