package com.nexhome.app.dto;

import java.math.BigDecimal;

/**
 * Where a booking's payment stands: its method (ONLINE, or CASH once the partner collected cash instead), status
 * (PENDING, FAILED, PAID, ...), the amount, whether an online payment is still open at the gateway, and the latest
 * online attempt's outcome and message (null when none was checked).
 */
public record NexHomeBookingPaymentStatusResponse(
		Long bookingId,
		String bookingPublicId,
		String bookingStatus,
		String paymentMethod,
		String paymentStatus,
		BigDecimal totalAmount,
		String currency,
		boolean onlinePaymentOpen,
		String lastAttemptStatus,
		String lastAttemptMessage) {
}
