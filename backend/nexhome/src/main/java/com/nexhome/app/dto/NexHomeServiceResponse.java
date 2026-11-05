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
		Instant creationDate,

		/** The cover (first) image's public address; null without images. Filled in the service list only. */
		String coverImageUrl,

		/** The average service rating of the service's PUBLISHED reviews (1 decimal); null without any. List only. */
		BigDecimal averageRating,

		/** How many PUBLISHED reviews that average is of; null outside the list. */
		Long reviewCount) {
}
