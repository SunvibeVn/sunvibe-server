package vn.sunvibe.brokerage.core.presentation.resolver;

import org.springframework.stereotype.Component;
import vn.sunvibe.shared.security.CurrentPrincipal;

/**
 * The signed-in account of an owner endpoint (E05-S5 spec S5-D15): any account, staff included, which the use case then
 * answers as one with nothing (brokerage-agreement US-2 AC-1). The account is the principal, never a body field.
 */
@Component
public class OwnerActorResolver {

    public String userId() {
        // Unreachable after the JWT filter and the path rule when it fails
        return CurrentPrincipal.requireUserId();
    }
}
