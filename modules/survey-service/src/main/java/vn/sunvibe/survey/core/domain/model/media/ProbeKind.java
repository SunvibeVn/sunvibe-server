package vn.sunvibe.survey.core.domain.model.media;

/** The three health probes (survey-media spec A-18). */
public enum ProbeKind {
    /** A create-only put, get and delete of a probe key in each bucket. */
    STORE(PauseReason.STORE),
    /** PING, signature age by VERSION, a clean sample and an EICAR sample. */
    SCANNER(PauseReason.SCANNER),
    /** A fixed tiny JPEG normalized within a time limit, its output passing the variant checks. */
    PROCESSOR(PauseReason.PROCESSOR);

    private final PauseReason pauseReason;

    ProbeKind(PauseReason pauseReason) {
        this.pauseReason = pauseReason;
    }

    public PauseReason pauseReason() {
        return pauseReason;
    }
}
