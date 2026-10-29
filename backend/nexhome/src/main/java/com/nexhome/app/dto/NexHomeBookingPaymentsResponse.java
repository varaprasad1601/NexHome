package com.nexhome.app.dto;

import java.util.List;

/**
 * Everything about paying for a booking: its payment orders (of its checkout), payments, refunds, cash collection
 * and the partner's payout.
 */
public record NexHomeBookingPaymentsResponse(
		List<NexHomePaymentOrderResponse> orders,
		List<NexHomePaymentResponse> payments,
		List<NexHomeRefundResponse> refunds,
		NexHomeCashCollectionResponse cashCollection,
		NexHomePayoutResponse payout) {
}
