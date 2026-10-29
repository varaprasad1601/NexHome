package com.nexhome.app.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

public record NexHomeServiceVariantResponse(
		Long serviceVariantId,

		String publicId,

		Long serviceId,

		String variantCode,

		String name,

		String description,

		BigDecimal priceOverride,

		BigDecimal discountPrice,

		Integer durationMinutes,

		int displayOrder,

		boolean isActive,

		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant creationDate) {
}
