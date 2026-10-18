package vn.sunvibe.auth.core.application.usecase.staff;

import java.util.Set;

/**
 * An account that may be given owner leads, as published to other modules. {@code roles} holds the names of the
 * assignee roles the account has: a subset of {@code SALE} and {@code SALE_LEADER}, never other roles, ordered
 * {@code SALE} then {@code SALE_LEADER}.
 * No phone, e-mail or username.
 */
public record LeadAssigneeAccount(String userId, String fullName, Set<String> roles) {
}
