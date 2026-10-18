package vn.sunvibe.acquisition.core.application.shared.external.staff;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A staff account an owner lead may be given to, as the assignee port reports it: id, full name and the assignee
 * roles it holds ({@code SALE} and/or {@code SALE_LEADER}, in a stable order). No phone, e-mail or username.
 */
public record EligibleAssignee(String userId, String fullName, Set<String> roles) {

    public EligibleAssignee {
        roles = roles == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(roles));
    }
}
