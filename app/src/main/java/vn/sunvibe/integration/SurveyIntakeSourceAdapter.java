package vn.sunvibe.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.property.core.application.shared.external.survey.IntakeSource;
import vn.sunvibe.property.core.application.shared.external.survey.IntakeSourcePort;
import vn.sunvibe.survey.core.application.usecase.checklist.IntakeSourceView;
import vn.sunvibe.survey.core.application.usecase.checklist.SurveyIntakeSourceUseCase;

import java.util.List;
import java.util.Optional;

/**
 * Cross-module composition (property-create intake spec 5.1, ADR-009): answers property-service's source questions with
 * survey-service's published {@link SurveyIntakeSourceUseCase}. It only translates records, and joins the caller's
 * transaction.
 */
@Component
@RequiredArgsConstructor
public class SurveyIntakeSourceAdapter implements IntakeSourcePort {

    private final SurveyIntakeSourceUseCase sources;

    @Override
    public Optional<IntakeSource> currentSource(String leadId, int intentCycle) {
        return sources.currentSource(leadId, intentCycle).map(SurveyIntakeSourceAdapter::source);
    }

    @Override
    public List<IntakeSource.MarkedRevision> markedRevisions(String leadId) {
        return marks(sources.markedRevisions(leadId));
    }

    private static IntakeSource source(IntakeSourceView view) {
        return new IntakeSource(view.revisionId(), view.decidedAt(), view.performedBy(), view.recordedBy(),
                view.contentSha256(), view.rentalScope(), view.assetTypeOrNull(), view.addressText(),
                view.provinceCode(), view.provinceName(), view.wardCode(), view.wardName(), view.latitude(),
                view.longitude(), view.sourceDate(), view.spaces().stream().map(SurveyIntakeSourceAdapter::space).toList(),
                view.terms().stream().map(SurveyIntakeSourceAdapter::term).toList(), view.usable(), view.reasonOrNull(),
                marks(view.markedRevisions()));
    }

    private static IntakeSource.Space space(IntakeSourceView.Space space) {
        return new IntakeSource.Space(space.spaceId(), space.code(), space.location(), space.accessLimits(),
                space.sourceDate(), space.areaSqm(), space.bedrooms(), space.privateBathrooms(), space.condition(),
                space.amenities(), space.sources().stream().map(field ->
                        new IntakeSource.FieldSource(field.field(), field.source(), field.date())).toList());
    }

    private static IntakeSource.Term term(IntakeSourceView.Term term) {
        return new IntakeSource.Term(term.name(), term.spaceCodeOrNull(), term.amount(), term.currency(), term.unit(),
                term.text(), term.source(), term.date());
    }

    private static List<IntakeSource.MarkedRevision> marks(List<IntakeSourceView.MarkedRevision> marks) {
        return marks.stream().map(mark -> new IntakeSource.MarkedRevision(mark.revisionId(), mark.kind(), mark.markedAt()))
                .toList();
    }
}
