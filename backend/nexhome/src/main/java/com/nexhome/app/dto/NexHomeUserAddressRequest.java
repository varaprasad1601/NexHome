package com.nexhome.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param activeFlag only {@code true} makes this the user's active address and deactivates the current one;
 *                   {@code false} or a missing flag saves it as inactive and leaves the current active address as is.
 */
public record NexHomeUserAddressRequest(
		@NotNull(message = "User is required")
		Long userId,

		@NotBlank(message = "Address line 1 is required")
		@Size(max = 255, message = "Address line 1 must be at most 255 characters")
		String address1,

		@NotBlank(message = "Address line 2 is required")
		@Size(max = 255, message = "Address line 2 must be at most 255 characters")
		String address2,

		@Size(max = 255, message = "Address line 3 must be at most 255 characters")
		String address3,

		@NotBlank(message = "City is required")
		@Size(max = 100, message = "City must be at most 100 characters")
		String city,

		@NotBlank(message = "District is required")
		@Size(max = 100, message = "District must be at most 100 characters")
		String district,

		@NotBlank(message = "State is required")
		@Size(max = 100, message = "State must be at most 100 characters")
		String state,

		@NotBlank(message = "PIN code is required")
		@Pattern(regexp = "^[0-9]{6}$", message = "PIN code must be exactly 6 digits")
		String pinCode,

		Boolean activeFlag) {
}
