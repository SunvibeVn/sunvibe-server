package vn.sunvibe.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.brokerage.core.application.shared.external.survey.SourceTermsFacts;
import vn.sunvibe.brokerage.core.application.shared.external.survey.SourceTermsPort;
import vn.sunvibe.survey.core.application.usecase.checklist.IntakeSourceView;
import vn.sunvibe.survey.core.application.usecase.checklist.SurveyIntakeSourceUseCase;

import java.util.Optional;

/**
 * Cross-module composition (brokerage-agreement E05-S3 spec S3-D11, ADR-009): answers brokerage-service's question for
 * the G8 terms of a lead cycle's current survey source with survey-service's published {@link SurveyIntakeSourceUseCase}.
 * It only copies the revision, its visit date, the spaces and the terms, and joins the caller's transaction; brokerage
 * calls it only after its own visibility check. {@link SurveyIntakeSourceAdapter} serves property-service apart.
 */
@Component
@RequiredArgsConstructor
public class SurveySourceTermsAdapter implements SourceTermsPort {

    private final SurveyIntakeSourceUseCase sources;

    @Override
    public Optional<SourceTermsFacts> currentTerms(String leadId, int intentCycle) {
        return sources.currentSource(leadId, intentCycle).map(SurveySourceTermsAdapter::facts);
    }

    private static SourceTermsFacts facts(IntakeSourceView view) {
        return new SourceTermsFacts(view.revisionId(), view.sourceDate(),
                view.spaces().stream().map(space -> new SourceTermsFacts.Space(space.spaceId(), space.code())).toList(),
                view.terms().stream().map(term -> new SourceTermsFacts.Term(term.name(), term.spaceCodeOrNull(),
                        term.amount(), term.currency(), term.unit(), term.text(), term.source(), term.date())).toList());
    }
}
