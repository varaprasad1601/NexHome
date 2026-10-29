package com.nexhome.app.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * One variant in a service's create or update request, e.g. "2 Hours" of an hourly service or "2 BHK" of an
 * area-based one. Without {@code serviceVariantId} it is added (its code made from the name); with it, that variant of
 * the service is updated. In the list's order.
 *
 * @param durationMinutes HOURLY: the hours the price covers, in minutes
 * @param discountPrice what customers pay instead of the price; at most the price
 */
public record NexHomeServiceVariantInput(
		Long serviceVariantId,

		@NotBlank(message = "Variant name is required")
		@Size(max = 150, message = "Variant name must be at most 150 characters")
		String name,

		@Size(max = 500, message = "Variant description must be at most 500 characters")
		String description,

		@NotNull(message = "Variant price is required")
		@DecimalMin(value = "0", message = "Variant price cannot be negative")
		@Digits(integer = 10, fraction = 2, message = "Variant price must have at most 10 digits and 2 decimals")
		BigDecimal price,

		@DecimalMin(value = "0", message = "Variant discount price cannot be negative")
		@Digits(integer = 10, fraction = 2,
				message = "Variant discount price must have at most 10 digits and 2 decimals")
		BigDecimal discountPrice,

		@Positive(message = "Variant duration must be more than 0 minutes")
		Integer durationMinutes,

		Boolean isActive) {
}
