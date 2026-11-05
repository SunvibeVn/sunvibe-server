package vn.sunvibe.auth.core.testsupport;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.sunvibe.shared.security.AuthenticatedUser;

import java.util.List;

/** Signs a request in the way the JWT filter does: the principal carries the user id, nothing else. */
public final class SignedIn {

    private SignedIn() {
    }

    public static void as(String userId) {
        AuthenticatedUser principal = () -> userId;
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
}
