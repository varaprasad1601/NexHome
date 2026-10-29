package com.nexhome.app.dto;

import java.util.List;
import java.util.UUID;

/**
 * The bookings made by one checkout.
 */
public record NexHomeCheckoutResponse(
		UUID checkoutId,
		List<NexHomeBookingResponse> bookings) {
}
