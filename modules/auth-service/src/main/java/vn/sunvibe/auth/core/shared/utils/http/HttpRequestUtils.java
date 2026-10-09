package vn.sunvibe.auth.core.shared.utils.http;

import jakarta.servlet.http.HttpServletRequest;
import vn.sunvibe.auth.core.shared.constant.CommonConst;

public class HttpRequestUtils {

    public static final String USER_AGENT = "User-Agent";

    public static String extractClientIP(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    public static String getRawFingerprint(HttpServletRequest request) {
        return request.getHeader(USER_AGENT) + CommonConst.HYPHEN + request.getRemoteAddr();
    }
}
