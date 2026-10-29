package com.nexhome.app.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * A booking line; {@code variant} is the option as booked (serviceVariantId, publicId, variantCode, name, price,
 * discountPrice, durationMinutes), null without one.
 */
public record NexHomeBookingItemResponse(
		Long bookingItemId,
		Long serviceId,
		String itemName,
		Map<String, Object> variant,
		BigDecimal unitPrice,
		BigDecimal lineTotal) {
}
