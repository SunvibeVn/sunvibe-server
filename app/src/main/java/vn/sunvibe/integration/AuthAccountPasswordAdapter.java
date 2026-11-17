package vn.sunvibe.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.auth.core.application.usecase.user.PasswordReauthenticationUseCase;
import vn.sunvibe.brokerage.core.application.shared.external.account.AccountPasswordPort;
import vn.sunvibe.brokerage.core.application.shared.external.account.PasswordCheck;

/**
 * Cross-module composition (brokerage-agreement E05-S5 spec S5-D4, ADR-009): answers brokerage-service's password
 * question with auth-service's published re-authentication, which runs its own transaction and counts a wrong password
 * on the account's shared counter. It only maps the outcome by name.
 */
@Component
@RequiredArgsConstructor
public class AuthAccountPasswordAdapter implements AccountPasswordPort {

    private final PasswordReauthenticationUseCase reauthentication;

    @Override
    public PasswordCheck check(String userId, String password) {
        return new PasswordCheck(PasswordCheck.Outcome.valueOf(reauthentication.check(userId, password).outcome().name()));
    }
}
