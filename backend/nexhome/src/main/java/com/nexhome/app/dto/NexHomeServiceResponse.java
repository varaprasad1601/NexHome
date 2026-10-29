package com.nexhome.app.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * A service. The commission fields are only filled in for callers with CATALOG_MANAGE read; everyone else gets null.
 */
public record NexHomeServiceResponse(
		Long serviceId,

		String publicId,

		Long categoryId,

		String categoryName,

		String name,

		String slug,

		String shortDescription,

		String description,

		String serviceTarget,

		String pricingType,

		BigDecimal basePrice,

		BigDecimal discountPrice,

		BigDecimal minimumCharge,

		Integer estimatedDurationMinutes,

		Integer minimumBillableMinutes,

		Integer billingIncrementMinutes,

		String platformCommissionType,

		BigDecimal platformCommissionValue,

		boolean isInstantBookingAllowed,

		boolean isScheduledBookingAllowed,

		boolean isActive,

		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant creationDate) {
}
