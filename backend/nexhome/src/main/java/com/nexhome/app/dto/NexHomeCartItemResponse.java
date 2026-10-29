package com.nexhome.app.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

public record NexHomeCartItemResponse(
		Long cartItemId,
		Long serviceId,
		String serviceName,
		Long serviceVariantId,
		String variantName,
		BigDecimal estimatedUnitPrice,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant creationDate) {
}
