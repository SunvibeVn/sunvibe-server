package vn.sunvibe.brokerage.core.application.usecase;

/**
 * One (unit, agreement) pair a consumer asks the advertising answer for (E05-S6 spec S6-D9; ADR-012 condition (b): the
 * listing's own agreement on its own unit).
 */
public record AdvertisingPair(String unitId, String agreementId) {
}
