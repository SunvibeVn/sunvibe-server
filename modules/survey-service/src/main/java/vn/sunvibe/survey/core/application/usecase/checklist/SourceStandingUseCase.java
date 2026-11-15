package vn.sunvibe.survey.core.application.usecase.checklist;

import java.util.Map;

/**
 * Use Case - SourceStandingUseCase
 * <p>
 * The source standing of a set of leads (property-create intake spec 5.1), each answered as
 * {@link SurveyIntakeSourceUseCase#currentSource} and {@link SurveyIntakeSourceUseCase#markedRevisions} answer it alone,
 * without the source content. It writes nothing.
 * <p>
 * Viewer-less. Called only by an operation that has already checked that its caller may see what it asks about, or by an
 * anonymous read that shows none of these values, through an adapter in {@code vn.sunvibe.integration} that never
 * exposes it over HTTP (ADR-009; ADR-012 decision 2; ViewerlessReadArchTest). Read-only; no lock; no value of it reaches
 * a public answer except as ADR-012 decision 1 allows.
 */
public interface SourceStandingUseCase {

    /**
     * Read 2 (ADR-012 decision 2, row 2). Only inside the caller's transaction (MANDATORY), so one read sees one state of
     * the records; any number of leads, read in chunks of 250 at a fixed number of statements each, in the map's order.
     * Every key is answered: a lead with no current PASSED in its cycle has no revision, and an unknown lead no marks.
     *
     * @param intentCycleByLead each lead with the intent cycle whose current source is asked
     * @throws NullPointerException for a null map, key or cycle
     */
    Map<String, SourceStandingView> standings(Map<String, Integer> intentCycleByLead);
}
