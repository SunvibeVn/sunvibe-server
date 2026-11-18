package vn.sunvibe.brokerage.core.application.query;

import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementActorType;

import java.time.Instant;

/**
 * One event of an agreement's history (E05-S7 spec S7-D7, S7-D8; brokerage-agreement BR-17: the event, the actor, the
 * time and the version only; no reason and no user id).
 *
 * @param type            the event code, or OTHER for a stored code this version of the server does not know
 * @param versionNoOrNull the version the event is about
 * @param actorNameOrNull the staff member's full name; null for the owner, for Sunvibe's job and for an unknown account
 */
public record AgreementHistoryEntryView(String type, Integer versionNoOrNull, AgreementActorType actorType,
                                        String actorNameOrNull, Instant occurredAt) {
}
