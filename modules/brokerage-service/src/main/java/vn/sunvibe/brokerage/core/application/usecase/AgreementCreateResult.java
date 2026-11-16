package vn.sunvibe.brokerage.core.application.usecase;

/**
 * The draft agreement just created; only its id, so that only an opening of the detail shows the agreement and is logged
 * (brokerage-agreement BR-17: every opening of an agreement's detail is logged).
 */
public record AgreementCreateResult(String agreementId) {
}
