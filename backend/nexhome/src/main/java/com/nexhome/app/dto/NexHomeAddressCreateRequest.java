package com.nexhome.app.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Adds a service address for a customer (an account holding the CUSTOMER role). The first address becomes the
 * default; {@code isDefault: true} makes a later one the default instead. Coordinates are optional but come as a pair
 * and are rounded to 7 decimals. The country defaults to IN, where the postal code must be a 6-digit PIN code.
 */
public record NexHomeAddressCreateRequest(
		@NotNull(message = "User ID is required")
		Long userId,

		@NotBlank(message = "Contact name is required")
		@Size(max = 150, message = "Contact name must be at most 150 characters")
		String contactName,

		@NotBlank(message = "Mobile number is required")
		@Pattern(regexp = "^[0-9]{6,15}$", message = "Mobile number must be 6 to 15 digits")
		String mobileNumber,

		// ADDRESS_TYPE code: HOME, WORK, REGISTERED, OTHER
		@NotBlank(message = "Address type is required")
		String addressType,

		@Size(max = 50, message = "Label must be at most 50 characters")
		String label,

		@NotBlank(message = "Address line 1 is required")
		@Size(max = 255, message = "Address line 1 must be at most 255 characters")
		String addressLine1,

		@Size(max = 255, message = "Address line 2 must be at most 255 characters")
		String addressLine2,

		@Size(max = 255, message = "Landmark must be at most 255 characters")
		String landmark,

		@NotBlank(message = "City is required")
		@Size(max = 100, message = "City must be at most 100 characters")
		String city,

		@NotBlank(message = "State is required")
		@Size(max = 100, message = "State must be at most 100 characters")
		String state,

		@NotBlank(message = "Postal code is required")
		@Pattern(regexp = "^\\s*[A-Za-z0-9][A-Za-z0-9 -]{1,10}[A-Za-z0-9]\\s*$",
				message = "Postal code must be 3 to 12 letters, digits, spaces or hyphens")
		String postalCode,

		@Pattern(regexp = "^[A-Za-z]{2}$", message = "Country code must be a 2-letter ISO country code, e.g. IN")
		String countryCode,

		@DecimalMin(value = "-90", message = "Latitude must be between -90 and 90")
		@DecimalMax(value = "90", message = "Latitude must be between -90 and 90")
		BigDecimal latitude,

		@DecimalMin(value = "-180", message = "Longitude must be between -180 and 180")
		@DecimalMax(value = "180", message = "Longitude must be between -180 and 180")
		BigDecimal longitude,

		Boolean isDefault) {
}
