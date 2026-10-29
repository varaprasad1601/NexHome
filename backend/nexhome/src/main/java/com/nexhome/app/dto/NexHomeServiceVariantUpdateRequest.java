package com.nexhome.app.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Updates a variant. A null (or missing) field keeps its current value, except the price override, discount price and
 * duration, which are always replaced: leaving them out makes the service's price and duration apply again. The name cannot be blanked
 * and an empty description clears it. The service and code are fixed: they are only declared here so that a request
 * containing them is rejected with a clear message.
 */
public record NexHomeServiceVariantUpdateRequest(
		@NotNull(message = "Service variant ID is required")
		Long serviceVariantId,

		@Null(message = "Service cannot be updated")
		Long serviceId,

		@Null(message = "Variant code cannot be updated")
		String variantCode,

		@Null(message = "Public ID cannot be updated")
		String publicId,

		@Pattern(regexp = "(?s).*\\S.*", message = "Name cannot be blank")
		@Size(max = 150, message = "Name must be at most 150 characters")
		String name,

		String description,

		@DecimalMin(value = "0", message = "Price override cannot be negative")
		@Digits(integer = 10, fraction = 2, message = "Price override must have at most 10 digits and 2 decimals")
		BigDecimal priceOverride,

		@DecimalMin(value = "0", message = "Discount price cannot be negative")
		@Digits(integer = 10, fraction = 2, message = "Discount price must have at most 10 digits and 2 decimals")
		BigDecimal discountPrice,

		@Positive(message = "Duration must be more than 0 minutes")
		Integer durationMinutes,

		Integer displayOrder,

		Boolean isActive) {
}
