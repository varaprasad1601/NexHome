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
 * Creates a service in an active category. Lookup fields take value codes: pricing type FIXED, HOURLY (the base price
 * is per hour), PER_UNIT or INSPECTION; commission type PERCENTAGE (value at most 100) or FIXED. HOURLY needs the
 * minimum billable minutes and the billing increment. The slug defaults to the name in lower case with hyphens.
 */
public record NexHomeServiceCreateRequest(
		@NotNull(message = "Category is required")
		Long categoryId,

		@NotBlank(message = "Name is required")
		@Size(max = 150, message = "Name must be at most 150 characters")
		String name,

		@Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
				message = "Slug must be lower-case letters and digits separated by single hyphens")
		@Size(max = 180, message = "Slug must be at most 180 characters")
		String slug,

		@Size(max = 300, message = "Short description must be at most 300 characters")
		String shortDescription,

		String description,

		@NotBlank(message = "Pricing type is required")
		String pricingType,

		@NotNull(message = "Base price is required")
		@DecimalMin(value = "0", message = "Base price cannot be negative")
		@Digits(integer = 10, fraction = 2, message = "Base price must have at most 10 digits and 2 decimals")
		BigDecimal basePrice,

		@DecimalMin(value = "0", message = "Minimum charge cannot be negative")
		@Digits(integer = 10, fraction = 2, message = "Minimum charge must have at most 10 digits and 2 decimals")
		BigDecimal minimumCharge,

		@Positive(message = "Estimated duration must be more than 0 minutes")
		Integer estimatedDurationMinutes,

		@Positive(message = "Minimum billable time must be more than 0 minutes")
		Integer minimumBillableMinutes,

		@Positive(message = "Billing increment must be more than 0 minutes")
		Integer billingIncrementMinutes,

		@NotBlank(message = "Platform commission type is required")
		String platformCommissionType,

		@DecimalMin(value = "0", message = "Platform commission value cannot be negative")
		@Digits(integer = 6, fraction = 2,
				message = "Platform commission value must have at most 6 digits and 2 decimals")
		BigDecimal platformCommissionValue,

		Boolean isInstantBookingAllowed,

		Boolean isScheduledBookingAllowed,

		Boolean isActive) {
}
