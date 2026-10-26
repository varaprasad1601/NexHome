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
 * Updates a service. A null (or missing) field keeps its current value; required fields cannot be blanked and an empty
 * short description or description clears it. Durations cannot be cleared once set. The category may change to another
 * active category.
 */
public record NexHomeServiceUpdateRequest(
		@NotNull(message = "Service ID is required")
		Long serviceId,

		@Null(message = "Public ID cannot be updated")
		String publicId,

		Long categoryId,

		@Pattern(regexp = "(?s).*\\S.*", message = "Name cannot be blank")
		@Size(max = 150, message = "Name must be at most 150 characters")
		String name,

		@Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
				message = "Slug must be lower-case letters and digits separated by single hyphens")
		@Size(max = 180, message = "Slug must be at most 180 characters")
		String slug,

		@Size(max = 300, message = "Short description must be at most 300 characters")
		String shortDescription,

		String description,

		String pricingType,

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

		String platformCommissionType,

		@DecimalMin(value = "0", message = "Platform commission value cannot be negative")
		@Digits(integer = 6, fraction = 2,
				message = "Platform commission value must have at most 6 digits and 2 decimals")
		BigDecimal platformCommissionValue,

		Boolean isInstantBookingAllowed,

		Boolean isScheduledBookingAllowed,

		Boolean isActive) {
}
