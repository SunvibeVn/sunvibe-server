package vn.sunvibe.property.core.application.shared.external.survey;

import java.util.Map;

/**
 * The source standing of a set of leads, owned by survey-service; the adapter lives in the app module (ADR-009). It joins
 * the caller's transaction and refuses to run without one.
 */
public interface SourceStandingPort {

    /** Every lead with its cycle answered, in chunks of 250 at a fixed number of statements each. */
    Map<String, SourceStandingFacts> standings(Map<String, Integer> intentCycleByLead);
}
