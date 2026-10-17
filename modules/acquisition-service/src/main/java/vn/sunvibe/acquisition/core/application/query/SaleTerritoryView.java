package vn.sunvibe.acquisition.core.application.query;

import java.time.Instant;

/**
 * One ward of a province and the person who looks after it; {@code userId} and {@code assignedAt} are null when
 * nobody does. {@code wardType} is WARD, COMMUNE or SPECIAL_ZONE, as in the catalog.
 */
public record SaleTerritoryView(String wardCode, String wardName, String wardType, String userId,
                                Instant assignedAt) {
}
