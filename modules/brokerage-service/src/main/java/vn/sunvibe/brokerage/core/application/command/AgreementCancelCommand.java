package vn.sunvibe.brokerage.core.application.command;

/**
 * The basis lead's assigned Sale cancels an agreement that never took effect (brokerage-agreement BR-5, BR-16, US-1
 * AC-5; E05-S8 spec 3.4, S8-D7), with the data version they read (BR-5).
 */
public record AgreementCancelCommand(String agreementId, long expectedDataVersion) {
}
