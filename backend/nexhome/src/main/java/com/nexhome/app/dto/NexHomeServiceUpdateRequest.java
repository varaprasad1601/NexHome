package com.nexhome.app.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Updates a service. A null (or missing) field keeps its current value; required fields cannot be blanked and an empty
 * short description or description clears it. Durations cannot be cleared once set. The discount price is always
 * replaced: leaving it out removes the discount. The category may change to another active category; the service
 * target and pricing type must stay a combination the target allows.
 * <p>
 * The lists replace what the service has when sent: {@code variants} updates the listed ones (by id), adds those
 * without an id and deletes the others; a requirement list keeps the chosen options of its side and deletes the other
 * options (free-text requirements stay). Null leaves them as they are.
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

		// Formatted HTML (cleaned by NexHomeRichText); the console limits its text to 4000 characters
		@Size(max = 20000, message = "Description must be at most 20000 characters with its formatting")
		String description,

		String serviceTarget,

		String pricingType,

		@DecimalMin(value = "0", message = "Base price cannot be negative")
		@Digits(integer = 10, fraction = 2, message = "Base price must have at most 10 digits and 2 decimals")
		BigDecimal basePrice,

		@DecimalMin(value = "0", message = "Discount price cannot be negative")
		@Digits(integer = 10, fraction = 2, message = "Discount price must have at most 10 digits and 2 decimals")
		BigDecimal discountPrice,

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

		Boolean isActive,

		@Size(max = 50, message = "A service can have at most 50 variants")
		List<@Valid @NotNull(message = "Variant is required") NexHomeServiceVariantInput> variants,

		@Size(max = 30, message = "Choose at most 30 customer requirements")
		List<@NotBlank(message = "Customer requirement cannot be blank") String> customerRequirements,

		@Size(max = 30, message = "Choose at most 30 partner requirements")
		List<@NotBlank(message = "Partner requirement cannot be blank") String> partnerRequirements) {
}
