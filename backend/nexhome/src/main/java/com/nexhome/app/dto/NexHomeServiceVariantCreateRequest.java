package com.nexhome.app.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Adds a package to a service, e.g. "2 BHK". A null price or duration means the service's applies. The code is unique
 * within the service and never changes.
 */
public record NexHomeServiceVariantCreateRequest(
		@NotNull(message = "Service is required")
		Long serviceId,

		@NotBlank(message = "Variant code is required")
		@Pattern(regexp = "^[A-Z][A-Z0-9_]*$",
				message = "Variant code must contain only capital letters, digits and underscores, starting with a letter")
		@Size(max = 80, message = "Variant code must be at most 80 characters")
		String variantCode,

		@NotBlank(message = "Name is required")
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
