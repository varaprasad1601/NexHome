package com.nexhome.app.dto;

import java.math.BigDecimal;

/**
 * A partner's job figures for the profile's statistics.
 *
 * @param totalJobs bookings the partner was assigned to (and still holds)
 * @param assigned ASSIGNED bookings the partner has not accepted yet
 * @param accepted bookings the partner accepted (ASSIGNED with an ACCEPTED assignment) or is on the way to
 *        (PARTNER_EN_ROUTE)
 * @param inProgress bookings being worked on: IN_PROGRESS or PAUSED
 * @param completed bookings the partner holds that are COMPLETED
 * @param cancelled bookings the partner holds that are CANCELLED
 * @param rejected offers and assignments the partner declined (assignment REJECTED)
 * @param totalEarnings what the partner earned: the net of their payouts (not cancelled) plus the cash they collected;
 *        null for a caller who may not see payouts
 * @param averageRating null until reviews exist (Module 9)
 */
public record NexHomePartnerJobStatsResponse(
		long totalJobs,
		long assigned,
		long accepted,
		long inProgress,
		long completed,
		long cancelled,
		long rejected,
		BigDecimal totalEarnings,
		BigDecimal averageRating) {
}
