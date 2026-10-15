package vn.sunvibe.auth.core.application.query.role;

import java.util.List;

/** The roles of one account, sorted by name. */
public record UserRolesResult(String userId, List<String> roles) {
}
