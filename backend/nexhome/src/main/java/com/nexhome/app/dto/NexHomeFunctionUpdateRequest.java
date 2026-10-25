package com.nexhome.app.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Updates a function. A null (or missing) name, type or active flag keeps its current value and they cannot be
 * blanked; the description is always replaced, so leaving it out clears it. The module and code identify the function
 * and cannot be changed: they are only declared here so that a request containing them is rejected with a clear
 * message instead of being silently ignored.
 */
public record NexHomeFunctionUpdateRequest(
		@NotNull(message = "Function ID is required")
		Long functionId,

		@Null(message = "Function module cannot be updated")
		String functionModule,

		@Null(message = "Function code cannot be updated")
		String functionCode,

		@Pattern(regexp = "(?s).*\\S.*", message = "Function name cannot be blank")
		@Size(max = 150, message = "Function name must be at most 150 characters")
		String functionName,

		@Pattern(regexp = "(?s).*\\S.*", message = "Function type cannot be blank")
		String functionType,

		String description,

		Boolean activeFlag) {
}
