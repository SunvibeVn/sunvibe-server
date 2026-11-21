package vn.sunvibe.listing.core.domain.model.listing;

/**
 * Why a revision is returned for changes (listing-publish BR-7; E06-S4 spec E06S4-D6), a closed list in this order. The
 * two photo reasons of BR-7 join it with the photo slices, which name the photos.
 */
public enum ChangeReason {
    /** "nội dung sai với căn" */
    CONTENT_MISMATCH,
    /** "văn bản vi phạm quy tắc", the fee names of the terms included */
    TEXT_RULE_VIOLATION,
    /** "thiếu thông tin" */
    MISSING_INFORMATION,
    /** "điều kiện thuê không khớp phiên bản thỏa thuận" */
    TERMS_MISMATCH,
    /** "khác (ghi rõ)": the note says what */
    OTHER
}
