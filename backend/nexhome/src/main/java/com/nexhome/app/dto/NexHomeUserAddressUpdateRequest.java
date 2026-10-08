package com.nexhome.app.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial update of an address. The required fields (address1, address2, city, district, state, pinCode) keep
 * their current value when null or missing, and cannot be blanked. The optional address3 is always replaced:
 * a null, missing or empty value clears it.
 *
 * @param activeFlag {@code true} makes this the user's active address (the current one is deactivated),
 *                   {@code false} deactivates it, null leaves it unchanged
 */
public record NexHomeUserAddressUpdateRequest(
		@NotNull(message = "User address ID is required")
		Long userAddressId,

		// The owning user cannot change; declared only so that sending it is rejected instead of silently ignored
		@Null(message = "User cannot be updated")
		Long userId,

		@Pattern(regexp = "(?s).*\\S.*", message = "Address line 1 cannot be blank")
		@Size(max = 255, message = "Address line 1 must be at most 255 characters")
		String address1,

		@Pattern(regexp = "(?s).*\\S.*", message = "Address line 2 cannot be blank")
		@Size(max = 255, message = "Address line 2 must be at most 255 characters")
		String address2,

		@Size(max = 255, message = "Address line 3 must be at most 255 characters")
		String address3,

		@Pattern(regexp = "(?s).*\\S.*", message = "City cannot be blank")
		@Size(max = 100, message = "City must be at most 100 characters")
		String city,

		@Pattern(regexp = "(?s).*\\S.*", message = "District cannot be blank")
		@Size(max = 100, message = "District must be at most 100 characters")
		String district,

		@Pattern(regexp = "(?s).*\\S.*", message = "State cannot be blank")
		@Size(max = 100, message = "State must be at most 100 characters")
		String state,

		@Pattern(regexp = "^[0-9]{6}$", message = "PIN code must be exactly 6 digits")
		String pinCode,

		Boolean activeFlag) {
}
