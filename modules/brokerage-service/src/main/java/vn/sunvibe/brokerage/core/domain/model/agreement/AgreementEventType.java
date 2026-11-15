package vn.sunvibe.brokerage.core.domain.model.agreement;

/**
 * The events of an agreement's history (brokerage-agreement BR-17: the history holds the event, the actor, the time
 * and the version only). Later slices add their events.
 */
public enum AgreementEventType {
    CREATED
}
