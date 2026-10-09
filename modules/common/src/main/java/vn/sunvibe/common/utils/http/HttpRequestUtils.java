package vn.sunvibe.common.utils.http;

import jakarta.servlet.http.HttpServletRequest;

public class HttpRequestUtils {

    public static final String USER_AGENT = "User-Agent";

    public static String extractClientIP(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

}
