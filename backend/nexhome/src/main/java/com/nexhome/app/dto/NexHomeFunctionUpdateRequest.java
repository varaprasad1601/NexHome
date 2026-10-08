package com.nexhome.app.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Updates a function's name and active flag; a null (or missing) field keeps its current value and the name cannot be
 * blanked. The module and code identify the function and cannot be changed: they are only declared here so that a
 * request containing them is rejected with a clear message instead of being silently ignored.
 */
public record NexHomeFunctionUpdateRequest(
		@NotNull(message = "Function ID is required")
		Long functionId,

		@Null(message = "Function module cannot be updated")
		String functionModule,

		@Null(message = "Function code cannot be updated")
		String functionCode,

		@Pattern(regexp = "(?s).*\\S.*", message = "Function name cannot be blank")
		@Size(max = 100, message = "Function name must be at most 100 characters")
		String functionName,

		Boolean activeFlag) {
}
