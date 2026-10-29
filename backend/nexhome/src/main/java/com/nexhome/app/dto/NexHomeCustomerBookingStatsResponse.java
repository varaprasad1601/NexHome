package com.nexhome.app.dto;

import java.math.BigDecimal;

/**
 * A customer's figures for the console's customer profile. The booking stages are counted as the partner's are
 * ({@link NexHomePartnerJobStatsResponse}).
 *
 * @param totalBookings every booking of the customer, whatever its status
 * @param pending PENDING bookings (every new booking, until staff or its payment confirm it)
 * @param confirmed CONFIRMED bookings (waiting for a partner)
 * @param partnerAssigned ASSIGNED bookings the partner has not accepted yet
 * @param accepted bookings the partner accepted (ASSIGNED with an ACCEPTED assignment) or is on the way to
 *        (PARTNER_EN_ROUTE)
 * @param inProgress bookings being worked on: IN_PROGRESS or PAUSED
 * @param completed COMPLETED bookings
 * @param cancelled CANCELLED bookings
 * @param partnerRejected offers and assignments of the customer's bookings that partners declined (REJECTED)
 * @param totalSpend what the customer paid: online payments captured plus cash collected, less refunds that succeeded
 * @param averageRating the average of the customer's reviews; null until reviews exist
 */
public record NexHomeCustomerBookingStatsResponse(
		long totalBookings,
		long pending,
		long confirmed,
		long partnerAssigned,
		long accepted,
		long inProgress,
		long completed,
		long cancelled,
		long partnerRejected,
		BigDecimal totalSpend,
		BigDecimal averageRating) {
}
