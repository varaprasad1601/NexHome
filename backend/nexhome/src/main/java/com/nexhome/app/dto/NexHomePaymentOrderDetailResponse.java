package com.nexhome.app.dto;

import java.util.List;

/**
 * One payment order with everything behind it: the order (with its bookings), every attempt at the gateway
 * (newest first) and the payments captured from it.
 */
public record NexHomePaymentOrderDetailResponse(
		NexHomePaymentOrderResponse order,
		List<NexHomePaymentAttemptResponse> attempts,
		List<NexHomePaymentResponse> payments) {
}
