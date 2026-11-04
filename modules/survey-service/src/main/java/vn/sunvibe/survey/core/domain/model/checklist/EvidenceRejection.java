package vn.sunvibe.survey.core.domain.model.checklist;

/**
 * A frozen photo the reviewer did not accept, with why (survey-checklist submission spec A-5, media BR-21 b). The
 * reason is private: never in History, logs or list views (survey BR-4, BR-14).
 */
public record EvidenceRejection(String mediaId, String reason) {

    public EvidenceRejection {
        if (mediaId == null || reason == null) {
            throw new IllegalArgumentException("A rejection names its photo and its reason.");
        }
    }

    @Override
    public String toString() {
        return "EvidenceRejection[" + mediaId + "]";
    }
}
