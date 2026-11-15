package vn.sunvibe.brokerage.core.domain.model.agreement;

/**
 * What a row of the agreement access log records (brokerage-agreement BR-17: opening the detail of an agreement is
 * logged with who, which agreement and when). Later slices add their own reads.
 */
public enum AgreementAccessType {
    DETAIL_READ
}
