package vn.sunvibe.auth.core.application.command.auth;

/**
 * The device a sign-in session was started or refreshed from. Stored with the
 * refresh token for auditing; never used to reject a request (mobile IPs change
 * constantly). Values are truncated to their column sizes.
 *
 * @param userAgent the {@code User-Agent} header, may be null
 * @param ipAddress the client address as seen by the server, may be null
 */
public record ClientInfo(String userAgent, String ipAddress) {

    private static final int MAX_USER_AGENT_LENGTH = 255;
    private static final int MAX_IP_ADDRESS_LENGTH = 45;

    public ClientInfo {
        userAgent = truncate(userAgent, MAX_USER_AGENT_LENGTH);
        ipAddress = truncate(ipAddress, MAX_IP_ADDRESS_LENGTH);
    }

    /** For sessions started without an HTTP request at hand. */
    public static ClientInfo unknown() {
        return new ClientInfo(null, null);
    }

    private static String truncate(String value, int maxLength) {
        return value != null && value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
