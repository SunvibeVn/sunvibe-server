package vn.sunvibe.auth.core.application.usecase.external.token;

import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;

/**
 * Signs the short-lived access tokens (JWT) clients send with every request.
 */
public interface AccessTokenIssuerPort {

    /**
     * Issues an access token for the account as it is now.
     *
     * @throws org.springframework.security.authentication.AccountStatusException
     *         if the account is locked, terminated or deleted
     */
    String issue(AppUser user);

}
