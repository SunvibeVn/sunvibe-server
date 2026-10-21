package vn.sunvibe.acquisition.core.domain.model.consent;

import vn.sunvibe.acquisition.core.domain.exception.InvalidIntakeValueException;

import java.time.Instant;

/**
 * Proof that an owner accepted the privacy notice when sending the intake form (acquisition-owner-intake BR-9): which
 * notice version, for what purpose, through which channel and when. The IP address and user agent are kept only as
 * keyed hashes. Each lead has its own record.
 */
public final class ConsentRecord {

    /** The only notice version a new consent can refer to. */
    public static final String CURRENT_NOTICE_VERSION = "owner-intake-2026-10-21";

    private final ConsentRecordId id;
    private final String noticeVersion;
    private final ConsentPurpose purpose;
    private final ConsentChannel channel;
    private final String ipHash;
    private final String userAgentHash;
    private final Instant acceptedAt;

    private ConsentRecord(ConsentRecordId id, String noticeVersion, ConsentPurpose purpose, ConsentChannel channel,
                          String ipHash, String userAgentHash, Instant acceptedAt) {
        this.id = required(id, "id");
        this.noticeVersion = required(noticeVersion, "noticeVersion");
        this.purpose = required(purpose, "purpose");
        this.channel = required(channel, "channel");
        this.ipHash = ipHash;
        this.userAgentHash = userAgentHash;
        this.acceptedAt = required(acceptedAt, "acceptedAt");
    }

    /**
     * A new consent. Refused unless it refers to the current notice version.
     */
    public static ConsentRecord create(String noticeVersion, ConsentPurpose purpose, ConsentChannel channel,
                                       String ipHash, String userAgentHash, Instant acceptedAt) {
        if (!CURRENT_NOTICE_VERSION.equals(noticeVersion)) {
            throw new InvalidIntakeValueException("consent.noticeVersion",
                    "Consent must refer to the current privacy notice version.");
        }
        return new ConsentRecord(ConsentRecordId.generate(), noticeVersion, purpose, channel, ipHash, userAgentHash,
                acceptedAt);
    }

    /**
     * Load from database (reconstruct). An older notice version is allowed here.
     */
    public static ConsentRecord load(ConsentRecordId id, String noticeVersion, ConsentPurpose purpose,
                                     ConsentChannel channel, String ipHash, String userAgentHash,
                                     Instant acceptedAt) {
        return new ConsentRecord(id, noticeVersion, purpose, channel, ipHash, userAgentHash, acceptedAt);
    }

    // ==================== Getters ====================

    public ConsentRecordId getId() {
        return id;
    }

    public String getNoticeVersion() {
        return noticeVersion;
    }

    public ConsentPurpose getPurpose() {
        return purpose;
    }

    public ConsentChannel getChannel() {
        return channel;
    }

    public String getIpHash() {
        return ipHash;
    }

    public String getUserAgentHash() {
        return userAgentHash;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    private static <T> T required(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required.");
        }
        return value;
    }
}
