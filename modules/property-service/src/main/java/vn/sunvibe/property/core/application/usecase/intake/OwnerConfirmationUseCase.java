package vn.sunvibe.property.core.application.usecase.intake;

import java.util.List;

/**
 * Use Case - the owner's side of a confirmation (property-create intake spec 4.6, 7.3). Every verb takes the signed-in account:
 * a request bound to another one answers 404 before any counter moves.
 */
public interface OwnerConfirmationUseCase {

    List<OwnerConfirmationItem> list(String userId);

    OwnerConfirmationDetails details(String userId, String requestId, String code);

    void confirm(String userId, String requestId, String code, String password, String statementVersion);

    void decline(String userId, String requestId, String code);

    void withdraw(String userId, String requestId, String password);

    /** The phrase the manager read out; only this account can enter it (property-create intake spec 4.6 step 6). */
    void enterPhrase(String userId, String requestId, String phrase);
}
