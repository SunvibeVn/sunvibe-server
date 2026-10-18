package vn.sunvibe.acquisition.core.application.query;

import java.time.Instant;

/**
 * One ward of a province and the person who looks after it; {@code userId} and {@code assignedAt} are null when
 * nobody does; {@code userName} is the person's full name, null when nobody does or the id has no user row
 * (acquisition-owner-lead-queue-ui C-18). {@code wardType} is WARD, COMMUNE or SPECIAL_ZONE, as in the catalog.
 */
public record SaleTerritoryView(String wardCode, String wardName, String wardType, String userId,
                                String userName, Instant assignedAt) {
}
