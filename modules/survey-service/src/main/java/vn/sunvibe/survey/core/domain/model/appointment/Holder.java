package vn.sunvibe.survey.core.domain.model.appointment;

import java.time.Instant;

/**
 * Who held (was the surveyor of) an appointment from when to when (survey-schedule spec 4.1 "người phụ trách lịch
 * theo thời gian"); {@code to} is null for the current holder.
 */
public record Holder(String userId, Instant from, Instant to) {
}
