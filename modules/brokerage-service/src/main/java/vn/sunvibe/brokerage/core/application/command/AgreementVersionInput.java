package vn.sunvibe.brokerage.core.application.command;

/** One version the owner confirms: its agreement and its number (E05-S5 spec S5-D5). */
public record AgreementVersionInput(String agreementId, int versionNo) {
}
