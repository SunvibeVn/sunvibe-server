package vn.sunvibe.brokerage.core.domain.model.agreement;

/**
 * The events of an agreement's history (brokerage-agreement BR-17: the history holds the event, the actor, the time
 * and the version only). Later slices add their events.
 */
public enum AgreementEventType {
    CREATED,
    /**
     * One save of a draft's content (E05-S3 spec S3-D10): one row per save with the version, the actor and the time. The
     * authors of a version are its creator and every actor of these rows.
     */
    DRAFT_EDITED
}
