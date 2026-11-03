package vn.sunvibe.survey.core.domain.policy.media;

import java.util.List;

/** Every allowed pair of a checklist's evidence coverage, asset first then space order (survey-media spec 4.6). */
public record EvidenceCoverage(List<CoveragePair> pairs) {

    public EvidenceCoverage {
        pairs = List.copyOf(pairs);
    }

    /** The required pairs not COVERED, in coverage order: the readiness items (EVIDENCE_MISSING, _PROCESSING). */
    public List<CoveragePair> missing() {
        return pairs.stream().filter(CoveragePair::missing).toList();
    }
}
