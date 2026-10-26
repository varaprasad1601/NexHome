package com.nexhome.app.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Updates a customer address. A null (or missing) field keeps its current value; required fields cannot be blanked,
 * and an empty label, address line 2 or landmark clears it. Coordinates are replaced as a pair.
 * {@code isDefault: true} makes this the default address (replacing the current one); {@code false} leaves the
 * customer without a default. The owner and public id are fixed: they are only declared here so that a request
 * containing them is rejected with a clear message.
 */
public record NexHomeAddressUpdateRequest(
		@NotNull(message = "Address ID is required")
		Long addressId,

		@Null(message = "User cannot be updated")
		Long userId,

		@Null(message = "Public ID cannot be updated")
		String publicId,

		@Pattern(regexp = "(?s).*\\S.*", message = "Contact name cannot be blank")
		@Size(max = 150, message = "Contact name must be at most 150 characters")
		String contactName,

		@Pattern(regexp = "^[0-9]{6,15}$", message = "Mobile number must be 6 to 15 digits")
		String mobileNumber,

		String addressType,

		@Size(max = 50, message = "Label must be at most 50 characters")
		String label,

		@Pattern(regexp = "(?s).*\\S.*", message = "Address line 1 cannot be blank")
		@Size(max = 255, message = "Address line 1 must be at most 255 characters")
		String addressLine1,

		@Size(max = 255, message = "Address line 2 must be at most 255 characters")
		String addressLine2,

		@Size(max = 255, message = "Landmark must be at most 255 characters")
		String landmark,

		@Pattern(regexp = "(?s).*\\S.*", message = "City cannot be blank")
		@Size(max = 100, message = "City must be at most 100 characters")
		String city,

		@Pattern(regexp = "(?s).*\\S.*", message = "State cannot be blank")
		@Size(max = 100, message = "State must be at most 100 characters")
		String state,

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
