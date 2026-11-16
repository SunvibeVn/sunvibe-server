package vn.sunvibe.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.auth.core.application.usecase.role.OwnerAccountUseCase;
import vn.sunvibe.auth.core.application.usecase.role.OwnerNoticeKind;
import vn.sunvibe.brokerage.core.application.shared.external.account.OwnerNoticePort;

/**
 * Cross-module composition (E05-S4 spec S4-D7, ADR-009): brokerage-service's owner notice as auth-service's published
 * owner notice of the kind AGREEMENT_PENDING. It only translates, and joins the caller's transaction; auth chooses the
 * address and the text and delivers the mail.
 */
@Component
@RequiredArgsConstructor
public class AuthOwnerNoticeAdapter implements OwnerNoticePort {

    private final OwnerAccountUseCase owners;

    @Override
    public void agreementPending(String ownerUserId) {
        owners.notifyOwner(ownerUserId, OwnerNoticeKind.AGREEMENT_PENDING);
    }
}
