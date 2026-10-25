package com.nexhome.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param functionType a FUNCTION_TYPE lookup code: MODULE, SCREEN, FEATURE, API or ACTION
 * @param activeFlag defaults to true when missing
 */
public record NexHomeFunctionCreateRequest(
		@NotBlank(message = "Function module is required")
		@Pattern(regexp = "^\\s*[A-Z][A-Z0-9_]*\\s*$", message
				= "Function module must contain only capital letters, digits and underscores, starting with a letter")
		@Size(max = 50, message = "Function module must be at most 50 characters")
		String functionModule,

		@NotBlank(message = "Function code is required")
		@Pattern(regexp = "^\\s*[A-Z][A-Z0-9_]*\\s*$", message
				= "Function code must contain only capital letters, digits and underscores, starting with a letter")
		@Size(max = 100, message = "Function code must be at most 100 characters")
		String functionCode,

		@NotBlank(message = "Function name is required")
		@Size(max = 150, message = "Function name must be at most 150 characters")
		String functionName,

		@NotBlank(message = "Function type is required")
		String functionType,

		String description,

		Boolean activeFlag) {
}
