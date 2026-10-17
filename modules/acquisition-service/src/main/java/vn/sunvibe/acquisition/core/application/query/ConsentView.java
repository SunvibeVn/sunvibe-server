package vn.sunvibe.acquisition.core.application.query;

import java.time.Instant;

/**
 * The consent of a lead as staff see it: which privacy notice the owner accepted and when. The IP and user agent
 * hashes stay out.
 */
public record ConsentView(String noticeVersion, Instant acceptedAt) {
}
