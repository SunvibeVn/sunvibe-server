package vn.sunvibe.brokerage.core.domain.exception;

/**
 * The unit already has an unfinished agreement of the same transaction type (brokerage-agreement BR-1). It names no
 * lead and no agreement (brokerage-agreement BR-17).
 */
public class AgreementExistsException extends RuntimeException {

    public AgreementExistsException() {
        super("This unit already has an agreement in progress.");
    }
}
