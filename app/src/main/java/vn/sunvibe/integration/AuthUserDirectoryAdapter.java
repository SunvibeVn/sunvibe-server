package vn.sunvibe.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.auth.core.application.usecase.role.OwnerAccountContext;
import vn.sunvibe.auth.core.application.usecase.role.OwnerAccountUseCase;
import vn.sunvibe.auth.core.application.usecase.role.OwnerLookup;
import vn.sunvibe.auth.core.application.usecase.role.OwnerNoticeKind;
import vn.sunvibe.auth.core.application.usecase.role.OwnerStanding;
import vn.sunvibe.auth.core.application.usecase.user.PasswordReauthenticationUseCase;
import vn.sunvibe.auth.core.application.usecase.user.ReauthenticationResult;
import vn.sunvibe.property.core.application.shared.external.user.OwnerAccountFacts;
import vn.sunvibe.property.core.application.shared.external.user.OwnerNotice;
import vn.sunvibe.property.core.application.shared.external.user.OwnerRoute;
import vn.sunvibe.property.core.application.shared.external.user.OwnerStandingFacts;
import vn.sunvibe.property.core.application.shared.external.user.PasswordCheck;
import vn.sunvibe.property.core.application.shared.external.user.UserDirectoryPort;

/**
 * Cross-module composition: answers property-service's owner questions with auth-service's published
 * {@link OwnerAccountUseCase}, so neither module depends on the other. Lives in app, which depends on every module.
 */
@Component
@RequiredArgsConstructor
public class AuthUserDirectoryAdapter implements UserDirectoryPort {

    private final OwnerAccountUseCase ownerAccountUseCase;
    private final PasswordReauthenticationUseCase passwordReauthentication;

    @Override
    public boolean isEligibleOwner(String userId) {
        return ownerAccountUseCase.isEligibleOwner(userId);
    }

    @Override
    public boolean ensureOwnerRole(String ownerUserId, String actorUserId) {
        return ownerAccountUseCase.ensureOwnerRole(ownerUserId, actorUserId);
    }

    @Override
    public OwnerRoute lookupConfirmable(String email) {
        OwnerLookup lookup = ownerAccountUseCase.lookupConfirmable(email);
        return new OwnerRoute(OwnerRoute.Outcome.valueOf(lookup.outcome().name()), lookup.userIdOrNull());
    }

    @Override
    public OwnerAccountFacts accountContext(String userId) {
        OwnerAccountContext context = ownerAccountUseCase.accountContext(userId);
        return new OwnerAccountFacts(context.addressMasked(), context.ageDays());
    }

    @Override
    public OwnerStandingFacts ownerStanding(String userId) {
        OwnerStanding standing = ownerAccountUseCase.ownerStanding(userId);
        return new OwnerStandingFacts(standing.eligible(), standing.signInLosses());
    }

    @Override
    public void notifyOwner(String userId, OwnerNotice notice) {
        ownerAccountUseCase.notifyOwner(userId, OwnerNoticeKind.valueOf(notice.name()));
    }

    @Override
    public PasswordCheck checkPassword(String userId, String password) {
        ReauthenticationResult result = passwordReauthentication.check(userId, password);
        return new PasswordCheck(PasswordCheck.Outcome.valueOf(result.outcome().name()), result.lockedFor());
    }
}
