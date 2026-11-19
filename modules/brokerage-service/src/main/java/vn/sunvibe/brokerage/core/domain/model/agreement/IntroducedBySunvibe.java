package vn.sunvibe.brokerage.core.domain.model.agreement;

/**
 * Whether Sunvibe introduced the tenant of a unit reported let (brokerage-agreement BR-14 "có, không, hoặc chưa rõ";
 * E05-S8 spec S8-D5). Stored and exposed.
 */
public enum IntroducedBySunvibe {
    YES,
    NO,
    UNKNOWN
}
