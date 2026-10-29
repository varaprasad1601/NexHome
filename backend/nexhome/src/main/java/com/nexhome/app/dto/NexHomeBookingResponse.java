package com.nexhome.app.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * A booking with its customer and partner's names and the address and price it was booked with.
 */
public record NexHomeBookingResponse(
		Long bookingId,
		String publicId,
		UUID checkoutId,
		Long customerProfileId,
		String customerPublicId,
		String customerName,
		Long customerUserId,
		Long serviceId,
		String serviceName,
		Long serviceVariantId,
		Long partnerProfileId,
		String partnerPublicId,
		String partnerName,
		Long addressId,
		String bookingType,
		String bookingStatus,
		String paymentStatus,
		String paymentMethod,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant scheduledStartAt,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant scheduledEndAt,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant actualStartAt,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant actualEndAt,
		Map<String, Object> address,
		Map<String, Object> pricing,
		BigDecimal subtotalAmount,
		BigDecimal discountAmount,
		BigDecimal taxAmount,
		BigDecimal transactionFeeAmount,
		BigDecimal totalAmount,
		String currency,
		String cancellationReason,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant creationDate) {
}
