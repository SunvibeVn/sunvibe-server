package vn.sunvibe.brokerage.core.domain.model.agreement;

/**
 * Who acted on a row of an agreement's history, as the history answers it (E05-S7 spec S7-D7; brokerage-agreement
 * BR-17: the history says who acted, never more of the owner). An owner account holds no staff role
 * (brokerage-agreement BR-4), so no staff member is the agreement's owner.
 */
public enum AgreementActorType {
    /** A staff member: any actor who is not the agreement's owner. */
    STAFF,
    /** The agreement's owner. */
    OWNER,
    /** No actor: a transition Sunvibe's jobs stored when it fell due (a lapse, a start). */
    SYSTEM;

    /** The type of a row's actor: none is SYSTEM, the agreement's owner is OWNER, anyone else STAFF. */
    public static AgreementActorType of(String actorUserIdOrNull, String ownerUserId) {
        if (actorUserIdOrNull == null) {
            return SYSTEM;
        }
        return actorUserIdOrNull.equals(ownerUserId) ? OWNER : STAFF;
    }
}
