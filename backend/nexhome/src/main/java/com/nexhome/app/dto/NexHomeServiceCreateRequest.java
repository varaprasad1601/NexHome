package com.nexhome.app.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Creates a service in an active category, with its variants and requirements in the same transaction. Lookup fields
 * take value codes: service target APPLIANCE or PROPERTY (SERVICE_TARGET), which decides the pricing types allowed
 * (from the lookup's metadata: APPLIANCE FIXED or HOURLY, PROPERTY also AREA); pricing type FIXED, HOURLY (the base
 * price is per hour) or AREA (by home size); commission type PERCENTAGE (value at most 100) or FIXED. HOURLY needs the
 * minimum billable minutes and the billing increment. The discount price is at most the base price. The slug defaults
 * to the name in lower case with hyphens. Requirements are CUSTOMER_REQUIREMENT / PARTNER_REQUIREMENT value codes.
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

		// Formatted HTML (cleaned by NexHomeRichText); the console limits its text to 4000 characters
		@Size(max = 20000, message = "Description must be at most 20000 characters with its formatting")
		String description,

		@NotBlank(message = "Service for is required")
		String serviceTarget,

		@NotBlank(message = "Pricing type is required")
		String pricingType,

		@NotNull(message = "Base price is required")
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

		@NotBlank(message = "Platform commission type is required")
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
