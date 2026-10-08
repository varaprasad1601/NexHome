package com.nexhome.app.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial update of a user's details, found by {@code userDetailsId}: a null (or missing) field keeps its current
 * value. An empty last name clears it; the first name cannot be blanked. The full name is recalculated.
 */
public record NexHomeUserDetailsUpdateRequest(
		@NotNull(message = "User details ID is required")
		Long userDetailsId,

		// The owning user cannot change; declared only so that sending it is rejected instead of silently ignored
		@Null(message = "User cannot be updated")
		Long userId,

		@Pattern(regexp = "(?s).*\\S.*", message = "First name cannot be blank")
		@Size(max = 100, message = "First name must be at most 100 characters")
		String firstName,

		@Size(max = 100, message = "Last name must be at most 100 characters")
		String lastName) {
}
