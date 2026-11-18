package vn.sunvibe.brokerage.core.application.command;

/**
 * The assigned Sale opens the next draft version after a rejection or a lapse (brokerage-agreement US-2 AC-3, E-6;
 * E05-S5 spec S5-D10), against the data version they read (BR-5).
 */
public record AgreementOpenDraftCommand(String agreementId, Long expectedDataVersion) {
}
