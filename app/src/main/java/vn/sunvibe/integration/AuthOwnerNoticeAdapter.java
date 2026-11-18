package vn.sunvibe.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.auth.core.application.usecase.role.OwnerAccountUseCase;
import vn.sunvibe.auth.core.application.usecase.role.OwnerNoticeKind;
import vn.sunvibe.brokerage.core.application.shared.external.account.OwnerNoticePort;

/**
 * Cross-module composition (E05-S4 spec S4-D7, E05-S6 spec S6-D13, ADR-009): brokerage-service's owner notices as
 * auth-service's published owner notices of the kinds AGREEMENT_PENDING and AGREEMENT_ACTIVATED. It only translates, and
 * joins the caller's transaction; auth chooses the address and the text and delivers the mail.
 */
@Component
@RequiredArgsConstructor
public class AuthOwnerNoticeAdapter implements OwnerNoticePort {

    private final OwnerAccountUseCase owners;

    @Override
    public void agreementPending(String ownerUserId) {
        owners.notifyOwner(ownerUserId, OwnerNoticeKind.AGREEMENT_PENDING);
    }

    /** E05-S6 spec S6-D13: the notice of the activation, of the kind AGREEMENT_ACTIVATED. */
    @Override
    public void agreementActivated(String ownerUserId) {
        owners.notifyOwner(ownerUserId, OwnerNoticeKind.AGREEMENT_ACTIVATED);
    }
}
